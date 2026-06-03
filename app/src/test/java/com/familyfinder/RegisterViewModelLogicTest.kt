package com.familyfinder

import com.familyfinder.ui.RegisterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterViewModelLogicTest {

    @Test
    fun `all fields complete returns empty list`() {
        val missing = RegisterViewModel.missingFields(
            relationship = "엄마",
            hasPhoto = true,
            hasQuestion = true,
            hasCorrect = true,
            hasIncorrect = true,
        )
        assertTrue(missing.isEmpty())
    }

    @Test
    fun `blank relationship is flagged`() {
        val missing = RegisterViewModel.missingFields(
            relationship = "   ",
            hasPhoto = true,
            hasQuestion = true,
            hasCorrect = true,
            hasIncorrect = true,
        )
        assertEquals(listOf("관계"), missing)
    }

    @Test
    fun `missing photo is flagged`() {
        val missing = RegisterViewModel.missingFields(
            relationship = "아빠",
            hasPhoto = false,
            hasQuestion = true,
            hasCorrect = true,
            hasIncorrect = true,
        )
        assertEquals(listOf("사진"), missing)
    }

    @Test
    fun `missing question audio is flagged`() {
        val missing = RegisterViewModel.missingFields(
            relationship = "아빠",
            hasPhoto = true,
            hasQuestion = false,
            hasCorrect = true,
            hasIncorrect = true,
        )
        assertEquals(listOf("질문 녹음"), missing)
    }

    @Test
    fun `missing correct and incorrect responses are flagged`() {
        val missing = RegisterViewModel.missingFields(
            relationship = "할머니",
            hasPhoto = true,
            hasQuestion = true,
            hasCorrect = false,
            hasIncorrect = false,
        )
        assertEquals(listOf("정답 반응(공통)", "오답 반응(공통)"), missing)
    }

    @Test
    fun `all fields missing returns five items`() {
        val missing = RegisterViewModel.missingFields(
            relationship = "",
            hasPhoto = false,
            hasQuestion = false,
            hasCorrect = false,
            hasIncorrect = false,
        )
        assertEquals(5, missing.size)
        assertEquals(listOf("관계", "사진", "질문 녹음", "정답 반응(공통)", "오답 반응(공통)"), missing)
    }
}
