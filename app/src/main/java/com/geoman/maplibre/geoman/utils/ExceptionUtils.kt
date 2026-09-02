package com.geoman.maplibre.geoman.utils

import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * Generate a unique feature ID with the given [prefix]. Centralizes feature ID
 * generation so all call sites use the same scheme instead of mixing UUIDs and
 * timestamp-based fallbacks.
 */
fun generateFeatureId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

/**
 * Executes [block], rethrowing [CancellationException] (so cooperative
 * coroutine cancellation is never swallowed) and converting any other
 * [Exception] into `null` after reporting it via [onError].
 *
 * Centralizes the repeated `catch (e: Exception) { if (e is CancellationException)
 * throw e; log(...) }` pattern used across adapter/style code paths. Catching the
 * generic [Exception] is intentional here and confined to this single, documented
 * helper rather than scattered throughout the codebase.
 */
inline fun <T> runCatchingRethrowCancellation(onError: (Throwable) -> Unit, block: () -> T): T? = try {
    block()
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    if (e is CancellationException) throw e
    onError(e)
    null
}
