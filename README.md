# IPTV Family

**Reproductor IPTV para Windows y para Android TV / Fire TV**

[![CI/CD](https://github.com/chanmailbot-dotcom/IPTV_Family/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/chanmailbot-dotcom/IPTV_Family/actions/workflows/ci-cd.yml)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-purple.svg)](https://kotlinlang.org)

> **Esta aplicación no incluye ningún contenido, ni canales, ni listas.** Es un
> reproductor: hace falta tu propia lista M3U o las credenciales del proveedor
> que hayas contratado. Tampoco se distribuye ninguna lista con el programa.

## Instalar

**Windows** — descarga el `.msi` de la [última release](https://github.com/chanmailbot-dotcom/IPTV_Family/releases)
y ejecútalo. Trae el motor de vídeo y el runtime de Java dentro: no hace falta
instalar VLC ni Java aparte. No pide permisos de administrador.

**Android TV / Fire TV** — descarga el `.apk` de la misma release. Si el nombre
lleva `debug`, es una versión de pruebas (ver [docs/firmar-apk.md](docs/firmar-apk.md)).

## Qué hace

Común a las dos plataformas, sobre el mismo núcleo compartido:

- Listas **M3U** por URL o archivo local, y paneles **Xtream Codes**
- Categorías del proveedor con filtro TV en directo / Películas / Series
- Buscador, favoritos, logos de canal
- **Series**: al abrir una serie se piden sus episodios (`get_series_info`), que
  es la única forma de reproducirlos en Xtream
- **Guía EPG** (XMLTV): «Ahora» en la lista de canales y en el reproductor, con
  la hora de fin y el programa siguiente. La guía se lee en streaming, así que
  una guía de cientos de MB no agota la memoria del aparato
- **Audio en español por defecto**, descartando la audiodescripción, que en la
  televisión española suele venir como primera pista; y selector manual de pista
  de audio y de subtítulos
- **Control parental** con PIN por categoría (el PIN se guarda derivado, no en claro)
- **Modo sin conexión**: si el proveedor no responde, se muestra la última lista
  descargada, diciendo de cuándo es
- La contraseña del proveedor se guarda **cifrada** (DPAPI en Windows)

Solo en **escritorio**:

- Vídeo embebido con libvlc (HLS, MPEG-TS, RTMP)
- **Servidor web** para ver y cambiar de canal desde el móvil o desde fuera de
  casa, con HTTPS, usuario y contraseña, y freno a los intentos de acceso
- Atajos: `Espacio` pausa, `F` pantalla completa, `Esc` sale de ella
- La ventana se adapta a anchos pequeños

Solo en **Android TV / Fire TV**:

- Manejo por mando en dos modos: con los mandos ocultos las flechas zapean; al
  pulsar OK aparecen los controles y las flechas se mueven por ellos
- Vista previa del canal al mover el foco, y miniatura mientras navegas
- Teclas de medios (play/pausa, canal +/−, avance rápido)

## Estructura

```
shared/        Núcleo KMP: modelos, parser M3U, cliente Xtream, XMLTV,
               persistencia, estado de la biblioteca, textos traducidos
composeApp/    Escritorio (Compose Multiplatform + libvlc) + servidor web
app/           Android TV / Fire TV (Compose + Media3 ExoPlayer)
installer/     Utilidades de descarga/instalación
scripts/       fetch-vlc.ps1 / fetch-vlc.sh: runtime de vídeo para el empaquetado
config/detekt/ Configuración y línea base del análisis estático
```

Las dos aplicaciones consumen `shared`: el mismo parser, el mismo cliente Xtream,
el mismo repositorio y el mismo estado (`LibraryState`).

## Compilar

Requiere **JDK 17**.

```bash
./gradlew :shared:desktopTest :composeApp:desktopTest   # tests
./gradlew detekt                                        # análisis estático
./gradlew :composeApp:run                               # escritorio en local
./gradlew :app:assembleDebug                            # APK de pruebas
```

Para ejecutar el escritorio en local hace falta libvlc en el sistema
(`sudo apt-get install vlc` en Linux, o VLC instalado en Windows/macOS). El
instalador que produce el CI ya lo lleva dentro.

Las pruebas corren con un heap de 256 MB a propósito (`-PtestHeap=128m` para
apretarlo más): se parece al reparto de memoria de un aparato de televisión, y
así una regresión de memoria sale en las pruebas y no en el salón de alguien.

### Instalador de Windows (MSI / EXE)

`jpackage` **no cruza plataformas**: el MSI solo se construye desde Windows.

- **Por CI:** un push a `master` dispara el job `windows`, que descarga VLC,
  genera MSI y EXE y los publica en un GitHub Release.
- **A mano en Windows:** JDK 17 y [WiX 3.11](https://github.com/wixtoolset/wix3/releases)
  en el `PATH`, y luego:

```powershell
.\scripts\fetch-vlc.ps1
.\gradlew :composeApp:packageMsi
```

El resultado queda en `composeApp\build\compose\binaries\main\msi\`.

Ojo: Windows Installer solo actualiza en sitio si la versión **sube**
(`packageVersion` en `composeApp/build.gradle.kts`). Con el mismo número aborta
con «Another version of this product is already installed».

## Pendiente

- Traducir lo que queda: pantalla de Ajustes, mensajes de error del repositorio y
  del cliente Xtream, y la interfaz web (la infraestructura ya está: ver
  `shared/.../i18n/Textos.kt`)
- Pruebas de interfaz automatizadas
- El análisis estático corre sin resolución de tipos, así que las reglas que
  dependen de los tipos están desactivadas

## Licencia

El código de este proyecto es **MIT** — ver [LICENSE](LICENSE).

El instalador de Windows **no** contiene solo código de este proyecto: incluye el
motor de vídeo **VLC** (libvlc, libvlccore y sus plugins), de VideoLAN, que tiene
sus propias licencias — LGPL v2.1+ y, en algunos plugins, GPL v2+. Los detalles y
lo que eso implica para redistribuirlo están en
[LICENSES-TERCEROS.md](LICENSES-TERCEROS.md).

## Contribuir

Ver [CONTRIBUTING.md](CONTRIBUTING.md). Los avisos de seguridad, en
[SECURITY.md](SECURITY.md).
