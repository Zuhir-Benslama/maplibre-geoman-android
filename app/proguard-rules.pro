# =============================================================================
# ProGuard Rules for MapLibre Geoman Android Library (Internal)
# These rules apply when building this library itself
# =============================================================================

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep GeoJSON model classes for serialization
-keep class com.geoman.maplibre.geoman.types.geojson.** { *; }

# Keep MapLibre classes
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# Keep Geoman public API - don't obfuscate public API
-keep public class com.geoman.maplibre.geoman.Geoman { *; }
-keep public class com.geoman.maplibre.geoman.core.options.** { *; }
-keep public class com.geoman.maplibre.geoman.core.controls.** { *; }
-keep public class com.geoman.maplibre.geoman.core.events.** { *; }
-keep public class com.geoman.maplibre.geoman.core.features.** { *; }
-keep public class com.geoman.maplibre.geoman.modes.draw.** { *; }
-keep public class com.geoman.maplibre.geoman.modes.edit.** { *; }
-keep public class com.geoman.maplibre.geoman.modes.helpers.** { *; }
-keep public class com.geoman.maplibre.geoman.adapter.** { *; }
-keep public class com.geoman.maplibre.geoman.types.** { *; }
-keep public class com.geoman.maplibre.geoman.utils.** { *; }

# Keep Compose components
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Compose compiler generated classes
-keepclassmembers,allowobfuscation class * implements androidx.compose.runtime.Composer {
    void <init>(...);
}

# Keep Kotlinx serialization
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep thread-safe collections
-keep class java.util.concurrent.** { *; }
