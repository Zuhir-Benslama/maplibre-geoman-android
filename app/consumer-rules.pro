# =============================================================================
# Consumer ProGuard Rules for MapLibre Geoman Android
# These rules are included when this library is consumed by an application
# =============================================================================

# Remove Geoman debug/verbose logging in release builds. This only affects the
# library's own logging calls; consumer logging is untouched.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep GeoJSON model classes for kotlinx.serialization reflection
-keep class com.geoman.maplibre.geoman.types.geojson.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep serialization metadata (needed for @Serializable codegen)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# Keep Geoman public API - essential for library consumers.
# Scoped to the library's own packages; the MapLibre and Compose runtime
# dependencies ship their own consumer rules and remain reachable from the
# kept API, so no blanket keeps are needed here.
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