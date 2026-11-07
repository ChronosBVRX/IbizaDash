#!/usr/bin/env python3
"""Offline map pack builder for IbizaDash (stereo) + IbizaCompanion (phone).

Downloads Esri World Dark Gray Canvas tiles for Michoacan + neighboring
states and writes:
  - one .mbtiles file per zoom (stereo osmdroid reads them automatically)
  - a z/x/y.png tree (phone Leaflet loads it via file:// with online fallback)

Usage:
    python scripts/build_offline_pack.py --plan      # print tile counts only
    python scripts/build_offline_pack.py             # download everything
    python scripts/build_offline_pack.py --zip 15    # only zoom 15, etc.

Resume-safe: existing tiles in .mbtiles / PNG tree are skipped.
"""
import argparse
import math
import os
import sqlite3
import sys
import threading
import time
import zipfile

import requests
from requests.adapters import HTTPAdapter
from concurrent.futures import ThreadPoolExecutor

BASE = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile"
UA = {"User-Agent": "IbizaDash-offline-pack/1.0 (personal use; attribution shown in app)"}
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "dist", "offline-maps")

# lon_min, lon_max, lat_min, lat_max, zooms
JOBS = [
    # Michoacan + vecinos (Gto, Jal, Edomex, Gro, Qro, Colima): overview zooms
    (-104.6, -99.8, 17.6, 21.6, [11, 12, 13]),
    # Michoacan a detalle: ciudad y carretera
    (-103.6, -99.9, 18.4, 20.6, [14, 15]),
]

THREADS = 20
RETRIES = 4
TIMEOUT = 25


def lon_to_x(lon, z):
    return int((lon + 180.0) / 360.0 * (1 << z))


