<#
    Descarga el runtime de VLC y lo coloca donde el empaquetador lo espera,
    para que el instalador de Windows lleve el motor de video incluido y el
    usuario no tenga que instalar VLC aparte.

    Uso:  powershell -ExecutionPolicy Bypass -File scripts\fetch-vlc.ps1
#>
$ErrorActionPreference = "Stop"

$version = if ($env:VLC_VERSION) { $env:VLC_VERSION } else { "3.0.21" }
$root    = Split-Path -Parent $PSScriptRoot
$dest    = Join-Path $root "composeApp\resources\windows-x64\vlc"

if (Test-Path (Join-Path $dest "plugins")) {
    Write-Host "VLC $version ya esta en $dest, nada que hacer."
    exit 0
}

$work = Join-Path $env:TEMP "vlc-fetch"
$zip  = Join-Path $work "vlc.zip"
New-Item -ItemType Directory -Force -Path $work | Out-Null

Write-Host "Descargando VLC $version (win64)..."
Invoke-WebRequest -Uri "https://get.videolan.org/vlc/$version/win64/vlc-$version-win64.zip" `
    -OutFile $zip -UseBasicParsing

Write-Host "Extrayendo..."
Expand-Archive -Path $zip -DestinationPath $work -Force
$src = Join-Path $work "vlc-$version"

New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item (Join-Path $src "libvlc.dll")     $dest -Force
Copy-Item (Join-Path $src "libvlccore.dll") $dest -Force
Copy-Item (Join-Path $src "plugins")        $dest -Recurse -Force

Remove-Item $work -Recurse -Force
Write-Host "VLC listo en $dest"
