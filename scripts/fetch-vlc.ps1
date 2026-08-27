# Descarga el runtime de libvlc y lo deja donde Compose Desktop lo empaqueta.
#
# Compose copia composeApp/resources/windows-x64/** dentro del instalador, y en
# tiempo de ejecucion la app lo localiza via compose.application.resources.dir
# (ver VlcNative en VlcPlayer.kt).
#
# La version NO se fija a mano: VideoLAN retira las antiguas de get.videolan.org
# y el build se rompia al buscar una que ya no existia. Se descubre la actual en
# /vlc/last/ y se verifica el sha256 que publica VideoLAN junto al zip.
#
# Se incluye el arbol completo de plugins: recortarlo ahorra ~40 MB pero rompe
# formatos concretos de stream de forma dificil de diagnosticar.

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'   # Write-Progress ralentiza mucho en CI

$discoverBase = 'https://get.videolan.org/vlc/last/win64'
# get.videolan.org reparte entre mirrors por geoIP: a veces en vez de servir el zip
# devuelve una pagina HTML de aterrizaje con un meta-refresh a los 5s, sin dar 404 ni
# error HTTP. Para descubrir la version es inofensivo (solo lista nombres de fichero),
# pero para el binario usamos el FTP maestro de VideoLAN, que siempre sirve el fichero
# directo y no hace ese redirect "cortes".
$downloadBase = 'https://download.videolan.org/pub/videolan/vlc'
$repoRoot = Split-Path -Parent $PSScriptRoot
$target   = Join-Path $repoRoot 'composeApp\resources\windows-x64\vlc'

if (Test-Path (Join-Path $target 'libvlc.dll')) {
    Write-Host "libvlc ya presente en $target, nada que hacer."
    exit 0
}

$work = if ($env:RUNNER_TEMP) { Join-Path $env:RUNNER_TEMP 'vlc-download' }
        else { Join-Path $env:TEMP 'vlc-download' }
New-Item -ItemType Directory -Force -Path $work | Out-Null

Write-Host "Buscando la ultima version de VLC en $discoverBase"
$index = Invoke-WebRequest -Uri "$discoverBase/" -UseBasicParsing
$zipName = ([regex]::Matches($index.Content, 'vlc-\d+\.\d+\.\d+-win64\.zip(?!\.)') |
            ForEach-Object { $_.Value } | Sort-Object -Unique | Select-Object -First 1)
if (-not $zipName) { throw "No se encontro ningun vlc-*-win64.zip en $discoverBase" }

$version = [regex]::Match($zipName, '\d+\.\d+\.\d+').Value
Write-Host "Version detectada: $version"

$base = "$downloadBase/$version/win64"
$zip = Join-Path $work $zipName
Write-Host "Descargando $base/$zipName"
Invoke-WebRequest -Uri "$base/$zipName" -OutFile $zip -UseBasicParsing

$sizeMb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
if ($sizeMb -lt 40) { throw "La descarga son solo $sizeMb MB: no es el zip de VLC (probablemente una pagina de error)." }
Write-Host "Descargados $sizeMb MB"

# Verificar la descarga: se ejecuta codigo nativo desde este zip, no basta con confiar en la URL.
Write-Host 'Verificando sha256'
# download.videolan.org sirve el .sha256 como application/octet-stream: en Windows
# PowerShell 5.1, Invoke-WebRequest con -UseBasicParsing devuelve entonces Content
# como byte[] en vez de string, así que hay que decodificarlo explícitamente.
$shaResponse = Invoke-WebRequest -Uri "$base/$zipName.sha256" -UseBasicParsing
$shaText = if ($shaResponse.Content -is [byte[]]) {
    [System.Text.Encoding]::UTF8.GetString($shaResponse.Content)
} else {
    $shaResponse.Content
}
$expected = ($shaText -split '\s+')[0].Trim().ToLower()
$actual   = (Get-FileHash -Path $zip -Algorithm SHA256).Hash.ToLower()
if ($expected -ne $actual) { throw "sha256 no coincide.`n  esperado: $expected`n  obtenido: $actual" }
Write-Host "sha256 correcto: $actual"

Write-Host 'Descomprimiendo'
Expand-Archive -Path $zip -DestinationPath $work -Force

$source = Join-Path $work "vlc-$version"
if (-not (Test-Path $source)) { throw "No se encontro $source tras descomprimir" }

New-Item -ItemType Directory -Force -Path $target | Out-Null
Copy-Item (Join-Path $source 'libvlc.dll')     $target -Force
Copy-Item (Join-Path $source 'libvlccore.dll') $target -Force
Copy-Item (Join-Path $source 'plugins')        $target -Recurse -Force

foreach ($needed in 'libvlc.dll', 'libvlccore.dll', 'plugins') {
    if (-not (Test-Path (Join-Path $target $needed))) { throw "Falta $needed en $target" }
}

$size = [math]::Round(((Get-ChildItem $target -Recurse -File | Measure-Object Length -Sum).Sum / 1MB), 1)
$plugins = (Get-ChildItem (Join-Path $target 'plugins') -Recurse -Filter *.dll).Count
Write-Host "libvlc $version listo en $target ($size MB, $plugins plugins)"
