package com.familyfinder

import com.familyfinder.ui.GameResult
import com.familyfinder.ui.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class GameViewModelLogicTest {

    @Test
    fun `matching ids return CORRECT`() {
        val result = GameViewModel.determineResult(selectedId = 3, targetId = 3)
        assertEquals(GameResult.CORRECT, result)
    }

    @Test
    fun `different ids return INCORRECT`() {
        val result = GameViewModel.determineResult(selectedId = 2, targetId = 5)
        assertEquals(GameResult.INCORRECT, result)
    }

    @Test
    fun `id zero vs zero is CORRECT`() {
        val result = GameViewModel.determineResult(selectedId = 0, targetId = 0)
        assertEquals(GameResult.CORRECT, result)
    }

    @Test
    fun `negative id mismatch returns INCORRECT`() {
        val result = GameViewModel.determineResult(selectedId = -1, targetId = 1)
        assertEquals(GameResult.INCORRECT, result)
    }
}
