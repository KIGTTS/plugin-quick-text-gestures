// SPDX-License-Identifier: GPL-3.0-only
// Adapted from LHT02/KIGTTS QuickTextGestureRecognizer; see NOTICE.md.
package com.kigtts.input.gestures

import android.gesture.Gesture
import android.gesture.GesturePoint as AndroidPoint
import android.gesture.GestureStore
import android.gesture.GestureStroke
import kotlin.math.hypot

internal class GestureRecognizer(active: Set<String>) {
    private val store = GestureStore().apply {
        orientationStyle = GestureStore.ORIENTATION_SENSITIVE
        sequenceType = GestureStore.SEQUENCE_SENSITIVE
        GestureTemplates.all.filter { it.id in active }.forEach { addGesture(it.id, it.points.gesture()) }
    }
    fun recognize(points: List<GesturePoint>, duration: Long, width: Float, height: Float): String? {
        if (points.size !in 8..512 || duration !in 120..5000 || width <= 0 || height <= 0 ||
            !width.isFinite() || !height.isFinite() || points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..width || it.y !in 0f..height }) return null
        val spanX = points.maxOf { it.x } - points.minOf { it.x }
        val spanY = points.maxOf { it.y } - points.minOf { it.y }
        if (spanX < width * .12f || spanY < height * .14f) return null
        val length = points.zipWithNext().sumOf { (a, b) -> hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }
        val diagonal = hypot(spanX.toDouble(), spanY.toDouble())
        if (length < minOf(width, height) * .72 || diagonal <= 0 || length / diagonal < 1.32) return null
        val predictions = store.recognize(points.gesture()).sortedByDescending { it.score }
        val best = predictions.firstOrNull() ?: return null
        val next = predictions.getOrNull(1)?.score ?: 0.0
        if (best.score < 1.8 || next > 0 && best.score / next < 1.12) return null
        return best.name
    }
    private fun List<GesturePoint>.gesture() = Gesture().apply {
        addStroke(GestureStroke(ArrayList(mapIndexed { index, point -> AndroidPoint(point.x, point.y, index * 16L) })))
    }
}
