# Deploy the offline map packs built by scripts/build_offline_pack.py.
#
#   .\scripts\deploy_offline.ps1
#   .\scripts\deploy_offline.ps1 -Stereo 10.246.3.182:5555 -Phone df0a78c4
#
# Stereo: MBTiles go to the osmdroid BASE folder (scanned for archives).
# Phone : offline-tiles.zip in getExternalFilesDir(null); the app extracts it.
param(
    [string]$Stereo = "10.246.3.182:5555",
    [string]$Phone = "df0a78c4",
    [switch]$SkipStereo,
    [switch]$SkipPhone
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = "C:\Users\Axel Rosete\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$mbtiles = Join-Path $root "dist\offline-maps\mbtiles"
$png = Join-Path $root "dist\offline-maps\png"

function Invoke-Adb([string]$serial, [string[]]$arguments) {
    & $adb -s $serial @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb -s $serial $($arguments -join ' ') failed"
    }
}

if (-not (Test-Path $adb)) { throw "adb not found: $adb" }

if (-not $SkipStereo) {
    & $adb connect $Stereo | Out-Null
    Write-Host "Stereo: $Stereo"
    $stereoDir = "/sdcard/Android/data/com.chronosbvrx.ibizadash/files/osmdroid"
    Invoke-Adb $Stereo @("shell", "mkdir", "-p", $stereoDir)
    # Remove the early test pack and any packs left in the old tiles folder:
    # osmdroid scans the base path (not the tile cache) for .mbtiles archives.
    & $adb -s $Stereo shell rm -f "$stereoDir/test_z15.mbtiles" | Out-Null
    & $adb -s $Stereo shell rm -f "$stereoDir/tiles/test_z15.mbtiles" | Out-Null
    & $adb -s $Stereo shell rm -f "$stereoDir/tiles/esri_dark_z*.mbtiles" | Out-Null
    Get-ChildItem $mbtiles -Filter *.mbtiles | ForEach-Object {
        Write-Host "  push $($_.Name) ($([math]::Round($_.Length / 1MB, 1)) MB)"
        Invoke-Adb $Stereo @("push", $_.FullName, "$stereoDir/$($_.Name)")
    }
    # osmdroid enumerates archives when the map tile provider is created:
    # restart the launcher so the new packs are picked up.
    Write-Host "  restarting IbizaDash so osmdroid indexes the packs…"
    & $adb -s $Stereo shell am force-stop com.chronosbvrx.ibizadash | Out-Null
    & $adb -s $Stereo shell am start -n com.chronosbvrx.ibizadash/.MainActivity | Out-Null
}

if (-not $SkipPhone) {
    Write-Host "Phone: $Phone"
    $phoneBase = "/sdcard/Android/data/com.chronosbvrx.ibizacompanion/files"
    $phoneDir = "$phoneBase/offline-tiles"
    Invoke-Adb $Phone @("shell", "mkdir", "-p", $phoneBase)
    $zip = Join-Path $root "dist\offline-tiles.zip"
    if (Test-Path $zip) {
        Write-Host "  push offline-tiles.zip ($([math]::Round((Get-Item $zip).Length / 1MB, 1)) MB); the app extracts it on launch"
        Invoke-Adb $Phone @("push", $zip, "$phoneBase/offline-tiles.zip")
        Write-Host "  restarting IbizaCompanion so it extracts the pack…"
        & $adb -s $Phone shell am force-stop com.chronosbvrx.ibizacompanion | Out-Null
        & $adb -s $Phone shell am start -n com.chronosbvrx.ibizacompanion/.MainActivity | Out-Null
    } elseif (Test-Path $png) {
        Write-Host "  push PNG tree (this can take a while)…"
        Invoke-Adb $Phone @("shell", "mkdir", "-p", $phoneDir)
        & $adb -s $Phone push "$png\." "$phoneDir/"
        if ($LASTEXITCODE -ne 0) {
            throw "phone tile push failed"
        }
    } else {
        Write-Warning "No PNG tree or zip at dist\offline-maps or dist\offline-tiles.zip"
    }
}

Write-Host "Done."
