#!/usr/bin/env bash
# Copia el libvlc del sistema al arbol de recursos para que el paquete Linux
# (.deb) lleve el motor de video incluido.
#
# Uso:  scripts/fetch-vlc.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
dest="$root/composeApp/resources/linux-x64/vlc"

libdir="$(dirname "$(ls /usr/lib/*/libvlc.so.5 2>/dev/null | head -1)")" || true
if [[ -z "${libdir:-}" || ! -d "$libdir" ]]; then
    echo "No se encontro libvlc en el sistema. Instala: sudo apt install vlc libvlc-dev" >&2
    exit 1
fi

mkdir -p "$dest"
cp -f "$libdir"/libvlc.so* "$libdir"/libvlccore.so* "$dest/"
cp -rf "$libdir/vlc/plugins" "$dest/"
echo "VLC listo en $dest"
