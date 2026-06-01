package com.familyfinder

import com.familyfinder.ui.GameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameResultTest {

    @Test
    fun `initial state is NONE`() {
        assertEquals(GameResult.NONE, GameResult.valueOf("NONE"))
    }

    @Test
    fun `exactly three result states exist`() {
        assertEquals(3, GameResult.entries.size)
    }

    @Test
    fun `NONE is not an answered state`() {
        assertFalse(GameResult.NONE != GameResult.NONE)
    }

    @Test
    fun `CORRECT and INCORRECT are answered states`() {
        assertTrue(GameResult.CORRECT != GameResult.NONE)
        assertTrue(GameResult.INCORRECT != GameResult.NONE)
    }

    @Test
    fun `CORRECT and INCORRECT are distinct`() {
        assertTrue(GameResult.CORRECT != GameResult.INCORRECT)
    }

    @Test
    fun `ordinal ordering is NONE, CORRECT, INCORRECT`() {
        val values = GameResult.entries
        assertEquals(GameResult.NONE,      values[0])
        assertEquals(GameResult.CORRECT,   values[1])
        assertEquals(GameResult.INCORRECT, values[2])
    }
}
