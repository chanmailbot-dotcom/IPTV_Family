# IPTV_Family - Plan de Desarrollo

## Objetivo
Reproductor IPTV personal para PC (Windows/Linux/macOS), Android/Fire TV, y
control remoto por navegador — módulo de dominio compartido (Kotlin
Multiplatform) entre todas las plataformas.

## Criterios de éxito
Estado por plataforma: **E** = escritorio, **A** = Android/Fire TV, **W** = servidor web remoto.

- [x] Soporte listas M3U (archivo local + URL remota) — E, A
- [x] Soporte Xtream Codes API (URL + user + pass) — E, A *(probado contra paneles reales del usuario)*
- [x] UI Material 3 en español — E, A *(textos fijos en español; sin i18n real)*
- [x] Categorías: Live TV, VOD, Series — E, A
- [x] EPG (guía electrónica de programas) — E *(XMLTV por URL de lista o fallback
      automático `xmltv.php` de Xtream; mapeo `epg_channel_id`; "Ahora/Luego" en
      el reproductor, listas y web remota. Android pendiente)*
- [x] Favoritos — E, A
- [x] Control parental (PIN por categoría) — E *(modelo también existe para A, sin UI de ajustes todavía)*
- [x] Reproductor de vídeo embebido — E (VLCJ/libvlc), A (Media3 ExoPlayer)
- [x] Control remoto por navegador (ver/cambiar canal desde cualquier dispositivo) — W
- [x] Package: `com.iptv.family`

## Roadmap

### Fase 1: Foundation — COMPLETADA
### Fase 2: Módulo compartido (`shared`) — COMPLETADA
M3U/Xtream parser, `LibraryRepository`, modelos, `AppLog` (logging de
archivo), targets `jvm("desktop")` + `androidTarget()`.

### Fase 3: Escritorio (`composeApp`) — COMPLETADA
Reproductor VLCJ embebido, UI completa, ajustes reales, empaquetado
MSI/EXE/DEB, icono propio.

### Fase 4: Servidor web de control remoto — COMPLETADA (MVP)
Servidor Ktor embebido en el escritorio: ver/cambiar canal desde el
navegador de cualquier dispositivo de la red o de internet (vía ngrok).
Multiplexor de sesion unica IMPLEMENTADO Y VERIFICADO contra el panel real: VLC y los navegadores comparten la sesion upstream del proxy (cache de manifiestos TTL 2 s, cache de segmentos inmutables, mutex de peticion, host/token rotatorio resuelto en cada refresh). Verificado: manifiesto 200, segmento 200 x2 (2a desde cache), reproduccion activa y cero 513/407/timeout en el log.

### Fase 5: Android / Fire TV (`app`) — COMPLETADA (MVP), pendiente de pulir
Reconectado al `shared` (antes tenía código 100% duplicado y estaba fuera
del build). UI Compose para mando a distancia, reproductor Media3
ExoPlayer. Pendiente: EPG, ajustes de control parental, firmar/publicar
el APK (hoy es un debug APK de pruebas).

### Fase 6: CI/CD & Release
- [x] Workflow de CI construye MSI + EXE en `windows-latest` y publica Release
- [ ] Build de Android en GitHub Actions (hoy el APK se genera y sube a mano)
- [ ] APK firmado para release, no solo debug

## Arquitectura

```
shared/                     # Módulo KMP compartido
├── src/commonMain/
│   ├── model/              # Channel, Playlist, Category, UserSettings, ...
│   ├── log/                # AppLog: logging de archivo plano
│   ├── data/
│   │   ├── m3u/            # M3UParser multiplataforma
│   │   ├── xtream/         # XtreamApiClient (player_api.php)
│   │   ├── store/          # KeyValueStore/FileKeyValueStore
│   │   └── repository/     # LibraryRepository (orquestador)
│   └── androidTarget() + jvm("desktop")

app/                        # Android/Fire TV (Compose) — consume shared
composeApp/                 # Escritorio Windows/Linux/macOS — consume shared
│   └── remote/             # Servidor Ktor de control remoto (solo desktop)
installer/                  # Instaladores / scripts de descarga
```

