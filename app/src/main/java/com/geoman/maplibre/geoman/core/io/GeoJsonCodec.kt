package com.geoman.maplibre.geoman.core.io

import com.geoman.maplibre.geoman.core.GeomanCoreConstants.FEATURE_SHAPE_PROPERTY
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.FeatureShape
import com.geoman.maplibre.geoman.core.features.PropertyValidators
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.geoman.maplibre.geoman.core.GeomanCoreConstants.FEATURE_PROPERTY_PREFIX as SYSTEM_PROPERTY_PREFIX

/**
 * Error for a single rejected feature in a batch import.
 */
data class ImportError(val index: Int, val message: String)

/**
 * Result of decoding a GeoJSON document.
 *
 * [ImportResult.features] contains every structurally valid feature;
 * [ImportResult.errors] reports why individual features were rejected, so
 * callers can import partial batches.
 */
data class ImportResult(val features: List<FeatureData>, val errors: List<ImportError>) {
    val isSuccess: Boolean get() = errors.isEmpty()
}

/**
 * GeoJSON encoding/decoding built on kotlinx.serialization JSON elements.
 *
 * Hand-rolled element conversion is used instead of direct polymorphic
 * serialization because feature properties hold arbitrary `Any?` values.
 *
 * Encoding is delegated to [GeoJsonEncoder], decoding to [GeoJsonDecoder].
 */
object GeoJsonCodec {

    internal val json = Json { prettyPrint = true }

    /**
     * Encode a list of features as a pretty-printed FeatureCollection string.
     */
    fun encodeFeatureCollection(features: List<FeatureData>): String = json.encodeToString(
        JsonObject.serializer(),
        GeoJsonEncoder.featureCollection(features),
    )

    /**
     * Encode a single feature as a JSON object string.
     */
    fun encodeFeature(feature: FeatureData): String =
        json.encodeToString(JsonObject.serializer(), GeoJsonEncoder.feature(feature))

    /**
     * Decode a FeatureCollection (or a single Feature) document.
     *
     * Each feature is validated with [PropertyValidators]; invalid ones are
     * reported in [ImportResult.errors] while valid ones are returned ready
     * to be added to the store.
     */
    fun decode(text: String, sourceName: String): ImportResult {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            return ImportResult(
                emptyList(),
                listOf(ImportError(-1, "invalid JSON document: ${e.message}")),
            )
        }

        val featureElements = GeoJsonDecoder.featureCollection(root)
            ?: return ImportResult(
                emptyList(),
                listOf(ImportError(-1, "document must be a FeatureCollection or Feature")),
            )

        val features = mutableListOf<FeatureData>()
        val errors = mutableListOf<ImportError>()

        featureElements.forEach { (index, element) ->
            val feature = GeoJsonDecoder.feature(element)
            if (feature == null) {
                errors.add(ImportError(index, "malformed feature structure"))
                return@forEach
            }

            val validation = PropertyValidators.validateFeature(feature)
            if (validation.isValid) {
                // System properties (__gm_*) carry tracking metadata; restore
                // the shape and keep them out of user-visible properties.
                val systemEntries = feature.properties.filterKeys { it.startsWith(SYSTEM_PROPERTY_PREFIX) }
                val userProperties = feature.properties.filterKeys { !it.startsWith(SYSTEM_PROPERTY_PREFIX) }
                features.add(
                    FeatureData(
                        id = feature.id ?: "imported_${index}_${System.nanoTime()}",
                        sourceName = sourceName,
                        feature = feature.copy(properties = userProperties),
                        properties = userProperties,
                        shape = FeatureShape.fromTag(systemEntries[FEATURE_SHAPE_PROPERTY] as? String),
                    ),
                )
            } else {
                errors.add(ImportError(index, validation.errors.joinToString("; ")))
            }
        }

        return ImportResult(features, errors)
    }
}
