# MapLibre Geoman Android

<p align="center">
  <strong>MapLibre Plugin For Creating And Editing Geometry Layers on Android</strong><br>
  Draw, Edit, Drag, Rotate, and Delete Layers with Jetpack Compose support<br>
  Supports Markers, Polylines, Polygons, Circles, and Rectangles
</p>

<p align="center">
  <a href="https://github.com/geoman-io/maplibre-geoman-android">
    <img src="https://img.shields.io/badge/version-1.0.0-blue.svg" alt="Version" />
  </a>
  <a href="https://opensource.org/licenses/MIT">
    <img src="https://img.shields.io/badge/license-MIT-green.svg" alt="License" />
  </a>
  <a href="https://android-arsenal.com/api?level=24">
    <img src="https://img.shields.io/badge/API-24%2B-brightgreen.svg" alt="API" />
  </a>
</p>

## Overview

MapLibre Geoman Android is a Kotlin library that brings powerful drawing and editing capabilities to MapLibre Android maps. Built with Jetpack Compose support and following modern Android development practices.

## Features

- **Drawing Modes:**
  - Marker
  - Line/Polyline
  - Polygon
  - Circle
  - Rectangle

- **Editing Modes:**
  - Drag - Move features around
  - Change - Edit vertices
  - Rotate - Rotate features
  - Delete - Remove features

- **Helper Modes:**
  - Snapping - Snap to existing features

- **Jetpack Compose UI** - Modern declarative UI controls
- **GeoJSON Support** - Full GeoJSON feature handling
- **Event System** - Reactive event handling with Kotlin Flow
- **Customizable Styling** - Configure colors, sizes, and more

## Installation

### Gradle Setup

Add the JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.geoman-io:maplibre-geoman-android:1.0.0")
}
```

### Required Dependencies

The library includes these dependencies automatically:
- MapLibre Android SDK 11.5.1
- Kotlin Coroutines
- Jetpack Compose
- Kotlinx Serialization

## Quick Start

### Basic Setup

```kotlin
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.core.options.GmOptionsData
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

class MainActivity : ComponentActivity() {
    private lateinit var geoman: Geoman
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize MapLibre
        val mapView = MapView(this)
        setContentView(mapView)
        
        mapView.getMapAsync { map ->
            // Initialize Geoman
            geoman = Geoman(
                mapView = mapView,
                map = map,
                options = GmOptionsData(
                    settings = SettingsOptions(
                        useControlsUi = true,
                        controlsPosition = ControlsPosition.TOP_LEFT
                    )
                )
            )
            
            // Wait for Geoman to load
            lifecycleScope.launch {
                geoman.waitForGeomanLoaded()
                println("Geoman loaded successfully!")
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }
    
    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        geoman.destroy()
        mapView.onDestroy()
    }
}
```

### Using with Jetpack Compose

```kotlin
import androidx.compose.runtime.*
import com.geoman.maplibre.geoman.Geoman
import com.geoman.maplibre.geoman.GeomanControls
import com.geoman.maplibre.geoman.core.options.GmOptionsData

