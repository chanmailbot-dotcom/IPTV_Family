# IPTV Family - Downloader & Installer

Este directorio contiene varias opciones para descargar e instalar IPTV Family en dispositivos Android, Fire TV y Android TV.

## 📁 Estructura

```
installer/
├── index.html                    # Página web para descargar (GitHub Pages)
├── download_iptv_family.sh       # Script Bash (Linux/Termux/ADB)
├── download_iptv_family.py       # Script Python (multiplataforma)
└── AndroidApp/                   # App Android nativa (instalador 1-click)
    ├── app/
    │   ├── src/main/
    │   │   ├── AndroidManifest.xml
    │   │   ├── java/com/iptv/family/downloader/
    │   │   │   ├── DownloaderApplication.kt
    │   │   │   └── MainActivity.kt
    │   │   ├── res/
    │   │   │   ├── values/strings.xml
    │   │   │   └── xml/file_paths.xml
    │   │   └── ...
    │   ├── build.gradle.kts
    │   └── ...
    ├── build.gradle.kts
    └── settings.gradle.kts
```

---

## 🌐 Opción 1: Página Web (GitHub Pages) - **Más fácil para Fire TV**

### Uso directo:
1. Sube `index.html` a tu repositorio en la rama `gh-pages`
2. Activa GitHub Pages en Settings > Pages > Source: Deploy from branch > gh-pages
3. Accede desde Fire TV: `https://TU_USUARIO.github.io/IPTV_Family/`

### URL directa (si ya tienes GitHub Pages):
```
https://chanmailbot-dotcom.github.io/IPTV_Family/
```

### Características:
- ✅ Detecta automáticamente Fire TV / Android TV
- ✅ Botón grande optimizado para mando a distancia
- ✅ Código QR para escanear con el móvil
- ✅ Lista de características visible
- ✅ Sin instalación previa necesaria

---

## 🐍 Opción 2: Script Python - **Universal (Termux, Linux, Windows, macOS)**

### Requisitos:
- Python 3.6+
- Conexión a internet

### Uso en Termux (Android):
```bash
pkg install python
python3 download_iptv_family.py
```

### Uso en Linux/macOS/Windows:
```bash
python3 download_iptv_family.py
```

### Características:
- ✅ Barra de progreso visual
- ✅ Detección automática de Termux/Android
- ✅ Manejo de errores robusto
- ✅ Sin dependencias externas (solo stdlib)
- ✅ Funciona en cualquier plataforma con Python

---

## 🐚 Opción 3: Script Bash - **Para terminales Linux/Termux/ADB**

### Requisitos:
- `curl` (obligatorio)
- `jq` (opcional, para mejor parsing JSON)

### Uso en Termux:
```bash
pkg install curl jq
chmod +x download_iptv_family.sh
./download_iptv_family.sh
```

### Uso con ADB (desde PC):
```bash
adb push download_iptv_family.sh /sdcard/Download/
adb shell "chmod +x /sdcard/Download/download_iptv_family.sh && /sdcard/Download/download_iptv_family.sh"
```

### Características:
- ✅ Instalación automática vía ADB si está disponible
- ✅ Barra de progreso nativa de curl
- ✅ Detección de versión desde GitHub API
- ✅ Funciona en cualquier shell POSIX

---

## 📱 Opción 4: App Android Nativa (Instalador 1-Click) - **Mejor experiencia en TV**

### Compilar:
```bash
cd AndroidApp
./gradlew assembleRelease
# APK en: app/build/outputs/apk/release/app-release.apk
```

### Instalar en Fire TV / Android TV:
1. Compila el APK (arriba)
2. Instala en el dispositivo:
   ```bash
   adb install app/build/outputs/apk/release/app-release.apk
   ```
3. Abre "IPTV Family Downloader" desde el launcher
4. Pulsa "Descargar e Instalar"
5. Permite "Instalar apps desconocidas" si te pide
6. ¡Listo! Se abre IPTV Family automáticamente

### Características:
- ✅ UI nativa Compose optimizada para TV (Leanback)
- ✅ Navegación con mando a distancia (D-pad)
- ✅ Descarga en background con progreso visual
- ✅ Instalación automática via FileProvider (Android 8+)
- ✅ Manejo de permisos automático
- ✅ Tema oscuro pensado para verse de lejos en una televisión
- ✅ Compatible Android 5.0+ (API 21+)

---

## 🔗 URLs de Descarga Directa (para scripts)

### Último Release (siempre actualizado):
```
https://github.com/chanmailbot-dotcom/IPTV_Family/releases/latest/download/app-release.apk
```

### Release Específico (ej. v1.0.0):
```
https://github.com/chanmailbot-dotcom/IPTV_Family/releases/download/v1.0.0/app-release.apk
```

### API GitHub (para obtener info):
```
https://api.github.com/repos/chanmailbot-dotcom/IPTV_Family/releases/latest
```

---

## 📋 Para Fire TV Stick / Fire TV Cube (Método Recomendado)

### Método A: Navegador Silk + GitHub Pages
1. Abre **Silk Browser** en Fire TV
2. Ve a: `https://chanmailbot-dotcom.github.io/IPTV_Family/`
3. Pulsa "Descargar e Instalar APK"
4. Permite instalación de "Silk Browser" en Configuración
5. Instala el APK

### Método B: App "Downloader" (AFTVnews)
1. Instala **Downloader** desde Amazon Appstore
2. En Downloader, ve a: `https://github.com/chanmailbot-dotcom/IPTV_Family/releases/latest/download/app-release.apk`
3. Pulsa "Go" → Descarga → Instalar

### Método C: ADB desde PC
```bash
# Conectar por ADB (activar depuración USB en Fire TV)
adb connect <IP_FIRE_TV>

# Instalar directamente
adb install https://github.com/chanmailbot-dotcom/IPTV_Family/releases/latest/download/app-release.apk
```

---

## 🛠️ Publicar en GitHub Pages (Para tener tu propia página de descarga)

```bash
# En tu repo IPTV_Family
git checkout --orphan gh-pages
git rm -rf .
cp /root/iptv-family/installer/index.html .
git add index.html
git commit -m "Add download page"
git push origin gh-pages

# En GitHub: Settings > Pages > Source: gh-pages branch
```

Tu página estará en: `https://TU_USUARIO.github.io/IPTV_Family/`

---

## 🔐 Notas de Seguridad

- Los APKs se descargan **directamente de GitHub Releases** (HTTPS)
- Verifica la firma del APK comparando SHA256 con el release
- La app instaladora usa `FileProvider` (seguro, sin `FLAG_GRANT_READ_URI_PERMISSION` riesgoso)
- No se recopila ningún dato personal

---

## 📞 Soporte

- **Issues**: https://github.com/chanmailbot-dotcom/IPTV_Family/issues
- **Repo principal**: https://github.com/chanmailbot-dotcom/IPTV_Family
- **Releases**: https://github.com/chanmailbot-dotcom/IPTV_Family/releases