#!/usr/bin/env python3
"""City offline pack for IbizaDash modules (Fase D).

Downloads Esri Dark Gray tiles for a bounded box around a city and writes a
ZIP with `{z}/{x}/{y}.png`, ready to be published as a release asset and
installed from IbizaCompanion -> Ajustes -> Modulos.

Usage:
    python scripts/build_city_pack.py --plan
    python scripts/build_city_pack.py --name tiles-morelia --lat 19.7060 --lon -101.1950 --km 20 --zooms 13-16
"""
import argparse
import hashlib
import json
import math
import os
import sys
import threading
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor

import requests
from requests.adapters import HTTPAdapter

BASE = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile"
UA = {"User-Agent": "IbizaDash-offline-pack/1.0 (personal use; attribution shown in app)"}
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "dist", "city-pack")

THREADS = 20
RETRIES = 4
TIMEOUT = 25


def lon_to_x(lon, z):
    return int((lon + 180.0) / 360.0 * (1 << z))


def lat_to_y(lat, z):
    r = math.radians(lat)
    return int((1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0 * (1 << z))


def km_to_deg(km, lat):
    dlat = km / 111.32
    dlon = km / (111.32 * math.cos(math.radians(lat)))
    return dlat, dlon


def ranges(lat, lon, km, z):
    dlat, dlon = km_to_deg(km, lat)
    x0, x1 = lon_to_x(lon - dlon, z), lon_to_x(lon + dlon, z)
    y0, y1 = lat_to_y(lat + dlat, z), lat_to_y(lat - dlat, z)
    return range(x0, x1 + 1), range(y0, y1 + 1)


_tls = threading.local()


def _session():
    s = getattr(_tls, "session", None)
    if s is None:
        s = requests.Session()
        adapter = HTTPAdapter(pool_connections=4, pool_maxsize=8, max_retries=0)
        s.mount("https://", adapter)
        s.headers.update(UA)
        _tls.session = s
    return s


def fetch(z, x, y):
    url = "%s/%d/%d/%d" % (BASE, z, y, x)
    last = None
    for _ in range(RETRIES):
        try:
            r = _session().get(url, timeout=TIMEOUT)
            if r.status_code == 200:
                return r.content
            last = "http %s" % r.status_code
        except Exception as e:  # noqa: BLE001
            last = str(e)[:100]
            time.sleep(0.4)
    print("FAIL z=%d x=%d y=%d (%s)" % (z, x, y, last), flush=True)
    return None


def parse_zooms(text):
    if "-" in text:
        a, b = text.split("-", 1)
        return list(range(int(a), int(b) + 1))
    return [int(v) for v in text.split(",") if v.strip()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", action="store_true")
    ap.add_argument("--name", default="tiles-morelia")
    ap.add_argument("--lat", type=float, default=19.7060)
    ap.add_argument("--lon", type=float, default=-101.1950)
    ap.add_argument("--km", type=float, default=20.0, help="half-side of the box, in km")
    ap.add_argument("--zooms", default="13-16")
    ap.add_argument("--version", type=int, default=2)
    ap.add_argument("--title", default="Mapas de Morelia")
    args = ap.parse_args()

    zooms = parse_zooms(args.zooms)
    plan = []
    total = 0
    for z in zooms:
        xs, ys = ranges(args.lat, args.lon, args.km, z)
        n = len(xs) * len(ys)
        plan.append((z, xs, ys))
        total += n
        print("z=%d  x=%d..%d  y=%d..%d  n=%d" % (z, xs[0], xs[-1], ys[0], ys[-1], n))
    print("tiles totales: %d  (~%.1f MB estimados)" % (total, total * 0.018))
    if args.plan:
        return 0

    os.makedirs(OUT, exist_ok=True)
    zip_path = os.path.join(OUT, "%s.zip" % args.name)
    lock = threading.Lock()
    done = [0]
    failed = [0]

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_STORED, allowZip64=True) as zf:
        for z, xs, ys in plan:
            items = [(x, y) for x in xs for y in ys]
            print("z=%d: %d tiles" % (z, len(items)), flush=True)

            def work(item):
                x, y = item
                data = fetch(z, x, y)
                if data is None:
                    with lock:
                        failed[0] += 1
                    return
                with lock:
                    zf.writestr("%d/%d/%d.tile" % (z, x, y), data)
                    done[0] += 1
                    if done[0] % 200 == 0:
                        print("  ...%d" % done[0], flush=True)

            with ThreadPoolExecutor(max_workers=THREADS) as ex:
                list(ex.map(work, items))

    size = os.path.getsize(zip_path)
    sha = hashlib.sha256(open(zip_path, "rb").read()).hexdigest()
    print("zip: %s  %d tiles  %.1f MB" % (zip_path, done[0], size / 1048576.0))
    if failed[0]:
        print("fallaron %d tiles" % failed[0])

    manifest = {
        "version": 1,
        "modules": [
            {
                "id": args.name,
                "name": args.title,
                "description": "Mapas offline z%d-%d alrededor de %.4f, %.4f"
                % (zooms[0], zooms[-1], args.lat, args.lon),
                "type": "tiles",
                "version": args.version,
                "sizeBytes": size,
                "sha256": sha,
                "url": "https://github.com/ChronosBVRX/IbizaDash/releases/latest/download/%s.zip"
                % args.name,
            }
        ],
    }
    manifest_path = os.path.join(OUT, "modules.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print("manifest: %s" % manifest_path)
    print("sha256: %s" % sha)
    return 0


if __name__ == "__main__":
    sys.exit(main())