def lat_to_y(lat, z):
    r = math.radians(lat)
    return int((1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0 * (1 << z))


def ranges(lon0, lon1, lat0, lat1, z):
    x0, x1 = lon_to_x(lon0, z), lon_to_x(lon1, z)
    y0, y1 = lat_to_y(lat1, z), lat_to_y(lat0, z)  # y grows south
    return range(x0, x1 + 1), range(y0, y1 + 1)


def mbtiles_path(z):
    return os.path.join(OUT, "mbtiles", "esri_dark_z%d.mbtiles" % z)


def png_path(z, x, y):
    return os.path.join(OUT, "png", str(z), str(x), "%d.png" % y)


def ensure_mbtiles(path, z):
    fresh = not os.path.exists(path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    db = sqlite3.connect(path, timeout=60.0, check_same_thread=False)
    if fresh:
        db.execute("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT)")
        db.execute("CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
        db.execute("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row)")
        db.execute("INSERT OR REPLACE INTO metadata VALUES ('name','EsriDark')")
        db.execute("INSERT OR REPLACE INTO metadata VALUES ('type','baselayer')")
        db.execute("INSERT OR REPLACE INTO metadata VALUES ('version','1')")
        db.execute("INSERT OR REPLACE INTO metadata VALUES ('format','png')")
        db.execute("INSERT OR REPLACE INTO metadata VALUES ('minzoom',%d)" % z)
        db.execute("INSERT OR REPLACE INTO metadata VALUES ('maxzoom',%d)" % z)
        db.commit()
    return db


_tls = threading.local()


def _session():
    """One keep-alive session per worker: avoids a TLS handshake per tile."""
    s = getattr(_tls, "session", None)
    if s is None:
        s = requests.Session()
        adapter = HTTPAdapter(pool_connections=4, pool_maxsize=8, max_retries=0)
        s.mount("https://", adapter)
        s.mount("http://", adapter)
        s.headers.update(UA)
        _tls.session = s
    return s


def fetch(z, x, y):
    # MBTiles uses TMS (flipped Y); Esri/XYZ uses Google Y. Convert.
    tms_y = (1 << z) - 1 - y
    url = "%s/%d/%d/%d" % (BASE, z, y, x)
    last = None
    for _ in range(RETRIES):
        try:
            r = _session().get(url, timeout=TIMEOUT)
            if r.status_code == 200:
                return r.content, tms_y
            last = "http %s" % r.status_code
        except Exception as e:  # noqa: BLE001 - network flakes, retry
            last = str(e)[:100]
            time.sleep(0.5)
    print("FAIL z=%d x=%d y=%d (%s)" % (z, x, y, last), flush=True)
    return None, tms_y


def make_zip():
    png_dir = os.path.join(OUT, "png")
    if not os.path.isdir(png_dir):
        print("no hay arbol PNG en %s" % png_dir)
        return
    zip_path = os.path.join(os.path.dirname(OUT), "offline-tiles.zip")
    count = 0
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_STORED) as zf:
        for root, _dirs, files in os.walk(png_dir):
            for name in files:
                full = os.path.join(root, name)
                arc = os.path.relpath(full, png_dir)
                zf.write(full, arc)
                count += 1
    size = os.path.getsize(zip_path) / (1024 * 1024)
    print("zip: %s  %d tiles  %.1f MB" % (zip_path, count, size))


def repair_mbtiles():
    """Copies PNG tiles that are missing from the MBTiles databases.

    The downloader writes both, but a killed run can leave PNGs on disk whose
    database row was never committed. osmdroid only reads the .mbtiles files,
    so those tiles must be inserted before deploying.
    """
    zoom_levels = sorted({z for _lon0, _lon1, _lat0, _lat1, zs in JOBS for z in zs})
    for z in zoom_levels:
        png_z = os.path.join(OUT, "png", str(z))
        db_path = mbtiles_path(z)
        db = ensure_mbtiles(db_path, z)
        try:
            have = set(db.execute(
                "SELECT tile_column, tile_row FROM tiles WHERE zoom_level=?", (z,)
            ).fetchall())
            fixed = 0
            if os.path.isdir(png_z):
                for x_name in os.listdir(png_z):
                    x_dir = os.path.join(png_z, x_name)
                    if not os.path.isdir(x_dir):
                        continue
                    try:
                        x = int(x_name)
                    except ValueError:
                        continue
                    for fname in os.listdir(x_dir):
                        if not fname.endswith(".png"):
                            continue
                        try:
                            y = int(fname[:-4])
                        except ValueError:
                            continue
                        tms_y = (1 << z) - 1 - y
                        if (x, tms_y) in have:
                            continue
                        with open(os.path.join(x_dir, fname), "rb") as f:
                            data = f.read()
                        db.execute(
                            "INSERT OR IGNORE INTO tiles VALUES (?,?,?,?)",
                            (z, x, tms_y, sqlite3.Binary(data)),
                        )
                        fixed += 1
            db.commit()
            print("z=%d reparado: %d tiles insertados" % (z, fixed))
        finally:
            db.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", action="store_true")
    ap.add_argument("--zip", type=int, default=None)
    ap.add_argument("--make-zip", action="store_true",
                    help="empaqueta dist/offline-maps/png en dist/offline-tiles.zip")
    ap.add_argument("--repair", action="store_true",
                    help="inserta en los .mbtiles los PNG que falten")
    args = ap.parse_args()

    if args.make_zip:
        make_zip()
        return
    if args.repair:
        repair_mbtiles()
        return

    jobs = []
    for lon0, lon1, lat0, lat1, zooms in JOBS:
        for z in zooms:
            if args.zip is not None and z != args.zip:
                continue
            xs, ys = ranges(lon0, lon1, lat0, lat1, z)
            jobs.append((z, list(xs), list(ys)))

    total = sum(len(xs) * len(ys) for _, xs, ys in jobs)
    print("zooms: %s" % sorted({z for z, _, _ in jobs}))
    print("tiles totales: %d" % total)
    for z, xs, ys in jobs:
        print("  z=%d  x=%d..%d  y=%d..%d  n=%d" % (z, xs[0], xs[-1], ys[0], ys[-1], len(xs) * len(ys)))
    if args.plan:
        return

    os.makedirs(OUT, exist_ok=True)
    lock = threading.Lock()
    done = [0]

    for z, xs, ys in jobs:
        db = ensure_mbtiles(mbtiles_path(z), z)
        # existing keys for resume
        have = set()
        try:
            have = set(db.execute(
                "SELECT tile_column, tile_row FROM tiles WHERE zoom_level=?", (z,)).fetchall())
        except sqlite3.Error:
            pass
        missing = [(x, (1 << z) - 1 - y, y) for x in xs for y in ys
                   if (x, (1 << z) - 1 - y) not in have
                   and not os.path.exists(png_path(z, x, y))]
        print("z=%d: %d/%d faltantes" % (z, len(missing), len(xs) * len(ys)), flush=True)

        def work(item):
            x, tms_y, y = item
            data, _ = fetch(z, x, y)
            if data is None:
                return
            pp = png_path(z, x, y)
            os.makedirs(os.path.dirname(pp), exist_ok=True)
            with open(pp, "wb") as f:
                f.write(data)
            with lock:
                db.execute(
                    "INSERT OR IGNORE INTO tiles VALUES (?,?,?,?)",
                    (z, x, tms_y, sqlite3.Binary(data)))
                done[0] += 1
                if done[0] % 200 == 0:
                    db.commit()
                    print("  ...%d descargados" % done[0], flush=True)

        with ThreadPoolExecutor(max_workers=THREADS) as ex:
            list(ex.map(work, missing))
        with lock:
            db.commit()
        db.close()
        print("z=%d listo" % z, flush=True)
    print("TOTAL descargados esta corrida: %d" % done[0])


if __name__ == "__main__":
    sys.exit(main())
