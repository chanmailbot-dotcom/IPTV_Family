#!/usr/bin/env python3
"""
IPTV Family - Downloader Python Script
Funciona en: Android (Termux), Linux, Windows, macOS
Uso: python3 download_iptv_family.py
"""

import os
import sys
import json
import urllib.request
import urllib.error
from pathlib import Path

REPO = "chanmailbot-dotcom/IPTV_Family"
API_URL = f"https://api.github.com/repos/{REPO}/releases/latest"
DOWNLOAD_DIR = Path.home() / "Downloads"
APK_NAME = "iptv-family.apk"

def print_banner():
    print("=" * 50)
    print("  IPTV Family - Descargador APK (Python)")
    print("=" * 50)
    print()

def get_latest_release():
    """Obtiene info del último release de GitHub"""
    try:
        req = urllib.request.Request(API_URL, headers={'User-Agent': 'IPTV-Family-Downloader/1.0'})
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.loads(response.read().decode())
        
        # Buscar asset APK
        apk_asset = None
        for asset in data.get('assets', []):
            if asset['name'].endswith('.apk'):
                apk_asset = asset
                break
        
        if not apk_asset:
            return None, None, "No se encontró APK en el release"
        
        return apk_asset['browser_download_url'], data.get('tag_name', 'unknown'), None
        
    except urllib.error.URLError as e:
        return None, None, f"Error de red: {e.reason}"
    except json.JSONDecodeError:
        return None, None, "Error parseando respuesta de GitHub"
    except Exception as e:
        return None, None, f"Error inesperado: {e}"

def download_file(url, dest_path):
    """Descarga archivo con barra de progreso"""
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'IPTV-Family-Downloader/1.0'})
        with urllib.request.urlopen(req, timeout=60) as response:
            total_size = int(response.headers.get('Content-Length', 0))
            downloaded = 0
            chunk_size = 8192
            
            with open(dest_path, 'wb') as f:
                while True:
                    chunk = response.read(chunk_size)
                    if not chunk:
                        break
                    f.write(chunk)
                    downloaded += len(chunk)
                    
                    if total_size > 0:
                        percent = (downloaded * 100) // total_size
                        bar_len = 30
                        filled = (percent * bar_len) // 100
                        bar = '█' * filled + '░' * (bar_len - filled)
                        mb_downloaded = downloaded / (1024 * 1024)
                        mb_total = total_size / (1024 * 1024)
                        print(f"\r  [{bar}] {percent}% ({mb_downloaded:.1f}/{mb_total:.1f} MB)", end='', flush=True)
        
        print()  # Nueva línea después de la barra
        return True, None
        
    except Exception as e:
        return False, str(e)

def main():
    print_banner()
    
    # Crear directorio
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    dest_path = DOWNLOAD_DIR / APK_NAME
    
    print(f"📂 Directorio de descarga: {DOWNLOAD_DIR}")
    print()
    
    # Obtener release
    print("🔍 Consultando GitHub API...")
    download_url, version, error = get_latest_release()
    
    if error:
        print(f"❌ {error}")
        print(f"   Verifica manualmente: https://github.com/{REPO}/releases")
        sys.exit(1)
    
    print(f"✅ Versión: {version}")
    print(f"📥 Descargando...")
    print()
    
    # Descargar
    success, error = download_file(download_url, dest_path)
    
    if success:
        size_mb = dest_path.stat().st_size / (1024 * 1024)
        print()
        print("=" * 50)
        print("✅ ¡Descarga completada!")
        print("=" * 50)
        print(f"📁 Archivo: {dest_path}")
        print(f"📦 Tamaño: {size_mb:.1f} MB")
        print(f"🏷️  Versión: {version}")
        print()
        print("📋 Para instalar en Fire TV / Android:")
        print("   1. Abre 'Gestor de archivos' o 'Downloader'")
        print(f"   2. Ve a: {DOWNLOAD_DIR}")
        print(f"   3. Toca en: {APK_NAME}")
        print("   4. Permite 'Instalar apps desconocidas' si te pide")
        print()
        
        # Verificar si es Android (Termux)
        if 'TERMUX_VERSION' in os.environ or os.path.exists('/data/data/com.termux'):
            print("📱 Detectado Termux en Android")
            print("   Puedes instalar con: termux-open", dest_path)
        
    else:
        print()
        print(f"❌ Error en descarga: {error}")
        sys.exit(1)

if __name__ == "__main__":
    main()