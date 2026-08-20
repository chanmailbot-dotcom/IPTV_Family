# IPTV_Family - Plan de Desarrollo

## Objetivo
Clonar funcionalidad de IBO Player — reproductor IPTV premium para Android TV / Teléfonos / Tablets.

## Criterios de éxito
- [ ] Soporte listas M3U (archivo local + URL remota)
- [ ] Soporte Xtream Codes API (URL + user + pass)
- [ ] UI Material 3, multilingüe (español)
- [ ] Categorías: Live TV, VOD, Series
- [ ] EPG (guía electrónica de programas)
- [ ] Favoritos
- [ ] Control parental
- [ ] Reproductor basado en ExoPlayer / Media3
- [ ] Package: `com.iptv.family`

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
- [x] Módulo KMP `shared` compilando para desktop con tests (M3U parseo, xtream, store JSON)
- [ ] Conectar `app` (Android) para consumir `shared`
- [ ] Conectar `composeApp` (escritorio) para consumir `shared` y construir UI de escritorio

## Tecnologías
- **Kotlin 2.0.21**
- **Jetpack Compose** (Material 3)
- **Media3 ExoPlayer** para reproducción
- **Hilt** para DI
- **Room** para persistencia
- **Retrofit + OkHttp** para API
- **Navigation Compose** para navegación
- **Datastore** para preferencias