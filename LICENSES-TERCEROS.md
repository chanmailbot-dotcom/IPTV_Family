# Software de terceros y licencias

El **código de este proyecto** es MIT (ver [LICENSE](LICENSE)).

Pero el **instalador de Windows no contiene solo este código**: lleva dentro el
motor de vídeo VLC, que es de VideoLAN y tiene sus propias licencias. Este
documento dice qué hay, bajo qué licencia y qué implica.

> Uso personal: **nada de esto te afecta.** La LGPL y la GPL regulan la
> distribución, no el uso. Instalar y usar el programa en tus equipos no impone
> ninguna obligación. Lo de abajo aplica a quien **reparta** el instalador — que
> es lo que hace este repositorio al publicar el `.msi` en una release.

## Qué se distribuye

| Componente | Origen | Licencia |
|---|---|---|
| Código de IPTV Family | este repositorio | MIT |
| `libvlc.dll`, `libvlccore.dll` | [VideoLAN](https://www.videolan.org/vlc/) 3.0.23 | LGPL v2.1 o posterior |
| Plugins de VLC (365 archivos) | VideoLAN 3.0.23 | LGPL v2.1+ en su mayoría, **GPL v2+ en algunos** |
| Runtime de Java (jlink) | OpenJDK / Temurin | GPL v2 con excepción Classpath |
| Compose Multiplatform, Kotlin, Ktor, kotlinx.* | JetBrains y colaboradores | Apache 2.0 |
| Media3 / ExoPlayer, AndroidX (solo el APK) | Google | Apache 2.0 |
| Coil (solo el APK) | Coil contributors | Apache 2.0 |

Los textos completos de las licencias GNU están en [`licencias/`](licencias/).
El instalador incluye además el `COPYING` que VideoLAN distribuye con sus
binarios (lo copian `scripts/fetch-vlc.ps1` y `scripts/fetch-vlc.sh`).

## Los plugins GPL

La distribución oficial de VLC para Windows incluye plugins que provienen de
proyectos con licencia **GPL**, no LGPL. Identificados en la versión empaquetada:

```
codec/libx264_plugin.dll          x264      — codificador H.264 (GPL v2+)
codec/libx26410b_plugin.dll       x264      — idem, 10 bits
codec/libx265_plugin.dll          x265      — codificador HEVC (GPL v2+)
codec/libfaad_plugin.dll          FAAD2     — decodificador AAC (GPL v2)
codec/libsvcdsub_plugin.dll       libcdio   — subtítulos SVCD
video_filter/libpostproc_plugin.dll  libpostproc — postproceso (GPL)
audio_filter/libmad_plugin.dll    libmad    — decodificador MP3 (GPL v2+)
access/libdvdread_plugin.dll      libdvdread  — lectura de DVD (GPL v2)
access/libdvdnav_plugin.dll       libdvdnav   — navegación de DVD (GPL v2)
access/libvcd_plugin.dll          libcdio     — Video CD (GPL)
access/libcdda_plugin.dll         libcdio     — CD de audio (GPL)
```

Ninguno de ellos lo usa esta aplicación: son **codificadores** (aquí solo se
decodifica) y **acceso a discos físicos** (DVD, CD, Video CD). Se distribuyen
porque vienen en el paquete oficial de VLC y se empaqueta tal cual, sin
seleccionar plugins.

## Consecuencia para quien redistribuya el instalador

Como el paquete incluye componentes GPL, **el instalador de Windows como conjunto
se distribuye bajo GPL v2 o posterior**. El código propio sigue siendo MIT, que es
compatible: quien quiera reutilizarlo por separado lo tiene bajo MIT.

En la práctica, esto obliga a cuatro cosas, todas cumplidas aquí:

1. **Incluir el texto de las licencias** → [`licencias/`](licencias/), y el
   `COPYING` de VideoLAN dentro del propio paquete.
2. **Decir qué parte es de quién** → este documento.
3. **Ofrecer el código fuente correspondiente.** Los binarios de VLC se
   distribuyen **sin modificar**, tal como los publica VideoLAN, así que el
   fuente correspondiente es el oficial de la versión 3.0.23:
   <https://download.videolan.org/pub/videolan/vlc/3.0.23/> — y el de esta
   aplicación es este repositorio.
4. **No añadir restricciones propias** encima de esas licencias.

No hay que registrar, notificar ni pedir permiso a nadie: la GPL es una licencia,
no un trámite.

### El APK no está afectado

La aplicación de Android usa **Media3 / ExoPlayer** (Apache 2.0), no VLC. El APK
no contiene código GPL, así que se distribuye bajo la licencia MIT de este
proyecto.

## Si en algún momento se prefiere un paquete solo LGPL

Bastaría con excluir del empaquetado los plugins listados arriba, en
`scripts/fetch-vlc.ps1`. No se pierde ninguna capacidad que la aplicación use:
la decodificación real la hace `libavcodec_plugin.dll` (LGPL). Es una decisión
abierta, no un pendiente: hoy el paquete va completo, como lo publica VideoLAN.

## Marcas

VLC y VideoLAN son marcas de la organización VideoLAN. Este proyecto **no está
asociado a VideoLAN ni respaldado por ella**: se limita a usar y redistribuir
libvlc conforme a su licencia.
