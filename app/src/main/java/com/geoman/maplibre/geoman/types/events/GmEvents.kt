package com.geoman.maplibre.geoman.types.events

import com.geoman.maplibre.geoman.core.features.FeatureData

/**
 * Base event interface for all Geoman events
 */
interface GmEvent {
    val type: String
    val target: Any?
}

/**
 * Control event types
 */
sealed class GmControlEvent(override val type: String) : GmEvent {
    override val target: Any? = null

    object Load : GmControlEvent("gm:control:load")
    object Enable : GmControlEvent("gm:control:enable")
    object Disable : GmControlEvent("gm:control:disable")
}

/**
 * Draw event types
 */
sealed class GmDrawEvent(override val type: String, open val shape: String) : GmEvent {
    override val target: Any? = null

    data class Create(override val shape: String, val feature: FeatureData? = null) :
        GmDrawEvent("gm:draw:create", shape)

    data class EditStart(override val shape: String, val feature: FeatureData? = null) :
        GmDrawEvent("gm:draw:editstart", shape)

    data class EditEnd(override val shape: String, val feature: FeatureData? = null) :
        GmDrawEvent("gm:draw:editend", shape)

    data class Remove(override val shape: String, val feature: FeatureData? = null) :
        GmDrawEvent("gm:draw:remove", shape)
}

/**
 * Edit event types
 */
sealed class GmEditEvent(override val type: String) : GmEvent {
    override val target: Any? = null

    data class DragStart(val feature: FeatureData? = null) : GmEditEvent("gm:edit:dragstart")

    data class DragEnd(val feature: FeatureData? = null) : GmEditEvent("gm:edit:dragend")

    data class ChangeStart(val feature: FeatureData? = null) : GmEditEvent("gm:edit:changestart")

    data class ChangeEnd(val feature: FeatureData? = null) : GmEditEvent("gm:edit:changeend")

    data class RotateStart(val feature: FeatureData? = null) : GmEditEvent("gm:edit:rotatestart")

    data class RotateEnd(val feature: FeatureData? = null) : GmEditEvent("gm:edit:rotateend")

    data class CutStart(val feature: FeatureData? = null) : GmEditEvent("gm:edit:cutstart")

    data class CutEnd(val feature: FeatureData? = null) : GmEditEvent("gm:edit:cutend")

    data class Delete(val feature: FeatureData? = null) : GmEditEvent("gm:edit:delete")
}

/**
 * Feature event types
 */
sealed class GmFeatureEvent(override val type: String) : GmEvent {
    override val target: Any? = null

    data class BeforeCreate(val feature: FeatureData? = null) : GmFeatureEvent("gm:feature:beforecreate")

    data class Created(val feature: FeatureData? = null) : GmFeatureEvent("gm:feature:created")

    data class BeforeUpdate(val feature: FeatureData? = null) : GmFeatureEvent("gm:feature:beforeupdate")

    data class Updated(val feature: FeatureData? = null) : GmFeatureEvent("gm:feature:updated")

    data class BeforeRemove(val feature: FeatureData? = null) : GmFeatureEvent("gm:feature:beforeremove")

    data class Removed(val feature: FeatureData? = null) : GmFeatureEvent("gm:feature:removed")
}

/**
 * Helper event types
 */
sealed class GmHelperEvent(override val type: String) : GmEvent {
    override val target: Any? = null

    data class SnapStart(val feature: FeatureData? = null) : GmHelperEvent("gm:helper:snapstart")

    data class SnapEnd(val feature: FeatureData? = null) : GmHelperEvent("gm:helper:snapend")
}

/**
 * Mode event types
 */
sealed class GmModeEvent(override val type: String, open val modeName: String) : GmEvent {
    override val target: Any? = null

    data class Enable(override val modeName: String, val modeType: String) :
        GmModeEvent("gm:mode:enable", modeName)

    data class Disable(override val modeName: String, val modeType: String) :
        GmModeEvent("gm:mode:disable", modeName)

    data class Toggle(override val modeName: String, val modeType: String) :
        GmModeEvent("gm:mode:toggle", modeName)
}

/**
 * Map load event
 */
sealed class GmMapEvent(override val type: String) : GmEvent {
    override val target: Any? = null

    object Loaded : GmMapEvent("gm:loaded")
    object Destroyed : GmMapEvent("gm:destroyed")
}