## Estado actual (24/08/2026)

### Escritorio (`composeApp`) — funcionando
- [x] Reproducción embebida con VLCJ, verificada de extremo a extremo
- [x] UI completa: listas, categorías, zapeo, favoritos, ajustes reales
- [x] Icono y paleta de marca propios (degradado índigo → verde azulado)
- [x] Servidor web de control remoto (Ktor/CIO): token de acceso, SSE con
      latido para no dejar conexiones muertas, proxy de stream, enlaces con
      auto-login
- [x] Empaquetado MSI/EXE/DEB funcionando (WiX lo trae el propio plugin)

### Android / Fire TV (`app`) — reconectado esta sesión
- [x] `shared` con `androidTarget()`; `app` consume el mismo núcleo que
      escritorio (antes tenía Room/Retrofit/Hilt duplicados y ni compilaba)
- [x] UI Compose: Mis listas, TV en vivo, Películas, Series, Favoritos, Ajustes
- [x] Reproductor Media3 ExoPlayer
- [x] UX de mando: foco visible (ámbar), zapeo instantáneo arriba/abajo,
      tira de canales izquierda/derecha, previsualización en vivo al mover
      el foco, foco que se restaura al volver de pantalla completa
- [x] Distribución de prueba: Release fija de GitHub + código Downloader
      (ver memoria del proyecto para el tag y el flujo exacto)

### Bugs corregidos en esta tanda
- Xtream: películas se pedían en `/vod/...` en vez de `/movie/...` → 404 /
  `ERROR_CODE_IO_BAD_HTTP_STATUS`. Ahora usa la extensión real del archivo.
- Xtream: series solo daban el show agregado (no reproducible); ahora se
  piden los episodios reales vía `get_series_info`.
- `LibraryRepository.fetch()` no seguía redirecciones → una lista M3U
  redirigida se colaba como "0 canales" sin ningún error visible.
- URLs de M3U/Xtream sin esquema (`dominio.com:8080`) lanzaban
  `MalformedURLException`; ahora se normalizan a `http://`.
- Android: `usesCleartextTraffic` — bloqueaba cualquier panel `http://` (casi
  todos) por defecto desde API 28.

### Mejoras UX/UI (sesiones 25–27/08/2026)
- [x] Escritorio: tema Sistema/Claro/Oscuro real (selector segmentado), textos
      centralizados en `AppStrings`, anillo de foco por teclado y
      `contentDescription` en la navegación
- [x] Web remota: rediseño profesional completo — layout de dos columnas,
      overlays de vídeo (LIVE/buffer/error/big-play), `hls.js` servido local
      (sin CDN; antes si el CDN fallaba no había vídeo), token embebido en los
      segmentos del proxy (antes respondían 401 → pantalla negra), render por
      lotes con scroll infinito para 40k+ canales, chips de categorías,
      favoritos, atajos de teclado y tema Sistema/Claro/Oscuro
- [x] Web remota: control total del reproductor desde el navegador (zapeo,
      pausa, stop, silencio, volumen) sincronizado vía SSE
- [x] Escritorio: caché de logos en disco (`~/.iptv-family/logos`): LRU en
      memoria (400) + persistente entre sesiones, con recorte por tamaño
      (tope 256 MB, se eliminan los más viejos)
- [x] Escritorio: atajos de teclado del reproductor — Espacio (play/pausa),
      F (pantalla completa), Esc (salir), M (silencio), ↑/↓ (volumen ±5),
      N/P (canal siguiente/anterior sobre la lista de zapeo)
- [x] Escritorio + web: **EPG** — `Playlist.epgUrl` (campo opcional en el alta),
      fallback automático `xmltv.php` en Xtream, parser XMLTV en `shared`
      (blindaje XXE que admite DOCTYPE, requisito de guías reales), carga en
      `AppState` con caché O(1) por canal + `epgTick` de refresco cada 30 s;
      "Ahora/Luego" en el reproductor, "Ahora:" en las filas de canales y en la
      tarjeta "En vivo" de la web (con hora de fin). Xtream: `epg_channel_id`
      del panel como id de guía (caída a stream_id). Verificado contra la guía
      real del panel: 233k programas, "TV: LA 1" → `la1.es` → programa actual

