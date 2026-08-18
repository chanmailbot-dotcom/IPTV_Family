# IPTV Family - Plan de Desarrollo

## Visión
Clone funcional y estética de IBO Player para Android: un reproductor IPTV premium que soporta listas M3U y Xtream Codes (URL + usuario/contraseña), con UI simple, categorías, EPG, favoritos, control parental y reproductor de alta calidad.

## Características Clave (basadas en IBO Player)
| Categoría | Características |
|---|---|
| **Fuentes de contenido** | - Archivo M3U local<br>- URL M3U remoto<br>- Xtream Codes API (URL panel + usuario + password) |
| **Navegación** | - Categorías/grupos de canales (Live TV, VOD, Series)<br>- Búsqueda de canales<br>- Favoritos<br>- Historial |
| **Reproducción** | - ExoPlayer basado<br>- Reproducción de alta calidad (HD/Full HD/4K)<br>- Modo full-screen<br>- Subtítulos (si están disponibles) |
| **EPG** | - Guía electrónica de programas (si el proveedor lo ofrece)<br>- Soporte EPG overlay |
| **Configuración** | - Multilingüe<br>- Control parental<br>- Ajustes de reproductor<br>- Gestión de playlists |
| **Dispositivos** | Android TV, Phone, Tablet |

## Arquitectura Técnica
- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Reproductor:** Media3 ExoPlayer
- **Arquitectura:** MVVM + Repository + Clean Architecture
- **DI:** Hilt
- **DB:** Room (playlists, favoritos, EPG cache)
- **Red:** Retrofit + OkHttp
- **Min SDK:** 21 (Android 5.0)
- **Target SDK:** 34
- **Package:** com.iptv.family

## Estructura del Proyecto
```
app/
├── src/main/
│   ├── java/com/iptv/family/
│   │   ├── di/           # Hilt modules
│   │   ├── ui/           # Composable screens
│   │   │   ├── home/     # Main screen, category browser
│   │   │   ├── channels/ # Channel list, search
│   │   │   ├── player/   # Player screen
│   │   │   ├── settings/ # Settings screens
│   │   │   ├── epg/      # EPG views
│   │   │   └── favorites/ # Favoritos
│   │   ├── data/
│   │   │   ├── m3u/       # M3U parser
│   │   │   ├── xtream/    # Xtream API client
│   │   │   ├── epg/       # EPG parsing
│   │   │   ├── local/     # Room DB
│   │   │   └── repository/
│   │   ├── domain/        # Modelos de dominio
│   │   ├── player/        # ExoPlayer wrapper
│   │   └── util/          # Helpers
│   ├── res/
│   │   ├── values/        # Strings, themes, colors
│   │   ├── drawable/      # Icons, imágenes
│   │   └── xml/           # Preference screens
│   └── AndroidManifest.xml
```

## Referencias
- [IBO Player](https://iboplayer.com) - App original
- [IPTVNator](https://github.com/4gray/iptvnator) - Features reference
- [OwnTV](https://github.com/ahXN00/OwnTV) - Android Kotlin reference
- [StreamVault-IPTV](https://github.com/Davidona/StreamVault-IPTV) - Android TV Kotlin/Compose reference

## Tareas (por prioridad)
1. ✅ Investigar IBO Player
2. ✅ Crear memoria del proyecto
3. ✅ Crear PLAN.md
4. Configurar repositorio GitHub
5. Setup proyecto Android inicial
6. Implementar parsing M3U
7. Implementar cliente Xtream API
8. Implementar ExoPlayer wrapper
9. UI: Pantalla principal con categorías
10. UI: Lista de canales
11. UI: Pantalla de reproductor
12. UI: Configuración y gestión de playlists
13. Persistencia con Room (favoritos, historial)
14. Tests unitarios
15. Build app bundle (AAB)
16. Subir a GitHub + configurar CI/CD