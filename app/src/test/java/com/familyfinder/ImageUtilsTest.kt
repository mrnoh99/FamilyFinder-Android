package com.familyfinder

import com.familyfinder.ui.calculateCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun `square bitmap with no zoom returns full image`() {
        // 200×200 bitmap in 200px box, scale=1, no pan → full image
        val r = calculateCropRect(200, 200, 1f, 0f, 0f, 200)
        assertEquals(0, r[0]); assertEquals(0, r[1])
        assertEquals(200, r[2]); assertEquals(200, r[3])
    }

    @Test
    fun `wider bitmap is center-cropped vertically`() {
        // 400×200 bitmap in 200px box: height fills, width is cropped to center 200px
        val r = calculateCropRect(400, 200, 1f, 0f, 0f, 200)
        assertEquals(100, r[0])  // x offset into bitmap
        assertEquals(0,   r[1])
        assertEquals(200, r[2])  // 200px wide crop
        assertEquals(200, r[3])
    }

    @Test
    fun `taller bitmap is center-cropped horizontally`() {
        // 200×400 bitmap in 200px box: width fills, height is cropped to center 200px
        val r = calculateCropRect(200, 400, 1f, 0f, 0f, 200)
        assertEquals(0,   r[0])
        assertEquals(100, r[1])
        assertEquals(200, r[2])
        assertEquals(200, r[3])
    }

    @Test
    fun `2x zoom halves the crop area`() {
        // 200×200 bitmap, scale=2 → crop is 100×100 centered
        val r = calculateCropRect(200, 200, 2f, 0f, 0f, 200)
        assertEquals(50,  r[0]); assertEquals(50,  r[1])
        assertEquals(100, r[2]); assertEquals(100, r[3])
    }

    @Test
    fun `pan right shifts crop window left in bitmap space`() {
        // 200×200, scale=2, pan right 50px → crop shifts left 25px in bitmap coords
        val centered = calculateCropRect(200, 200, 2f, 0f, 0f, 200)
        val panned   = calculateCropRect(200, 200, 2f, 50f, 0f, 200)
        assertTrue("pan right should move x left", panned[0] < centered[0])
    }

    @Test
    fun `degenerate zero boxSize returns safe defaults`() {
        val r = calculateCropRect(100, 100, 1f, 0f, 0f, 0)
        assertTrue(r[2] > 0)
        assertTrue(r[3] > 0)
    }

    @Test
    fun `crop rect never exceeds bitmap bounds`() {
        // Extreme pan + zoom — result must still be inside the bitmap
        val r = calculateCropRect(100, 100, 4f, 200f, 200f, 200)
        assertTrue("x out of bounds",     r[0] in 0..99)
        assertTrue("y out of bounds",     r[1] in 0..99)
        assertTrue("x+w out of bounds",   r[0] + r[2] <= 100)
        assertTrue("y+h out of bounds",   r[1] + r[3] <= 100)
    }

    @Test
    fun `width and height are always at least 1`() {
        // Extreme values should not produce zero-size crop
        listOf(
            intArrayOf(1, 1),
            intArrayOf(10, 10),
            intArrayOf(1000, 500)
        ).forEach { (bw, bh) ->
            val r = calculateCropRect(bw, bh, 8f, 500f, 500f, 200)
            assertTrue("w must be >= 1", r[2] >= 1)
            assertTrue("h must be >= 1", r[3] >= 1)
        }
    }
}
