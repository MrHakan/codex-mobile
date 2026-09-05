# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization keeps generated serializers on the annotated classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.mrhakan.codexmobile.** {
    *** Companion;
}
-keepclasseswithmembers class com.mrhakan.codexmobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Tink (pulled in by androidx.security:security-crypto) references Error Prone
# annotations that are compile-time only.
-dontwarn com.google.errorprone.annotations.**
