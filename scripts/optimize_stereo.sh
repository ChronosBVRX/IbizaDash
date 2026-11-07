#!/usr/bin/env bash
# optimize_stereo.sh — run on the real head unit when connected via ADB.
# Disables safe bloatware, restores animation scales and AOT-compiles IbizaDash.
set -e
ADB="${ADB:-adb}"

$ADB shell pm disable-user --user 0 com.dofun.market 2>/dev/null || true
$ADB shell pm disable-user --user 0 com.dofun.dofunweather.main 2>/dev/null || true
$ADB shell pm disable-user --user 0 com.tw.bootanimation 2>/dev/null || true
$ADB shell pm disable-user --user 0 com.tw.twfileexplore 2>/dev/null || true
$ADB shell pm disable-user --user 0 com.tw.auxin 2>/dev/null || true

$ADB shell settings put global window_animation_scale 0.5
$ADB shell settings put global transition_animation_scale 0.5
$ADB shell settings put global animator_duration_scale 0.5

$ADB shell cmd package compile -m speed -f com.chronosbvrx.ibizadash

echo "Optimization complete."