@Composable
fun MapWithGeoman() {
    var geoman by remember { mutableStateOf<Geoman?>(null) }
    var isLoaded by remember { mutableStateOf(false) }
    
    // MapLibre Compose Map
    MapLibreMap(
        modifier = Modifier.fillMaxSize(),
        onMapLoaded = { map, mapView ->
            geoman = Geoman(
                mapView = mapView,
                map = map,
                options = GmOptionsData()
            )
            isLoaded = true
        }
    )
    
    // Geoman Controls Overlay
    if (isLoaded && geoman != null) {
        GeomanControls(
            geoman = geoman!!,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
```

## Usage

### Drawing Shapes

```kotlin
// Enable drawing mode
geoman.enableDraw(DrawModeName.MARKER)
geoman.enableDraw(DrawModeName.LINE)
geoman.enableDraw(DrawModeName.POLYGON)
geoman.enableDraw(DrawModeName.CIRCLE)
geoman.enableDraw(DrawModeName.RECTANGLE)

// Toggle drawing mode
geoman.toggleDraw(DrawModeName.POLYGON)

// Check if drawing mode is active
if (geoman.drawEnabled(DrawModeName.LINE)) {
    // Line drawing is active
}

// Disable drawing
geoman.disableDraw(DrawModeName.POLYGON)
```

### Editing Features

```kotlin
// Enable drag mode
geoman.enableEdit(EditModeName.DRAG)

// Enable edit vertices mode
geoman.enableEdit(EditModeName.CHANGE)

// Enable rotate mode
geoman.enableEdit(EditModeName.ROTATE)

// Enable delete mode
geoman.enableEdit(EditModeName.DELETE)
```

### Working with Features

```kotlin
// Add a GeoJSON feature
val pointFeature = Feature(
    id = "my-point",
    geometry = Point.fromLngLat(LngLat(-74.0, 40.7)),
    properties = mapOf("name" to "New York")
)
geoman.addGeoJsonFeature(pointFeature)

// Add a polygon
val polygonFeature = Feature(
    id = "my-polygon",
    geometry = Polygon.fromLngLats(listOf(
        listOf(
            LngLat(-74.0, 40.7),
            LngLat(-74.0, 40.8),
            LngLat(-73.9, 40.8),
            LngLat(-73.9, 40.7),
            LngLat(-74.0, 40.7) // Close the polygon
        )
    ))
)
geoman.addGeoJsonFeature(polygonFeature)

// Get all features
val allFeatures = geoman.getAllFeatures()

// Get specific feature
val feature = geoman.getFeature(GeomanConstants.SOURCE_POLYGONS, "my-polygon")

// Remove a feature
geoman.removeFeature(GeomanConstants.SOURCE_POLYGONS, "my-polygon")

// Clear all features
geoman.clearAllFeatures()
```

### Event Handling

```kotlin
// Listen to Geoman events
lifecycleScope.launch {
    geoman.events.events.collect { event ->
        when (event) {
            is GmDrawEvent.Create -> {
                println("Feature created: ${event.shape}")
            }
            is GmEditEvent.DragStart -> {
                println("Started dragging feature")
            }
            is GmFeatureEvent.Created -> {
                println("New feature added")
            }
            is GmMapEvent.Loaded -> {
                println("Geoman fully loaded")
            }
        }
    }
}

// Subscribe to specific event types
geoman.events.on("gm:draw:create") { event ->
    println("Draw create event: $event")
}

// One-time event
geoman.events.once("gm:loaded") { event ->
    println("Geoman loaded!")
}
```

### Configuration

```kotlin
val options = GmOptionsData(
    settings = SettingsOptions(
        useControlsUi = true,
        controlsPosition = ControlsPosition.TOP_LEFT,
        enableSnap = true,
        snapDistance = 20f
    ),
    drawOptions = DrawOptions(
        allowSelfIntersections = false,
        snappable = true,
        finishOn = FinishOn.DOUBLE_CLICK
    ),
    editOptions = EditOptions(
        draggable = true,
        rotateable = true,
        removable = true
    ),
    layerStyles = LayerStyles(
        polygon = PolygonStyle(
            fillColor = Color(0x4D3388FF),
            color = Color(0xFF3388FF),
            width = 4f
        ),
        line = LineStyle(
            color = Color(0xFF3388FF),
            width = 4f
        )
    )
)

geoman = Geoman(mapView, map, options)
```

## Architecture

### Core Components

- **Geoman** - Main entry point, manages all functionality
- **BaseMapAdapter** - Abstracts map operations
- **MapLibreAdapter** - MapLibre Android implementation
- **Features** - Manages GeoJSON features
- **GmEventBus** - Event handling with Kotlin Flow
- **GmOptions** - Configuration management

### Modes

- **BaseDraw** - Base class for drawing modes
- **BaseEdit** - Base class for editing modes
- **BaseHelper** - Base class for helper modes

### Types

- **GeoJSON Types** - Point, LineString, Polygon, etc.
- **Mode Types** - DrawModeName, EditModeName, HelperModeName
- **Event Types** - GmDrawEvent, GmEditEvent, GmFeatureEvent

## ProGuard Rules

The library includes default ProGuard rules. If you encounter issues, add these to your `proguard-rules.pro`:

```proguard
# Keep Geoman classes
-keep class com.geoman.maplibre.geoman.** { *; }
-keep class com.geoman.maplibre.geoman.types.geojson.** { *; }

# Keep MapLibre classes
-keep class org.maplibre.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.Serializable
```

## Requirements

- Android API 24+
- Kotlin 1.9+
- MapLibre Android SDK 11.5.1
- Jetpack Compose (for UI components)

## Migration from JavaScript Version

This Android library follows the same API patterns as the JavaScript version:

| JavaScript API | Android API |
|---------------|-------------|
| `geoman.enableDraw('Polygon')` | `geoman.enableDraw(DrawModeName.POLYGON)` |
| `geoman.disableDraw()` | `geoman.disableDraw(DrawModeName.POLYGON)` |
| `geoman.on('gm:draw:create', cb)` | `geoman.events.on('gm:draw:create', cb)` |
| `geoman.addGeoJson(feature)` | `geoman.addGeoJsonFeature(feature)` |

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting PRs.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## License

MIT License - See [LICENSE](LICENSE) for details.

## Links

- [GitHub Repository](https://github.com/geoman-io/maplibre-geoman-android)
- [MapLibre Android Documentation](https://maplibre.org/maplibre-native/android/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)

## Acknowledgments

- Based on the excellent [maplibre-geoman](https://github.com/geoman-io/maplibre-geoman) JavaScript library
- Built with [MapLibre Android SDK](https://github.com/maplibre/maplibre-native)
- Uses [Jetpack Compose](https://developer.android.com/jetpack/compose) for modern UI
