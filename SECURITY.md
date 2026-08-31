# Seguridad

## Cómo avisar de un fallo

Usa el aviso privado de GitHub: **Security → Report a vulnerability**, en este
repositorio. Así el aviso no es público mientras se arregla.

Si prefieres no usar GitHub, abre una incidencia **sin detalles** pidiendo un
contacto, y se sigue por privado.

**No incluyas nunca** en el aviso tu lista de canales, la URL de tu proveedor ni
tus credenciales. Para reproducir un fallo basta con describir el tipo de lista
(M3U o Xtream) y el comportamiento.

## Qué está expuesto y qué no

Lo que más superficie tiene es el **servidor web de la versión de escritorio**,
que está apagado por defecto y solo se enciende desde Ajustes:

- Sirve por HTTPS con un certificado autofirmado que se genera en el equipo.
- Pide usuario y contraseña; la contraseña se guarda derivada con sal (PBKDF2),
  no en claro.
- Los intentos fallidos se van frenando (espera creciente por IP y por usuario)
  para que no se pueda probar contraseñas a ritmo de máquina.
- Comprueba el origen de las peticiones que cambian algo, y manda cabeceras que
  limitan lo que la página puede cargar.

Aun así, **abrirlo a internet es una decisión con consecuencias**: si lo publicas
por un túnel o abriendo un puerto del router, cualquiera que llegue a esa
dirección puede intentar entrar. Con contraseña larga y el freno de intentos el
riesgo es razonable, pero no es cero.

El resto de la aplicación no escucha en ningún puerto.

## Credenciales del proveedor

- En Windows se guardan **cifradas** con DPAPI, ligadas a la cuenta de usuario
  del sistema. En otras plataformas todavía se guardan en claro y el programa lo
  advierte en el log al arrancar.
- Las direcciones de los canales de Xtream llevan dentro el usuario y la
  contraseña. La copia local del catálogo (la que permite seguir viendo la lista
  sin conexión) las sustituye por un marcador antes de escribirla, precisamente
  para no repartir la contraseña por el disco.
- El log **oculta** las credenciales de las URLs que registra. Si vas a compartir
  un log, revísalo de todas formas.

## Qué NO es un fallo de seguridad aquí

- Que el certificado del servidor web sea autofirmado y el navegador avise: es
  intencionado, es un servidor doméstico sin dominio.
- Que un proveedor de IPTV responda cosas raras o corte la conexión. Pasa, y la
  aplicación está hecha para aguantarlo, pero no es un fallo del programa.
- El contenido al que apunte una lista. Esta aplicación no distribuye ni aloja
  contenido; lo que cada uno reproduzca es su responsabilidad.

## Versiones

Se arregla sobre la última versión publicada. No hay ramas de mantenimiento.
