# Reglas de R8 para la version de release.
#
# Este fichero conservaba reglas de Hilt, Retrofit, Gson y Room: dependencias que
# el modulo YA NO USA desde que la aplicacion pasa por el modulo compartido.
# Reglas para clases que no existen no protegen nada y hacen creer que hay una
# proteccion donde no la hay.
#
# Comprobado en el emulador con el APK de release firmado: se añade una lista, se
# cierra la aplicacion del todo y al volver a abrirla la lista sigue ahi con sus
# canales. Es decir, la serializacion sobrevive a la ofuscacion.

# --- kotlinx.serialization ---
# El propio artefacto trae sus reglas, pero se declaran aqui de forma explicita
# para las clases de ESTE proyecto: si algun dia se ofuscan sus serializadores
# generados, la aplicacion arranca y luego no puede leer sus propios datos, que
# es el peor de los fallos posibles -- silencioso y con perdida.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.iptv.family.**$$serializer { *; }
-keepclassmembers class com.iptv.family.** {
    *** Companion;
}
-keepclasseswithmembers class com.iptv.family.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Media3 / ExoPlayer ---
# Carga por reflexion los decodificadores y las extensiones segun el formato.
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Coroutines ---
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- Coil (logos de canal) ---
-dontwarn coil.**

# --- Parcelable ---
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
