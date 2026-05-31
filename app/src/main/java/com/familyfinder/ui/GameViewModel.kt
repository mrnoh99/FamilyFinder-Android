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

    // 한 번이라도 게임을 시작했으면 시작 화면의 버튼을 "다음 문제"로 보여준다.
    private val _hasPlayed = MutableStateFlow(false)
    val hasPlayed: StateFlow<Boolean> = _hasPlayed

    // 질문 음성이 재생되는 동안은 사진 선택(touch)을 막는다.
    private val _questionPlaying = MutableStateFlow(false)

    // 정답/오답 음성이 재생되는 동안은 다음 진행(touch)을 막는다.
    private val _resultPlaying = MutableStateFlow(false)
    val resultPlaying: StateFlow<Boolean> = _resultPlaying

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
    fun resetToStart() {
        mediaPlayer?.release()
        mediaPlayer = null
        _questionPlaying.value = false
        _resultPlaying.value = false
        _currentSet.value = emptyList()
        _targetMember.value = null
        _gameResult.value = GameResult.NONE
        _selectedMemberId.value = null
    }

    fun startGame() {
        viewModelScope.launch {
            val members = repository.allMembers.first()
            if (members.size < 4) return@launch

            _hasPlayed.value = true
            val set = members.shuffled().take(4)
            val target = set.random()
            _currentSet.value = set
            _targetMember.value = target
            _gameResult.value = GameResult.NONE
            _selectedMemberId.value = null
            _resultPlaying.value = false
            // 새 문제가 시작되면 질문 소리를 들려주고, 끝날 때까지 사진 선택을 막는다.
            _questionPlaying.value = true
            playAudio(target.questionAudioPath) { _questionPlaying.value = false }
        }
    }

    fun playQuestion() {
        val target = _targetMember.value ?: return
        _questionPlaying.value = true
        playAudio(target.questionAudioPath) { _questionPlaying.value = false }
    }

    fun selectMember(member: FamilyMember) {
        // 질문 음성이 끝나기 전이거나 이미 답한 경우 무시
        if (_questionPlaying.value) return
        if (_gameResult.value != GameResult.NONE) return

        _selectedMemberId.value = member.id
        val isCorrect = member.id == _targetMember.value?.id

        // 정답/오답 음성이 끝날 때까지 다음 진행을 막는다.
        _resultPlaying.value = true
        if (isCorrect) {
            _gameResult.value = GameResult.CORRECT
            playAudio(member.correctAudioPath) { _resultPlaying.value = false }
        } else {
            _gameResult.value = GameResult.INCORRECT
            playAudio(member.incorrectAudioPath) { _resultPlaying.value = false }
        }
    }

    private fun playAudio(path: String, onDone: () -> Unit = {}) {
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setOnCompletionListener { p ->
                if (mediaPlayer === p) mediaPlayer = null
                p.release()
                onDone()
            }
            mp.setOnErrorListener { p, _, _ ->
                if (mediaPlayer === p) mediaPlayer = null
                p.release()
                onDone()
                true
            }
            mp.setDataSource(path)
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            e.printStackTrace()
            onDone()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}
