# IPTV Family

**Reproductor IPTV para Windows, Linux y macOS — y Android TV / Fire TV en camino**

[![CI/CD](https://github.com/chanmailbot-dotcom/IPTV_Family/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/chanmailbot-dotcom/IPTV_Family/actions/workflows/ci-cd.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-purple.svg)](https://kotlinlang.org)

## Instalar en Windows

Descarga el `.msi` de la [última release](https://github.com/chanmailbot-dotcom/IPTV_Family/releases)
y ejecútalo. Trae el motor de vídeo y el runtime de Java dentro: **no hace falta
instalar VLC ni Java aparte**.

## Qué hace

- Listas **M3U** por URL, o un archivo `.m3u` que ya tengas en el equipo
- Paneles **Xtream Codes** (URL + usuario + contraseña)
- Vídeo **dentro de la aplicación** (HLS, MPEG-TS, RTMP) sobre libvlc
- Categorías del proveedor, con filtro TV en directo / Películas / Series
- Buscador de canales, favoritos, logos de canal
- Lista de zapeo al lado del vídeo para cambiar de canal sin salir
- Control parental con PIN para las categorías de adultos
- Pantalla completa, buffer de red ajustable, aceleración por hardware
- Recuerda la última lista abierta al arrancar

Atajos durante la reproducción: `Espacio` pausa, `F` pantalla completa, `Esc` sale de ella.

## Estructura del proyecto

```
shared/        Modulo KMP: modelos, parser M3U, cliente Xtream, XMLTV, persistencia
composeApp/    Aplicacion de escritorio (Compose Multiplatform + libvlc)
app/           Aplicacion Android — DESACTIVADA en settings.gradle.kts, pendiente de retomar
installer/     Utilidades de descarga/instalacion
scripts/       fetch-vlc.ps1 / fetch-vlc.sh: runtime de video para el empaquetado
```

La lógica de dominio vive en `shared/`, así que Android reutilizará el mismo
parser, el mismo cliente Xtream y el mismo repositorio cuando se reactive.

## Compilar

Requiere **JDK 17**.

```bash
./gradlew :shared:desktopTest :composeApp:desktopTest   # tests
./gradlew :composeApp:run                               # arrancar en local
./gradlew :composeApp:createDistributable               # imagen de la app, sin instalador
```

Para ejecutar en local hace falta libvlc en el sistema:
`sudo apt-get install vlc` en Linux, o instalar VLC en Windows/macOS.
El instalador que produce el CI ya lo lleva incluido.

### Instalador de Windows (MSI / EXE)

`jpackage` **no cruza plataformas**: el MSI solo se construye desde Windows.

- **Por CI (recomendado):** un push a `master` dispara el job `windows`,
  que descarga VLC, genera MSI y EXE y los publica en un GitHub Release.
- **A mano en Windows:** JDK 17 y [WiX 3.11](https://github.com/wixtoolset/wix3/releases)
  en el `PATH`, y luego:

```powershell
.\scripts\fetch-vlc.ps1
.\gradlew :composeApp:packageMsi
```

El resultado queda en `composeApp\build\compose\binaries\main\msi\`.

## Pendiente

- **EPG**: `XmltvParser` ya está en `shared` pero no está conectado a la UI, y
  `Playlist` todavía no tiene un campo para la URL XMLTV.
- **Android TV / Fire TV**: `:app` está desactivado. Hay que añadirle el plugin
  de compilador de Compose, migrarlo a consumir `shared`, y darle navegación por
  D-pad y reproducción con Media3.

Ver [PLAN.md](PLAN.md) para el detalle.

## Licencia

MIT — ver [LICENSE](LICENSE).

---

**Nota**: esta aplicación no incluye ningún contenido. Necesitas tu propia lista
M3U o tus credenciales Xtream Codes de tu proveedor.
