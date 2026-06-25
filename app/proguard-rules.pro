-keep class com.toolnagy.ringtonemanager.data.model.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

-keepattributes Signature
-keepattributes *Annotation*

-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
