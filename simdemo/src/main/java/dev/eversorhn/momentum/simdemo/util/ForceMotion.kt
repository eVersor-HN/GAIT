package dev.eversorhn.momentum.simdemo.util

import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * This whole app's purpose is the animated ramp -- see the main MOMENTUM app's
 * ui/util/ForceMotion.kt for the full rationale. Pinned to 1x regardless of the system's
 * reduced-motion setting so the demo still demonstrates something on a device that has it on.
 */
private val FullMotionScale = object : MotionDurationScale {
    override val scaleFactor: Float = 1f
}

suspend fun withFullMotion(block: suspend CoroutineScope.() -> Unit) {
    withContext(FullMotionScale, block)
}
