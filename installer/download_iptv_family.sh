#!/bin/bash
# IPTV Family - Downloader Script para Fire TV / Android
# Uso: ./download_iptv_family.sh
# Requiere: termux, adb, o cualquier terminal en Android

set -e

REPO="chanmailbot-dotcom/IPTV_Family"
API_URL="https://api.github.com/repos/$REPO/releases/latest"
DOWNLOAD_DIR="${HOME}/Downloads"
APK_NAME="iptv-family.apk"

echo "=========================================="
echo "  IPTV Family - Descargador APK"
echo "=========================================="
echo ""

# Verificar si curl está disponible
if ! command -v curl &> /dev/null; then
    echo "❌ Error: curl no está instalado"
    echo "   En Termux: pkg install curl"
    echo "   En Android con root: apt install curl"
    exit 1
fi

# Verificar si jq está disponible (para parsear JSON)
if ! command -v jq &> /dev/null; then
    echo "⚠️  jq no está instalado, intentando sin él..."
    USE_JQ=false
else
    USE_JQ=true
fi

# Crear directorio de descargas
mkdir -p "$DOWNLOAD_DIR"

echo "🔍 Obteniendo última versión desde GitHub..."

if [ "$USE_JQ" = true ]; then
    # Con jq
    DOWNLOAD_URL=$(curl -s "$API_URL" | jq -r '.assets[] | select(.name | endswith(".apk")) | .browser_download_url' | head -1)
    VERSION=$(curl -s "$API_URL" | jq -r '.tag_name')
else
    # Sin jq - usar grep/sed
    DOWNLOAD_URL=$(curl -s "$API_URL" | grep -o '"browser_download_url": "[^"]*\.apk"' | head -1 | sed 's/"browser_download_url": "//;s/"//')
    VERSION=$(curl -s "$API_URL" | grep -o '"tag_name": "[^"]*"' | head -1 | sed 's/"tag_name": "//;s/"//')
fi

if [ -z "$DOWNLOAD_URL" ] || [ "$DOWNLOAD_URL" = "null" ]; then
    echo "❌ No se encontró APK en el release más reciente"
    echo "   Verifica que el repositorio tenga releases con APKs"
    echo "   URL API: $API_URL"
    exit 1
fi

echo "✅ Versión encontrada: $VERSION"
echo "📥 Descargando APK..."

# Descargar con barra de progreso
curl -L --progress-bar -o "$DOWNLOAD_DIR/$APK_NAME" "$DOWNLOAD_URL"

if [ $? -eq 0 ]; then
    SIZE=$(du -h "$DOWNLOAD_DIR/$APK_NAME" | cut -f1)
    echo ""
    echo "=========================================="
    echo "✅ Descarga completada!"
    echo "=========================================="
    echo "📁 Archivo: $DOWNLOAD_DIR/$APK_NAME"
    echo "📦 Tamaño: $SIZE"
    echo "🏷️  Versión: $VERSION"
    echo ""
    echo "📋 Para instalar:"
    echo "   1. Abre el administrador de archivos"
    echo "   2. Ve a Descargas"
    echo "   3. Toca en $APK_NAME"
    echo "   4. Permite 'Instalar apps desconocidas' si te pide"
    echo ""
    
    # Intentar instalar automáticamente si es Android con ADB
    if command -v adb &> /dev/null; then
        echo "🔧 ADB detectado. ¿Quieres instalar automáticamente? (s/n)"
        read -r INSTALL
        if [[ "$INSTALL" =~ ^[Ss]$ ]]; then
            adb install -r "$DOWNLOAD_DIR/$APK_NAME"
            echo "✅ Instalado vía ADB"
        fi
    fi
else
    echo "❌ Error en la descarga"
    exit 1
fi