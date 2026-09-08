package com.geoman.maplibre.geoman.core.io

import com.geoman.maplibre.geoman.core.GeomanCoreConstants.FEATURE_ID_PROPERTY
import com.geoman.maplibre.geoman.core.GeomanCoreConstants.FEATURE_SHAPE_PROPERTY
import com.geoman.maplibre.geoman.core.features.FeatureData
import com.geoman.maplibre.geoman.core.features.FeatureShape
import com.geoman.maplibre.geoman.core.features.PropertyValidators
import com.geoman.maplibre.geoman.utils.generateFeatureId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.geoman.maplibre.geoman.core.GeomanCoreConstants.FEATURE_PROPERTY_PREFIX as SYSTEM_PROPERTY_PREFIX

/**
 * Error produced while decoding a GeoJSON document.
 *
 * Document-level failures (unparseable JSON, wrong root type) are reported as
 * [ImportError.Document] with no feature index; per-feature rejections are
 * reported as [ImportError.Feature] carrying the feature's document index.
 */
sealed class ImportError {
    /** Human-readable explanation of the rejection. */
    abstract val message: String

    /** Position of the rejected feature in the document, or null for document-level errors. */
    abstract val index: Int?

    /** The document as a whole could not be interpreted as GeoJSON. */
    data class Document(override val message: String) : ImportError() {
        override val index: Int? = null
    }

    /** A single feature at [index] was structurally invalid or failed validation. */
    data class Feature(override val index: Int, override val message: String) : ImportError()
}

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
                listOf(ImportError.Document("invalid JSON document: ${e.message}")),
            )
        }

        val featureElements = GeoJsonDecoder.featureCollection(root)
            ?: return ImportResult(
                emptyList(),
                listOf(ImportError.Document("document must be a FeatureCollection or Feature")),
            )

        val features = mutableListOf<FeatureData>()
        val errors = mutableListOf<ImportError>()

        featureElements.forEach { (index, element) ->
            val feature = GeoJsonDecoder.feature(element)
            if (feature == null) {
                errors.add(ImportError.Feature(index, "malformed feature structure"))
                return@forEach
            }

            // System properties (__gm_*) carry tracking metadata. Restore the
            // tracking id from __gm_id when no top-level id is present so the
            // feature is addressable after a round-trip, and keep system
            // entries out of user-visible properties.
            val systemEntries = feature.properties.filterKeys { it.startsWith(SYSTEM_PROPERTY_PREFIX) }
            val userProperties = feature.properties.filterKeys { !it.startsWith(SYSTEM_PROPERTY_PREFIX) }
            val restored = feature.copy(
                id = feature.id ?: systemEntries[FEATURE_ID_PROPERTY] as? String,
                properties = userProperties,
            )

            val validation = PropertyValidators.validateFeature(restored)
            if (validation.isValid) {
                features.add(
                    FeatureData(
                        id = restored.id ?: generateFeatureId("imported_$index"),
                        sourceName = sourceName,
                        feature = restored,
                        properties = userProperties,
                        shape = FeatureShape.fromTag(systemEntries[FEATURE_SHAPE_PROPERTY] as? String),
                    ),
                )
            } else {
                errors.add(ImportError.Feature(index, validation.errors.joinToString("; ")))
            }
        }

        return ImportResult(features, errors)
    }
}
