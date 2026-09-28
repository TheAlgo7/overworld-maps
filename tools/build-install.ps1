# Builds the debug APK and installs it on the connected device. Run from any shell:
#   powershell -File tools/build-install.ps1 [-Serial <adb serial>]
# (gradlew.bat breaks on the space in "Overworld Maps" when called from Git Bash.)
param([string]$Serial = "")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..\android")
.\gradlew.bat :app:assembleDebug --console=plain -q
if ($LASTEXITCODE -ne 0) { Write-Host "BUILD FAILED"; exit 1 }
$apk = "app\build\outputs\apk\debug\app-debug.apk"
Write-Host ("built " + (Get-Item $apk).LastWriteTime)
if ($Serial) { adb -s $Serial install -r $apk } else { adb install -r $apk }
