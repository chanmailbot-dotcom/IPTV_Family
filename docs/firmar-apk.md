# Firmar el APK de Android / Fire TV

El APK que se instalaba hasta ahora en el Fire TV era el de **depuración**. Sirve
para probar, pero no para repartir:

- va firmado con la clave de desarrollo del SDK, que tiene cualquiera;
- no pasa por R8, así que es más grande y más lento;
- y, sobre todo, Android identifica una aplicación por su firma: si mañana se
  reparte un APK firmado con otra clave, el sistema lo trata como una aplicación
  **distinta** y obliga a desinstalar, con pérdida de listas y favoritos.

De ahí que la clave se genere **una vez** y se guarde bien. Si se pierde, no hay
forma de actualizar las instalaciones existentes.

## 1. Generar la clave (una sola vez)

```bash
keytool -genkeypair -v \
  -keystore iptv-family.jks \
  -alias iptv-family \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=IPTV Family, O=IPTV Family, C=ES"
```

`-validity 10000` son unos 27 años: una clave que caduca deja de poder firmar
actualizaciones, y ahí no hay arreglo posible.

Guarda el `.jks` y sus contraseñas donde no se pierdan **y no en el
repositorio**: `*.jks` y `keystore.properties` están en `.gitignore`.

## 2. Firmar en local

Crea `keystore.properties` en la raíz del proyecto:

```properties
storeFile=C:/ruta/a/iptv-family.jks
storePassword=...
keyAlias=iptv-family
keyPassword=...
```

Y construye:

```bash
./gradlew :app:assembleRelease
```

El APK sale en `app/build/outputs/apk/release/`. Para comprobar con qué clave ha
quedado firmado:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Sin `keystore.properties` ni variables de entorno, la firma de release
simplemente no se define: `assembleDebug` sigue funcionando con normalidad, de
modo que quien clone el proyecto puede compilarlo sin tener ninguna clave.

## 3. Firmar en el CI

El workflow busca cuatro secretos en el repositorio de GitHub
(*Settings → Secrets and variables → Actions*):

| Secreto | Contenido |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | el `.jks` completo, en base64 |
| `ANDROID_KEYSTORE_PASSWORD` | contraseña del almacén |
| `ANDROID_KEY_ALIAS` | alias de la clave (`iptv-family`) |
| `ANDROID_KEY_PASSWORD` | contraseña de la clave |

Para el primero:

```bash
base64 -w 0 iptv-family.jks > iptv-family.jks.base64
```

(en Windows: `certutil -encode iptv-family.jks salida.txt` y quitar las líneas
de cabecera y pie).

Si los secretos **no** están puestos, el CI no falla: construye el APK de
depuración, lo sube marcado como tal y deja un aviso en el resumen de la
ejecución. Es a propósito — un repositorio recién clonado tiene que poder
construirse.

Cuando sí están, el CI además ejecuta `apksigner verify --print-certs` sobre el
APK resultante, para que quede por escrito con qué certificado se firmó.

## Comprobado

El mecanismo se probó de extremo a extremo con una clave desechable: el APK de
release salió firmado con ella (`apksigner` lo confirma), se instaló en un
emulador de Android TV, se añadió una lista, se cerró la aplicación del todo y al
volver a abrirla la lista seguía ahí con sus canales. Eso último importa más de
lo que parece: es lo que demuestra que R8 no rompe la serialización de los datos
guardados, el fallo típico de una primera versión de release.
