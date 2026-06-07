# =============================================================================
# Consumer ProGuard Rules for MapLibre Geoman Android
# These rules are included when this library is consumed by an application
# =============================================================================

# Keep GeoJSON model classes
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

# Keep Geoman public API - essential for library consumers
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

# Keep Kotlinx serialization
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
