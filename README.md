# IPTV Family

**Un reproductor IPTV moderno para Android inspirado en IBO Player**

[![CI/CD](https://github.com/chanmailbot-dotcom/IPTV_Family/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/chanmailbot-dotcom/IPTV_Family/actions/workflows/ci-cd.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/compose-1.7.0-green.svg)](https://developer.android.com/jetpack/compose)

## 📺 Características

- **Soporte M3U**: Carga listas M3U desde URL remota o archivo local
- **Xtream Codes API**: Conexión completa con panel Xtream (URL + usuario + contraseña)
- **Reproductor basado en ExoPlayer (Media3)**: Reproducción fluida de HLS, DASH, MPEG-DASH
- **Interfaz moderna**: Jetpack Compose + Material 3, diseño inspirado en IBO Player
- **Categorías**: Live TV, VOD, Series organizados por grupos
- **EPG**: Guía electrónica de programas (cuando el proveedor la ofrece)
- **Favoritos**: Marca y accede rápido a tus canales preferidos
- **Control parental**: PIN para proteger contenido adulto
- **Búsqueda**: Busca canales por nombre en tiempo real
- **Configuración**: Tema, idioma, buffer, sincronización en la nube
- **Android TV / Phone / Tablet**: UI adaptativa para todos los factores de forma

## 🏗️ Arquitectura

```
IPTV Family
├── app/
│   ├── src/main/java/com/iptv/family/
│   │   ├── data/
│   │   │   ├── local/           # Room Database (Entity, DAO, Database)
│   │   │   ├── m3u/             # M3U Parser
│   │   │   ├── xtream/          # Xtream Codes API Client
│   │   │   └── repository/      # Repository Pattern
│   │   ├── di/                  # Hilt Modules
│   │   ├── domain/
│   │   │   └── model/           # Domain Models (Channel, Category, Playlist, etc.)
│   │   ├── player/              # ExoPlayer Wrapper + MediaSessionService
│   │   └── ui/
│   │       └── main/            # Compose Screens (Home, Player, Search, Settings, AddPlaylist)
│   └── src/test/                # Unit Tests
├── .github/workflows/           # CI/CD Pipeline
├── detekt.yml                   # Kotlin Linter Config
├── build.gradle.kts             # Project Build
└── settings.gradle.kts          # Project Settings
```

### Principios
- **Clean Architecture**: Separación clara de capas (Data, Domain, Presentation)
- **MVVM + Repository**: ViewModels con StateFlow, Repository para acceso a datos
- **Hilt DI**: Inyección de dependencias en toda la app
- **Room + Coroutines**: Persistencia reactiva con Flow
- **Jetpack Compose**: UI declarativa y reactiva

## 🚀 Comenzando

### Requisitos
- Android Studio Koala (2024.1.1) o superior
- JDK 17
- Android SDK 34
- Dispositivo/Emulador Android 5.0+ (API 21+)

### Clonar y compilar
```bash
git clone https://github.com/chanmailbot-dotcom/IPTV_Family.git
cd IPTV_Family
./gradlew assembleDebug
```

### Ejecutar tests
```bash
./gradlew testDebugUnitTest
```

### Linting (Detekt)
```bash
./gradlew detekt
```

## 📱 Uso

### Añadir lista M3U (URL)
1. Abre la app → Botón flotante "+" 
2. Selecciona "M3U URL"
3. Introduce nombre y URL de la lista
4. Pulsa "Guardar"

### Añadir lista Xtream Codes
1. Abre la app → Botón flotante "+"
2. Selecciona "Xtream Codes"
3. Introduce: Nombre, URL del panel, Usuario, Contraseña
4. Pulsa "Guardar" (se validará la conexión)

### Añadir archivo M3U local
1. Selecciona "Archivo M3U"
2. Elige el archivo `.m3u` o `.m3u8` de tu almacenamiento

## 🧪 Testing

```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requiere dispositivo/emulador)
./gradlew connectedAndroidTest

# Generar reporte de cobertura
./gradlew jacocoTestReport
```

## 📦 Build Release

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requiere keystore configurado)
./gradlew assembleRelease
```

## 🔧 Configuración de desarrollo

### Variables de entorno para CI/CD
En GitHub Secrets configurar:
- `KEYSTORE_BASE64` - Keystore en base64 para firmar releases
- `KEYSTORE_PASSWORD` - Password del keystore
- `KEY_ALIAS` - Alias de la clave
- `KEY_PASSWORD` - Password de la clave

### Detekt Baseline
```bash
./gradlew detektBaseline
```

## 📄 Licencia

MIT License - ver [LICENSE](LICENSE) para detalles.

## 🙏 Agradecimientos

- [IBO Player](https://iboplayer.com) por la inspiración de UI/UX
- [ExoPlayer/Media3](https://exoplayer.dev) por el motor de reproducción
- [Jetpack Compose](https://developer.android.com/jetpack/compose) por el toolkit de UI
- [Hilt](https://dagger.dev/hilt) por la inyección de dependencias
- [Room](https://developer.android.com/jetpack/androidx/releases/room) por la persistencia

---

**Nota**: Esta app NO incluye contenido IPTV. El usuario debe proporcionar sus propias listas M3U o credenciales Xtream Codes de su proveedor de servicios IPTV.