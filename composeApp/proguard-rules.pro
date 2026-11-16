# kotlinx.serialization (navigation routes, backups, deck settings)
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.mnemo.**$$serializer { *; }
-keepclassmembers class app.mnemo.** { *** Companion; }
-dontwarn org.slf4j.**
