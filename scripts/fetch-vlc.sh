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

# La licencia viaja CON el binario: tanto la LGPL como la GPL exigen que el texto
# acompañe a lo que se distribuye, y aqui se estaba repartiendo libvlc y sus
# plugins sin una sola linea de licencia. En Debian/Ubuntu el paquete deja el
# texto en /usr/share/doc/libvlc5 (o vlc); se copia el que se encuentre.
copiada=0
for doc in /usr/share/doc/libvlc5 /usr/share/doc/libvlccore9 /usr/share/doc/vlc; do
    if [[ -f "$doc/copyright" ]]; then
        cp -f "$doc/copyright" "$dest/COPYING-vlc.txt"
        echo "Licencia de VLC copiada desde $doc/copyright"
        copiada=1
        break
    fi
done
if [[ "$copiada" -eq 0 ]]; then
    echo "AVISO: no se encontro el texto de licencia de VLC; revisa licencias/ a mano." >&2
fi

echo "VLC listo en $dest"
