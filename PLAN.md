# IPTV_Family - Plan de Desarrollo

## Objetivo
Clonar funcionalidad de IBO Player — reproductor IPTV premium para Android TV / Teléfonos / Tablets.

## Criterios de éxito
Estado por plataforma: **E** = escritorio, **A** = Android/TV.

- [x] Soporte listas M3U (archivo local + URL remota) — E
- [x] Soporte Xtream Codes API (URL + user + pass) — E *(implementado; sin probar contra un panel real)*
- [x] UI Material 3 en español — E *(textos fijos en español; sin i18n real)*
- [x] Categorías: Live TV, VOD, Series — E
- [ ] EPG (guía electrónica de programas) — `XmltvParser` existe en `shared` pero nada lo usa; falta un campo de URL XMLTV en `Playlist`
- [x] Favoritos — E
- [x] Control parental (PIN por categoría) — E
- [x] Reproductor de vídeo embebido — E, con VLCJ/libvlc *(ExoPlayer/Media3 son Android-only; se usan en la fase A)*
- [x] Package: `com.iptv.family`

## Roadmap

### Fase 1: Foundation (COMPLETADA)
- ✅ Repo GitHub creado
- ✅ Projecto Android base (Kotlin + Compose Material 3)
- ✅ Hilt DI configurado
- ✅ Theme.kt creado
- ✅ CI/CD workflow básico

### Fase 2: Data Layer (EN PROGRESO)
- [ ] M3U Parser - parsear archivos M3U/Xtream 19.1.2
- [ ] Xtream Codes API Client - login, getLiveStreams, getVodStreams, getSeries
- [ ] Room entities: Playlist, Channel, Category, EPG, Favorite
- [ ] DAO interfaces y Database

### Fase 3: Player Core
- [ ] ExoPlayer/Media3 wrapper con cache
- [ ] Gestión de reproducción (play/pause, seek)
- [ ] Subtítulos y audio tracks

### Fase 4: UI Layer
- [ ] MainActivity con navegación (Navigation Compose)
- [ ] Pantalla: Lista de playlists
- [ ] Pantalla: Navegación de canales (Live TV, VOD, Series)
- [ ] Pantalla: Reproductor fullscreen
- [ ] Pantalla: Ajustes (Settings)
- [ ] Pantalla: Control parental (PIN)
- [ ] Pantalla: Favoritos

### Fase 5: CI/CD & Release
- [ ] Build APK exitoso en GitHub Actions
- [ ] GitHub Release con APK descargable

## Arquitectura

```
shared/                     # <-- Módulo KMP compartido (NUEVO)
├── src/commonMain/
│   ├── model/              # Models: Channel, Playlist, Category, UserSettings, ...
│   ├── data/
│   │   ├── m3u/            # M3UParser multiplataforma
│   │   ├── xtream/         # XtreamApiClient (player_api.php)
│   │   ├── store/          # KeyValueStore/FileKeyValueStore
│   │   └── repository/     # LibraryRepository (orquestador)
└── build.gradle.kts        # Kotlin Multiplatform (destino: desktop y android)

app/                        # Android (Compose) - consume shared
composeApp/                 # Escritorio Windows/Linux/macOS - consume shared
installer/                  # Instaladores / scripts de descarga

## Estado actual (20/08/2026)

### Escritorio (`composeApp`) — funcionando
- [x] Compila y pasa tests; `createDistributable` genera la app nativa
- [x] Reproducción **embebida** con VLCJ: vídeo dentro de la app, verificado de extremo a
      extremo (decodificación 1280x720 + render en pantalla)
- [x] UI rehecha: barra lateral, tarjetas de lista, buscador, filtros Todo/TV/Películas/Series,
      barra lateral de categorías con contadores, lista de zapeo junto al vídeo,
      barra de controles (play/pausa, canal ant./sig., stop, volumen, pantalla completa)
- [x] Logos de canal descargados y cacheados; iniciales de color cuando la lista no trae logo
- [x] Reabre al arrancar la última lista usada
- [x] Atajos: `Espacio` pausa, `F` pantalla completa, `Esc` sale de pantalla completa
- [x] Ajustes que hacen algo de verdad: tema, buffer de red, aceleración por hardware,
      modo compatibilidad de vídeo, control parental
- [x] Icono propio para instalador y accesos directos

### Empaquetado
- [x] `packageMsi` / `packageExe` / `packageDeb` configurados, con `upgradeUuid` e instalación
      por usuario (sin permisos de administrador)
- [x] `scripts/fetch-vlc.ps1` mete libvlc dentro del instalador: el usuario no instala VLC
- [x] Workflow de CI rehecho: job real en `windows-latest` que construye MSI + EXE y publica Release
- [ ] **Falta ejecutar el workflow**: el MSI no se puede construir en Linux (jpackage no
      cross-compila), hay que hacer push a `master` y descargar el Release

### Pendiente
- [ ] EPG (ver criterios de éxito)
- [ ] Reactivar `:app` (Android) sobre `shared` + Compose compiler → Fire TV Stick y Android TV
- [ ] Detekt está declarado pero no aplicado en ningún módulo; `detekt-baseline.xml` está vacío
- [ ] Sin probar contra un panel Xtream real ni contra una lista M3U grande (miles de canales)

### Bugs corregidos en esta tanda
- `M3UParser` generaba ids de canal duplicados (`tvg-id` se repite en listas reales) →
  `LazyColumn(key=)` reventaba. Ahora son únicos, con test de regresión.
- `M3UParser` ponía `Category.id = name.hashCode()` mientras `Channel.group = name`, así que
  el filtro por categoría nunca casaba. Ahora coinciden, con test de regresión.
- Se llamaba a `play()` antes de que la superficie AWT existiera → libvlc lanzaba
  «video surface component must be displayable» en un diálogo modal. El stream lo arranca
  ahora `PlayerScreen` cuando la superficie está lista.
- Los mensajes de error y el indicador de carga se dibujaban **encima** del vídeo, donde el
  componente AWT pesado los tapa: eran invisibles. Ahora van fuera del área de vídeo.

## Tecnologías
- **Kotlin 2.0.21**
- **Jetpack Compose** (Material 3)
- **Media3 ExoPlayer** para reproducción
- **Hilt** para DI
- **Room** para persistencia
- **Retrofit + OkHttp** para API
- **Navigation Compose** para navegación
- **Datastore** para preferencias