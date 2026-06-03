# Keep NetMonster Core models
-keep class cz.mroczis.netmonster.core.model.** { *; }
-keep class cz.mroczis.netmonster.core.db.model.** { *; }

# Keep Room entities
-keep class com.cellrecorder.app.data.local.entity.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.cellrecorder.app.**$$serializer { *; }
-keepclassmembers class com.cellrecorder.app.** { *** Companion; }
-keepclasseswithmembers class com.cellrecorder.app.** { kotlinx.serialization.KSerializer serializer(...); }