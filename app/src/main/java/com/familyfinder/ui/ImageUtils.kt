package com.familyfinder.ui

import kotlin.math.roundToInt

/**
 * Pure math: computes the crop rect [x, y, width, height] in bitmap pixel space
 * that matches what the UI preview shows after the user applies scale and pan.
 * Has no Android dependencies, so it can be unit-tested on the JVM.
 */
internal fun calculateCropRect(
    bitmapWidth: Int,
    bitmapHeight: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    boxSizePx: Int
): IntArray {
    val bw = bitmapWidth
    val bh = bitmapHeight
    if (boxSizePx <= 0 || bw <= 0 || bh <= 0) {
        return intArrayOf(0, 0, bw.coerceAtLeast(1), bh.coerceAtLeast(1))
    }

    val cover = maxOf(boxSizePx / bw.toFloat(), boxSizePx / bh.toFloat())
    val disp = cover * scale
    if (disp <= 0f) return intArrayOf(0, 0, bw, bh)

    val left = boxSizePx / 2f - bw * disp / 2f + offsetX
    val top  = boxSizePx / 2f - bh * disp / 2f + offsetY

    val sx0 = ((0         - left) / disp).coerceIn(0f, bw.toFloat())
    val sy0 = ((0         - top)  / disp).coerceIn(0f, bh.toFloat())
    val sx1 = ((boxSizePx - left) / disp).coerceIn(0f, bw.toFloat())
    val sy1 = ((boxSizePx - top)  / disp).coerceIn(0f, bh.toFloat())

    val x = sx0.roundToInt().coerceIn(0, bw - 1)
    val y = sy0.roundToInt().coerceIn(0, bh - 1)
    val w = (sx1 - sx0).roundToInt().coerceIn(1, bw - x)
    val h = (sy1 - sy0).roundToInt().coerceIn(1, bh - y)

    return intArrayOf(x, y, w, h)
}