- [x] Web remota: **arreglado el acceso** — dos hallazgos: (1) la app de
      escritorio llegó a morir por un crash nativo de render (Skiko/Direct3D,
      EXCEPTION_ACCESS_VIOLATION) y eso tumbaba el servidor web entero: el
      "no se puede entrar" era simplemente que no había servidor; (2) de paso,
      dos bugs reales del flujo: el auto-login por enlace `?token=` no existía
      en `boot()` (ahora prueba el token de la URL, lo guarda y limpia la
      dirección) y la ruta `/` perdía el query al redirigir a `/index.html`
      (ahora lo conserva). `/login` parsea el body a mano (receiveText + Json)
      en vez de depender del plugin ContentNegotiation. Verificado con
      navegación real: login 200 + cookie, `/api/state` con cookie 200,
      token malo 401, redirect conservando `?token=`. Nota: regenerar el token
      en Ajustes invalida enlaces/cookies antiguos
- [x] Web remota: **"Sintonizando… infinito"** aclarado — el flujo proxy sí sirve
      vídeo (verificado: canal real → segmento 8,4 MB); el loop ocurría con
      canales que no emiten (p. ej. "##### PRIME…" o "DIRECTO…", que devuelven
      HTTP 513 sin segmentos) mientras hls.js reintentaba con solo el spinner.
      Ahora `startStream()` marca media real (`timeupdate`/`playing`) y si a los
      12 s no llegan fotogramas muestra "El canal no está emitiendo…" en vez de
      quedarse cargando. Nota UX: usar canales de TV reales, no los de promo.

### Pendiente
- [x] EPG en Android — el núcleo ya vivía en `shared` (`CommonEpgCache`, `XmltvParser`);
      portado el bloque completo al `AppState` de Android (`epgUrlFor`,
      `loadEpg`, `epgTick`, `currentProgram`/`nextProgram`, `addM3uUrl`/`addXtream`
      con `epgUrl` opcional), disparo en `MainActivity` al cambiar de lista +
      refresco periódico cada 30 s, y las filas de "TV en vivo" muestran
      "Ahora: <programa>" en azul. Compilado `:app:compileDebugKotlin` OK.
      Nota: `app` sí está en `settings.gradle.kts` (include ":app"), en el target
      Android la cachea el mismo `CommonEpgCache`.
- [ ] Firmar el APK / plan de release real para Android (hoy es debug)
- [x] Web remota: **multiplexor de conexión única** — `StreamProxy` cachea el
      manifest corto (TTL 8 s) y libvlc reproduce vía loopback
      (`/stream/origin.m3u8?src=…`) cuando el servidor web está activo, de
      modo que la app local y el navegador comparten LA MISMA sesión del
      proveedor (una sola conexión: paneles que responden 513 a la segunda ya
      no rechazan al navegador). `VlcController.sourceUrl` conserva la URL real
      para el estado remoto/EPG. Además: el proxy ahora **propaga** el estado
      real del upstream (513/404 → mismo código, antes lo enmascaraba como
      200-vacío que dejaba el "Sintonizando…" colgado), mete CORS
      (`Access-Control-Allow-Origin:*`) en `/stream/*` y convierte el "panel
      devolvió HTML vacío" en 502 para que el reproductor falle rápido con
      "canal caído".
- [ ] Detekt declarado pero no aplicado en ningún módulo
- [ ] Build de Android en CI (GitHub Actions)

## Tecnologías
- **Kotlin 2.0.21**, Kotlin Multiplatform (`shared`)
- **Jetpack Compose** (Material 3) en escritorio y Android
- **VLCJ/libvlc** (escritorio) y **Media3 ExoPlayer** (Android) para reproducción
- **Ktor** (servidor + cliente) para el control remoto por navegador
- **kotlinx.serialization** para modelos y persistencia en disco (sin Room/Retrofit/Hilt)
- **Coil** para carátulas/logos en Android
