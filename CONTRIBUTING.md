# Contribuir a IPTV Family

Gracias por el interés. El proyecto nació para uso doméstico y se ha ido
puliendo hasta poder abrirlo; eso significa que hay decisiones tomadas para una
casa concreta y que se pueden discutir.

## Antes de nada

**No se aceptan listas de canales, ni enlaces a proveedores, ni credenciales**,
ni en el código, ni en las incidencias, ni en los ejemplos. Esto es un
reproductor: el contenido lo pone cada uno. Una incidencia con una lista dentro
se cierra y se edita para quitarla.

## Compilar

Requiere **JDK 17**. No hace falta Android Studio.

```bash
./gradlew :shared:desktopTest :composeApp:desktopTest   # tests
./gradlew detekt                                        # análisis estático
./gradlew :composeApp:run                               # escritorio
./gradlew :app:assembleDebug                            # APK de pruebas
```

Para el escritorio en local hace falta libvlc instalado en el sistema. No se
necesita ninguna clave de firma para compilar: si no hay
`keystore.properties`, la firma de release simplemente no se define.

## Qué se espera de un cambio

**Que esté probado de verdad, no razonado.** Es la única regla que importa aquí.
En este proyecto casi todos los fallos serios aparecieron al ejecutar algo, no al
leer el código: la guía EPG que se moría por memoria, el `!!` que el análisis
estático no ve, las escrituras simultáneas que se pisaban, la guía que en Android
no cargaba nunca. Si un cambio se puede comprobar, compruébalo y cuenta cómo.

- **Pruebas** para lógica en `shared`. Y antes de fiarte de una prueba nueva,
  comprueba que **falla** con el código anterior: una prueba que pasa siempre no
  prueba nada.
- **Interfaz**: dilo si lo has visto funcionando, y en qué (emulador de Android
  TV, ventana de escritorio a tal ancho, navegador del móvil).
- Si algo no lo has podido probar, **dilo en el propio cambio**. Es infinitamente
  mejor que dejarlo implícito.

## Estilo

- El código, los comentarios y los mensajes de commit van **en castellano**.
  Los identificadores también (`añadirLista`), y el análisis estático está
  configurado para admitirlo.
- Los comentarios explican **por qué**, no qué. Si un comentario se puede deducir
  leyendo la línea de abajo, sobra. Si explica una trampa, un fallo que hubo o
  una decisión que parece rara, es oro.
- Los textos de la interfaz no se escriben sueltos: van en
  `shared/.../i18n/Textos.kt`, que es una interfaz, así que el compilador obliga
  a traducirlos también al inglés.
- `./gradlew detekt` tiene que pasar. Hay una línea base con la deuda existente:
  se puede tocar para quitar entradas, no para añadirlas.

## Mensajes de commit

Formato `tipo(ámbito): qué cambia`, en minúscula y sin punto final. Y en el
cuerpo, lo que de verdad ayuda al que venga después:

- **qué pasaba antes** (el síntoma, no la clase que has tocado);
- **por qué** se hizo así y qué alternativa se descartó;
- **cómo se comprobó**, con los números o el mensaje concretos.

Un ejemplo real del historial: «la guía se lee en streaming, no cargando la guía
entera» explica que con 150 MB de guía y 128 MB de heap el enfoque anterior
terminaba en `OutOfMemoryError`, medido. Eso es lo que se busca.

## Estructura

`shared/` es el núcleo compartido: si algo vale para las dos plataformas, va
ahí. Hubo una época en la que el estado de la aplicación estaba duplicado en
escritorio y en Android, y cada arreglo había que escribirlo dos veces; no se
vuelve a eso.
