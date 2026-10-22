// SPDX-License-Identifier: GPL-3.0-only
// Gesture paths and recognition rules adapted from LHT02/KIGTTS; see NOTICE.md.
package com.kigtts.input.gestures

internal data class GesturePoint(val x: Float, val y: Float)
internal data class GestureTemplate(val id: String, val name: String, val instruction: String, val points: List<GesturePoint>)
internal object GestureTemplates {
    val all = listOf(
        template("m_shape", "M 形", "从左下开始，一笔画出 M 形。", .10f to .86f, .10f to .16f, .50f to .70f, .90f to .16f, .90f to .86f),
        template("w_shape", "W 形", "从左上开始，一笔画出 W 形。", .10f to .16f, .10f to .86f, .50f to .30f, .90f to .86f, .90f to .16f),
        template("z_shape", "Z 形", "从左上向右画，再斜向左下，最后向右收笔。", .10f to .18f, .90f to .18f, .10f to .84f, .90f to .84f),
        template("lightning", "闪电", "从右上开始，沿折线一笔画到左下。", .82f to .10f, .30f to .46f, .68f to .46f, .18f to .90f),
        template("hourglass", "沙漏", "从左上斜到右下，再向上折回左下。", .12f to .14f, .88f to .86f, .88f to .14f, .12f to .86f),
        template("folded_hook", "折钩", "从左上向下，沿底部折向右上，再向内收笔。", .16f to .12f, .16f to .78f, .32f to .90f, .70f to .90f, .86f to .72f, .86f to .42f, .66f to .26f),
    )
    private fun template(id: String, name: String, instruction: String, vararg points: Pair<Float, Float>) =
        GestureTemplate(id, name, instruction, points.map { GesturePoint(it.first, it.second) })
}
