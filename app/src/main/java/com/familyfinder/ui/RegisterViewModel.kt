package com.familyfinder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinder.audio.WavRecorder
import com.familyfinder.data.FamilyDatabase
import com.familyfinder.data.FamilyMember
import com.familyfinder.data.FamilyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FamilyRepository(
        FamilyDatabase.getDatabase(application).familyDao()
    )

    val allMembers = repository.allMembers

    private val _relationship = MutableStateFlow("")
    val relationship: StateFlow<String> = _relationship

    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri: StateFlow<Uri?> = _photoUri

    private val _questionAudioPath = MutableStateFlow<String?>(null)
    val questionAudioPath: StateFlow<String?> = _questionAudioPath

    private val _correctAudioPath = MutableStateFlow<String?>(null)
    val correctAudioPath: StateFlow<String?> = _correctAudioPath

    private val _incorrectAudioPath = MutableStateFlow<String?>(null)
    val incorrectAudioPath: StateFlow<String?> = _incorrectAudioPath

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _currentRecordingType = MutableStateFlow<RecordingType?>(null)
    val currentRecordingType: StateFlow<RecordingType?> = _currentRecordingType

    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private val recorder = WavRecorder(application)
    private var pendingType: RecordingType? = null
    private var pendingFile: File? = null

    enum class RecordingType { QUESTION, CORRECT, INCORRECT }

    fun setRelationship(text: String) {
        _relationship.value = text
    }

    fun setPhotoUri(uri: Uri?) {
        _photoUri.value = uri
    }

    fun startRecording(type: RecordingType) {
        if (_isRecording.value) return
        val context = getApplication<Application>()

        val file = File(context.filesDir, "audio_${type.name.lowercase()}_${System.currentTimeMillis()}.wav")

        if (recorder.startRecording(viewModelScope)) {
            pendingType = type
            pendingFile = file
            _isRecording.value = true
            _currentRecordingType.value = type
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        val type = pendingType
        val file = pendingFile

        _isRecording.value = false
        _currentRecordingType.value = null
        pendingType = null
        pendingFile = null

        if (type == null || file == null) return

        viewModelScope.launch {
            val ok = recorder.stopAndSave(file)
            if (ok) {
                when (type) {
                    RecordingType.QUESTION -> _questionAudioPath.value = file.absolutePath
                    RecordingType.CORRECT -> _correctAudioPath.value = file.absolutePath
                    RecordingType.INCORRECT -> _incorrectAudioPath.value = file.absolutePath
                }
            }
        }
    }

    fun saveMember() {
        val context = getApplication<Application>()
        val rel = _relationship.value.trim()
        val photoUri = _photoUri.value
        val questionPath = _questionAudioPath.value
        val correctPath = _correctAudioPath.value
        val incorrectPath = _incorrectAudioPath.value

        if (rel.isEmpty() || photoUri == null || questionPath == null ||
            correctPath == null || incorrectPath == null
        ) return

        viewModelScope.launch {
            val photoFile = File(context.filesDir, "photo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(photoUri)?.use { input ->
                photoFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val member = FamilyMember(
                relationship = rel,
                photoPath = photoFile.absolutePath,
                questionAudioPath = questionPath,
                correctAudioPath = correctPath,
                incorrectAudioPath = incorrectPath
            )
            repository.insert(member)
            _savedCount.value++
            _saveSuccess.value = true
            resetForm()
        }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }

    fun canSave(): Boolean =
        _relationship.value.isNotBlank() &&
        _photoUri.value != null &&
        _questionAudioPath.value != null &&
        _correctAudioPath.value != null &&
        _incorrectAudioPath.value != null

    private fun resetForm() {
        _relationship.value = ""
        _photoUri.value = null
        _questionAudioPath.value = null
        _correctAudioPath.value = null
        _incorrectAudioPath.value = null
    }

    override fun onCleared() {
        super.onCleared()
        recorder.release()
    }
}
