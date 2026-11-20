-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class com.personalkanban.app.domain.model.** { *; }
-keepclassmembers class com.personalkanban.app.data.remote.dto.** { *; }
-dontwarn kotlinx.serialization.**
