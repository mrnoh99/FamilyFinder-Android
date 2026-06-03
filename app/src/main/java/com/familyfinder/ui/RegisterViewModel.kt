package com.familyfinder.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinder.audio.WavRecorder
import com.familyfinder.data.FamilyDatabase
import com.familyfinder.data.FamilyMember
import com.familyfinder.data.FamilyRepository
import com.familyfinder.data.TtsSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "RegisterViewModel"

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

    private val _recordingError = MutableStateFlow<String?>(null)
    val recordingError: StateFlow<String?> = _recordingError

    // 편집 중인 가족의 id (null이면 새 가족 등록 모드)
    private val _editingId = MutableStateFlow<Int?>(null)
    val editingId: StateFlow<Int?> = _editingId

    private val recorder = WavRecorder(application)
    private var pendingType: RecordingType? = null
    private var pendingFile: File? = null

    // 정답/오답 반응은 모든 가족 공통이라 앱 내부에 한 번만 녹음해 재사용한다.
    private val globalCorrectFile = File(application.filesDir, "global_correct.wav")
    private val globalIncorrectFile = File(application.filesDir, "global_incorrect.wav")

    enum class RecordingType { QUESTION, CORRECT, INCORRECT }

    init {
        if (globalCorrectFile.exists()) _correctAudioPath.value = globalCorrectFile.absolutePath
        if (globalIncorrectFile.exists()) _incorrectAudioPath.value = globalIncorrectFile.absolutePath
        // 예시 시드가 비동기로 공통 반응 파일을 만들면 이후에 반영한다(앱 재시작 불필요).
        viewModelScope.launch {
            allMembers.collect {
                if (_correctAudioPath.value == null && globalCorrectFile.exists()) {
                    _correctAudioPath.value = globalCorrectFile.absolutePath
                }
                if (_incorrectAudioPath.value == null && globalIncorrectFile.exists()) {
                    _incorrectAudioPath.value = globalIncorrectFile.absolutePath
                }
            }
        }
    }

    /** 목록에서 가족을 선택하면 그 내용을 폼에 불러와 편집 모드로 전환한다. */
    fun startEditing(member: FamilyMember) {
        _editingId.value = member.id
        _relationship.value = member.relationship
        _photoUri.value = Uri.fromFile(File(member.photoPath))
        _questionAudioPath.value = member.questionAudioPath
        _correctAudioPath.value = member.correctAudioPath
        _incorrectAudioPath.value = member.incorrectAudioPath
    }

    /** 편집을 취소하고 새 가족 등록 모드로 돌아간다. */
    fun cancelEditing() {
        _editingId.value = null
        resetForm()
    }

    fun deleteMember(member: FamilyMember) {
        viewModelScope.launch {
            repository.delete(member)
            if (_editingId.value == member.id) cancelEditing()
        }
    }

    fun setRelationship(text: String) {
        _relationship.value = text
    }

    fun setPhotoUri(uri: Uri?) {
        _photoUri.value = uri
    }

    /** Returns true if capture actually started (mic acquired), matching the hold-to-record gesture. */
    fun startRecording(type: RecordingType): Boolean {
        if (_isRecording.value) return false
        val context = getApplication<Application>()

        // 질문은 가족별 파일, 정답/오답은 공통 고정 파일(재녹음 시 덮어씀).
        val file = when (type) {
            RecordingType.QUESTION ->
                File(context.filesDir, "audio_question_${System.currentTimeMillis()}.wav")
            RecordingType.CORRECT -> globalCorrectFile
            RecordingType.INCORRECT -> globalIncorrectFile
        }

        return if (recorder.startRecording(viewModelScope)) {
            pendingType = type
            pendingFile = file
            _isRecording.value = true
            _currentRecordingType.value = type
            true
        } else {
            _recordingError.value = "녹음을 시작할 수 없습니다. 마이크 권한을 확인해주세요."
            false
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
            } else {
                _recordingError.value = "녹음이 저장되지 않았습니다. 버튼을 누른 채 또렷이 말한 뒤 손을 떼주세요."
            }
        }
    }

    fun clearRecordingError() {
        _recordingError.value = null
    }

    // ── TTS(기계음)로 되돌리기 ──────────────────────────────────────────────

    /** 질문 음성을 현재 관계로 만든 TTS 문장으로 되돌린다. */
    fun revertQuestionToTts() {
        val context = getApplication<Application>()
        val rel = _relationship.value.trim().ifBlank { "가족" }
        val text = "${rel}는 어디 있을까요?"
        val file = File(context.filesDir, "audio_question_tts_${System.currentTimeMillis()}.wav")
        viewModelScope.launch {
            if (TtsSynthesizer.synthesizeOrBeep(context, text, file, 440.0)) {
                _questionAudioPath.value = file.absolutePath
            } else {
                _recordingError.value = "TTS 음성을 만들 수 없습니다."
            }
        }
    }

    /** 정답 반응(공통)을 TTS로 되돌린다. */
    fun revertCorrectToTts() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            if (TtsSynthesizer.synthesizeOrBeep(context, "잘했어요!", globalCorrectFile, 880.0)) {
                _correctAudioPath.value = globalCorrectFile.absolutePath
            } else {
                _recordingError.value = "TTS 음성을 만들 수 없습니다."
            }
        }
    }

    /** 오답 반응(공통)을 TTS로 되돌린다. */
    fun revertIncorrectToTts() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            if (TtsSynthesizer.synthesizeOrBeep(context, "다시 해 볼까요?", globalIncorrectFile, 247.0)) {
                _incorrectAudioPath.value = globalIncorrectFile.absolutePath
            } else {
                _recordingError.value = "TTS 음성을 만들 수 없습니다."
            }
        }
    }

    /**
     * 저장 시 선택한 사진을 사용자가 맞춘 확대/이동(scale·offset)대로 **정사각형으로 크롭**해
     * 저장한다. 정사각형으로 저장하므로 어떤 화면 방향에서도 정사각 셀에 비율 그대로 표시되어
     * 찌그러지지 않는다. EXIF 회전도 보정한다.
     */
    fun saveMember(
        photoScale: Float = 1f,
        photoOffsetX: Float = 0f,
        photoOffsetY: Float = 0f,
        photoBoxSizePx: Int = 0,
    ) {
        val context = getApplication<Application>()
        val rel = _relationship.value.trim()
        val photoUri = _photoUri.value
        val questionPath = _questionAudioPath.value
        val correctPath = _correctAudioPath.value
        val incorrectPath = _incorrectAudioPath.value

        if (rel.isEmpty() || photoUri == null || questionPath == null ||
            correctPath == null || incorrectPath == null
        ) {
            _recordingError.value = "저장할 수 없습니다. 관계·사진·음성을 모두 완료해 주세요."
            android.util.Log.w(tag, "saveMember called with incomplete fields")
            return
        }

        val editingId = _editingId.value

        viewModelScope.launch {
            val photoFile = File(context.filesDir, "photo_${System.currentTimeMillis()}.jpg")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val src = loadOrientedBitmap(photoUri) ?: return@runCatching false
                    val cropped = cropSquare(src, photoScale, photoOffsetX, photoOffsetY, photoBoxSizePx)
                    val output = downscaleIfNeeded(cropped, 1024)
                    FileOutputStream(photoFile).use { fos ->
                        output.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    }
                    true
                }.onFailure { e ->
                    android.util.Log.e(tag, "Photo crop/save failed", e)
                }.getOrDefault(false)
            }
            if (!ok) {
                // 크롭 실패 시에도 등록이 막히지 않도록 원본을 그대로 복사한다.
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(photoUri)?.use { input ->
                            photoFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }.onFailure { e ->
                        android.util.Log.e(tag, "Photo copy fallback failed", e)
                    }
                }
            }

            val member = FamilyMember(
                id = editingId ?: 0,
                relationship = rel,
                photoPath = photoFile.absolutePath,
                questionAudioPath = questionPath,
                correctAudioPath = correctPath,
                incorrectAudioPath = incorrectPath
            )
            if (editingId != null) {
                repository.update(member)
            } else {
                repository.insert(member)
                _savedCount.value++
            }
            _editingId.value = null
            _saveSuccess.value = true
            resetForm()
        }
    }

    /**
     * 현재 조정(확대/이동)대로 사진을 정사각형으로 잘라 적용한다. 결과를 임시 파일로 저장하고
     * photoUri를 그 잘린 이미지로 교체해, 미리보기에 확정된 사각형 사진이 바로 나타나게 한다.
     */
    fun applyPhotoCrop(
        photoScale: Float,
        photoOffsetX: Float,
        photoOffsetY: Float,
        photoBoxSizePx: Int,
    ) {
        val uri = _photoUri.value ?: return
        if (photoBoxSizePx <= 0) return
        val context = getApplication<Application>()
        // Remember the previous URI so we can clean up its cache file after replacement.
        val prevCacheFile = cacheFileOf(context, uri)
        viewModelScope.launch {
            val croppedUri = withContext(Dispatchers.IO) {
                runCatching {
                    val src = loadOrientedBitmap(uri) ?: return@runCatching null
                    val cropped = cropSquare(src, photoScale, photoOffsetX, photoOffsetY, photoBoxSizePx)
                    val output = downscaleIfNeeded(cropped, 1024)
                    val file = File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { fos ->
                        output.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    }
                    Uri.fromFile(file)
                }.onFailure { e ->
                    android.util.Log.e(tag, "applyPhotoCrop failed", e)
                }.getOrNull()
            }
            if (croppedUri != null) {
                try {
                    prevCacheFile?.delete()
                } catch (e: Exception) {
                    android.util.Log.w(tag, "Failed to delete previous crop cache file", e)
                }
                _photoUri.value = croppedUri
            }
        }
    }

    /** Returns the File if [uri] points to a file inside the app's cacheDir, otherwise null. */
    private fun cacheFileOf(context: Application, uri: Uri): File? {
        val path = uri.path ?: return null
        val file = File(path)
        return if (file.canonicalPath.startsWith(context.cacheDir.canonicalPath)) file else null
    }

    /** Uri에서 비트맵을 디코드하고 EXIF 방향대로 회전시켜 바로 세운다. (OOM 방지 다운샘플링 포함) */
    private fun loadOrientedBitmap(uri: Uri): Bitmap? {
        val resolver = getApplication<Application>().contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        val maxDim = 2048
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }

        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropSquare(
        src: Bitmap,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        boxSizePx: Int,
    ): Bitmap {
        if (boxSizePx <= 0) return src
        val rect = calculateCropRect(src.width, src.height, scale, offsetX, offsetY, boxSizePx)
        return Bitmap.createBitmap(src, rect[0], rect[1], rect[2], rect[3])
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, max: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= max) return bitmap
        val ratio = max.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
            (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
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
        // 정답/오답 반응은 모든 가족 공통이므로 유지하고, 가족별 항목만 비운다.
        _relationship.value = ""
        _photoUri.value = null
        _questionAudioPath.value = null
    }

    override fun onCleared() {
        super.onCleared()
        recorder.release()
    }

    companion object {
        internal fun missingFields(
            relationship: String,
            hasPhoto: Boolean,
            hasQuestion: Boolean,
            hasCorrect: Boolean,
            hasIncorrect: Boolean,
        ): List<String> = buildList {
            if (relationship.isBlank()) add("관계")
            if (!hasPhoto) add("사진")
            if (!hasQuestion) add("질문 녹음")
            if (!hasCorrect) add("정답 반응(공통)")
            if (!hasIncorrect) add("오답 반응(공통)")
        }
    }
}
