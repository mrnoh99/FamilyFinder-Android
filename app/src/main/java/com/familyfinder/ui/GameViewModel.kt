package com.familyfinder.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinder.data.FamilyDatabase
import com.familyfinder.data.FamilyMember
import com.familyfinder.data.FamilyRepository
import com.familyfinder.data.SampleSeeder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class GameResult { NONE, CORRECT, INCORRECT }

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FamilyRepository(
        FamilyDatabase.getDatabase(application).familyDao()
    )

    private val _currentSet = MutableStateFlow<List<FamilyMember>>(emptyList())
    val currentSet: StateFlow<List<FamilyMember>> = _currentSet

    private val _targetMember = MutableStateFlow<FamilyMember?>(null)
    val targetMember: StateFlow<FamilyMember?> = _targetMember

    private val _gameResult = MutableStateFlow(GameResult.NONE)
    val gameResult: StateFlow<GameResult> = _gameResult

    private val _memberCount = MutableStateFlow(0)
    val memberCount: StateFlow<Int> = _memberCount

    private val _selectedMemberId = MutableStateFlow<Int?>(null)
    val selectedMemberId: StateFlow<Int?> = _selectedMemberId

    private var mediaPlayer: MediaPlayer? = null

    init {
        // 첫 실행 시 예시 가족(엄마·아빠·할아버지·할머니)을 만들어 넣는다.
        viewModelScope.launch {
            SampleSeeder.seedIfNeeded(getApplication<Application>(), repository)
        }
        viewModelScope.launch {
            repository.allMembers.collect { members ->
                _memberCount.value = members.size
                // 가족 목록이 바뀌면(등록/수정/삭제) 게임을 처음 시작 화면으로 되돌린다.
                resetToStart()
            }
        }
    }

    /** 진행 중이던 게임을 비우고 "게임 시작" 화면 상태로 되돌린다. */
    private fun resetToStart() {
        mediaPlayer?.release()
        mediaPlayer = null
        _currentSet.value = emptyList()
        _targetMember.value = null
        _gameResult.value = GameResult.NONE
        _selectedMemberId.value = null
    }

    fun startGame() {
        viewModelScope.launch {
            val members = repository.allMembers.first()
            if (members.size < 4) return@launch

            val set = members.shuffled().take(4)
            val target = set.random()
            _currentSet.value = set
            _targetMember.value = target
            _gameResult.value = GameResult.NONE
            _selectedMemberId.value = null
            // 새 문제가 시작되면 질문 소리를 자동으로 들려준다(별도 소리듣기 버튼 불필요).
            playAudio(target.questionAudioPath)
        }
    }

    fun playQuestion() {
        val target = _targetMember.value ?: return
        playAudio(target.questionAudioPath)
    }

    fun selectMember(member: FamilyMember) {
        if (_gameResult.value != GameResult.NONE) return

        _selectedMemberId.value = member.id
        val isCorrect = member.id == _targetMember.value?.id

        if (isCorrect) {
            _gameResult.value = GameResult.CORRECT
            _targetMember.value?.let { playAudio(it.correctAudioPath) }
        } else {
            _gameResult.value = GameResult.INCORRECT
            playAudio(member.incorrectAudioPath)
        }
    }

    private fun playAudio(path: String) {
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener { release() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}
