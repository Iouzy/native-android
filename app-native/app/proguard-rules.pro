# Keep kotlinx.serialization generated serializers for the backup model so the
# pauta.v4 import/export keeps working under R8. // PT: preservar serializadores
# do backup para o import/export pauta.v4 não partir com R8.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.pauta.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
