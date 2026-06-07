package com.geoman.maplibre.geoman.core.features

/**
 * Property validators for feature data validation
 * Matches the web version's validators.ts
 */
object PropertyValidators {
    
    /**
     * Validate feature ID
     */
    fun validateFeatureId(id: String?): Boolean {
        return !id.isNullOrBlank() && id.length <= 256
    }
    
    /**
     * Validate feature shape type
     */
    fun validateShape(shape: String?): Boolean {
        return shape in VALID_SHAPES
    }
    
    /**
     * Validate coordinates
     */
    fun validateCoordinates(coordinates: List<Double>?, minSize: Int = 2): Boolean {
        if (coordinates == null || coordinates.size < minSize) return false
        return coordinates.all { it.isFinite() }
    }
    
    /**
     * Validate longitude
     */
    fun validateLongitude(lng: Double?): Boolean {
        return lng != null && lng in -180.0..180.0
    }
    
    /**
     * Validate latitude
     */
    fun validateLatitude(lat: Double?): Boolean {
        return lat != null && lat in -90.0..90.0
    }
    
    /**
     * Validate radius (for circles)
     */
    fun validateRadius(radius: Double?): Boolean {
        return radius != null && radius > 0 && radius < 1000000 // Max 1000km
    }
    
    /**
     * Validate feature properties
     */
    fun validateProperties(properties: Map<String, Any?>?): Boolean {
        if (properties == null) return true // Properties are optional
        return properties.keys.all { it.isNotBlank() && it.length <= 256 }
    }
    
    /**
     * Validate complete feature data
     */
    fun validateFeature(
        id: String?,
        shape: String?,
        coordinates: List<Double>?,
        properties: Map<String, Any?>? = null
    ): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (!validateFeatureId(id)) {
            errors.add("Invalid feature ID: $id")
        }
        
        if (!validateShape(shape)) {
            errors.add("Invalid shape: $shape. Must be one of: ${VALID_SHAPES.joinToString(", ")}")
        }
        
        val minCoords = when (shape) {
            "point" -> 2
            "line" -> 4
            "polygon" -> 6
            "circle" -> 3
            else -> 2
        }
        
        if (!validateCoordinates(coordinates, minCoords)) {
            errors.add("Invalid coordinates: insufficient or invalid coordinate data")
        }
        
        if (!validateProperties(properties)) {
            errors.add("Invalid properties: property keys must be non-blank")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * Validation result data class
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    ) {
        fun getErrorMessages(): String = errors.joinToString("; ")
    }
    
    /**
     * Valid shape names
     */
    private val VALID_SHAPES = listOf(
        "point",
        "line",
        "polygon",
        "circle",
        "rectangle",
        "ellipse",
        "marker"
    )
}
