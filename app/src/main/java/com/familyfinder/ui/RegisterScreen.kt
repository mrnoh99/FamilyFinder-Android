package com.familyfinder.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyfinder.data.FamilyMember
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RegisterScreen(viewModel: RegisterViewModel) {
    val relationship by viewModel.relationship.collectAsStateWithLifecycle()
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
    val questionAudioPath by viewModel.questionAudioPath.collectAsStateWithLifecycle()
    val correctAudioPath by viewModel.correctAudioPath.collectAsStateWithLifecycle()
    val incorrectAudioPath by viewModel.incorrectAudioPath.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val currentRecordingType by viewModel.currentRecordingType.collectAsStateWithLifecycle()
    val allMembers by viewModel.allMembers.collectAsStateWithLifecycle(emptyList())
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()
    val recordingError by viewModel.recordingError.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showForm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(editingId) {
        if (editingId != null) scrollState.animateScrollTo(0)
    }

    val hasRecordPermission: () -> Boolean = {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "마이크 권한이 필요합니다. 설정에서 허용해 주세요.",
                    actionLabel = "설정 열기",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            }
        }
    }

    val onRequestPermission: () -> Unit = {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) viewModel.setPhotoUri(uri) }

    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean -> if (success) cameraUri?.let { viewModel.setPhotoUri(it) } }

    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            showForm = false
            snackbarHostState.showSnackbar("저장되었습니다! ✓")
            viewModel.clearSaveSuccess()
        }
    }

    LaunchedEffect(recordingError) {
        recordingError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearRecordingError()
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CommonAudioSection(
                correctAudioPath = correctAudioPath,
                incorrectAudioPath = incorrectAudioPath,
                isRecording = isRecording,
                currentRecordingType = currentRecordingType,
                hasRecordPermission = hasRecordPermission,
                onRequestPermission = onRequestPermission,
                onStartRecording = viewModel::startRecording,
                onStopRecording = viewModel::stopRecording,
                onRevertCorrectToTts = viewModel::revertCorrectToTts,
                onRevertIncorrectToTts = viewModel::revertIncorrectToTts
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            val missing = buildList {
                if (relationship.isBlank()) add("관계")
                if (photoUri == null) add("사진")
                if (questionAudioPath == null) add("질문 녹음")
                if (correctAudioPath == null) add("정답 반응(공통)")
                if (incorrectAudioPath == null) add("오답 반응(공통)")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (editingId != null) "가족 정보 수정" else "가족 등록",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                val formOpen = showForm || editingId != null
                Button(
                    onClick = { viewModel.cancelEditing(); showForm = !formOpen },
                    colors = if (formOpen) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.buttonColors()
                ) {
                    Icon(if (formOpen) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (formOpen) "취소" else "추가")
                }
            }

            Text(
                text = "등록된 가족: ${allMembers.size}명 " +
                    if (allMembers.size < 4) "(게임을 시작하려면 4명 이상 필요)" else "✓ 게임 가능!",
                fontSize = 14.sp,
                color = if (allMembers.size >= 4) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )

            if (showForm || editingId != null) {
                MemberEntryForm(
                    relationship = relationship,
                    photoUri = photoUri,
                    questionAudioPath = questionAudioPath,
                    correctAudioPath = correctAudioPath,
                    incorrectAudioPath = incorrectAudioPath,
                    editingId = editingId,
                    isRecording = isRecording,
                    currentRecordingType = currentRecordingType,
                    canSave = missing.isEmpty(),
                    missingItems = missing,
                    hasRecordPermission = hasRecordPermission,
                    onRequestPermission = onRequestPermission,
                    onRelationshipChange = viewModel::setRelationship,
                    onLaunchGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onLaunchCamera = {
                        val uri = createCameraImageUri(context)
                        cameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    onStartRecording = viewModel::startRecording,
                    onStopRecording = viewModel::stopRecording,
                    onRevertQuestionToTts = viewModel::revertQuestionToTts,
                    onApplyPhotoCrop = { scale, offsetX, offsetY, boxSizePx ->
                        viewModel.applyPhotoCrop(scale, offsetX, offsetY, boxSizePx)
                    },
                    onSave = { scale, offsetX, offsetY, boxSizePx ->
                        viewModel.saveMember(scale, offsetX, offsetY, boxSizePx)
                    },
                    onShowError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )
            }

            if (allMembers.isNotEmpty()) {
                RegisteredMemberList(
                    members = allMembers,
                    editingId = editingId,
                    onEditMember = viewModel::startEditing,
                    onDeleteMember = viewModel::deleteMember
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Section composables ───────────────────────────────────────────────────────

@Composable
private fun CommonAudioSection(
    correctAudioPath: String?,
    incorrectAudioPath: String?,
    isRecording: Boolean,
    currentRecordingType: RegisterViewModel.RecordingType?,
    hasRecordPermission: () -> Boolean,
    onRequestPermission: () -> Unit,
    onStartRecording: (RegisterViewModel.RecordingType) -> Boolean,
    onStopRecording: () -> Unit,
    onRevertCorrectToTts: () -> Unit,
    onRevertIncorrectToTts: () -> Unit
) {
    SectionCard(
        title = "공통 반응 음성 (모든 가족 공통 · 한 번만 녹음)",
        done = correctAudioPath != null && incorrectAudioPath != null
    ) {
        Text(
            text = "먼저 한 번만 녹음하면 모든 가족 게임에서 함께 사용됩니다.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = "정답 반응  ·  예: \"잘 했어요! 맞아요!\"", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        RecordingButton(
            label = "정답 반응 녹음",
            isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.CORRECT,
            hasRecording = correctAudioPath != null,
            audioPath = correctAudioPath,
            hasRecordPermission = hasRecordPermission,
            onRequestPermission = onRequestPermission,
            onHoldStart = { onStartRecording(RegisterViewModel.RecordingType.CORRECT) },
            onHoldEnd = onStopRecording
        )
        TextButton(onClick = onRevertCorrectToTts) {
            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("TTS(기계음)로 되돌리기")
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "오답 반응  ·  예: \"아닌데~ 다시 봐봐!\"", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        RecordingButton(
            label = "오답 반응 녹음",
            isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.INCORRECT,
            hasRecording = incorrectAudioPath != null,
            audioPath = incorrectAudioPath,
            hasRecordPermission = hasRecordPermission,
            onRequestPermission = onRequestPermission,
            onHoldStart = { onStartRecording(RegisterViewModel.RecordingType.INCORRECT) },
            onHoldEnd = onStopRecording
        )
        TextButton(onClick = onRevertIncorrectToTts) {
            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("TTS(기계음)로 되돌리기")
        }
    }
}

@Composable
private fun MemberEntryForm(
    relationship: String,
    photoUri: Uri?,
    questionAudioPath: String?,
    correctAudioPath: String?,
    incorrectAudioPath: String?,
    editingId: Int?,
    isRecording: Boolean,
    currentRecordingType: RegisterViewModel.RecordingType?,
    canSave: Boolean,
    missingItems: List<String>,
    hasRecordPermission: () -> Boolean,
    onRequestPermission: () -> Unit,
    onRelationshipChange: (String) -> Unit,
    onLaunchGallery: () -> Unit,
    onLaunchCamera: () -> Unit,
    onStartRecording: (RegisterViewModel.RecordingType) -> Boolean,
    onStopRecording: () -> Unit,
    onRevertQuestionToTts: () -> Unit,
    onApplyPhotoCrop: (scale: Float, offsetX: Float, offsetY: Float, boxSizePx: Int) -> Unit,
    onSave: (scale: Float, offsetX: Float, offsetY: Float, boxSizePx: Int) -> Unit,
    onShowError: (String) -> Unit
) {
    // Photo crop state lives here — reset when the photo changes
    var photoScale by remember { mutableFloatStateOf(1f) }
    var photoOffsetX by remember { mutableFloatStateOf(0f) }
    var photoOffsetY by remember { mutableFloatStateOf(0f) }
    var photoBoxSizePx by remember { mutableIntStateOf(0) }
    LaunchedEffect(photoUri) { photoScale = 1f; photoOffsetX = 0f; photoOffsetY = 0f }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            text = if (editingId != null) "편집 중 — 수정 후 저장하세요 (위 취소로 닫기)"
            else "새 가족 추가 중 (위 취소로 닫기)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }

    // 1. 관계 입력
    SectionCard(title = "1. 관계 입력", done = relationship.isNotBlank()) {
        Text(
            text = "아래에서 호칭을 고르거나 직접 입력하세요.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RelationshipChips(selected = relationship, onSelect = onRelationshipChange)
        OutlinedTextField(
            value = relationship,
            onValueChange = onRelationshipChange,
            label = { Text("직접 입력 (예: 엄마, 아빠, 할머니, 오빠)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )
    }

    // 2. 사진 등록
    SectionCard(
        title = "2. 사진 등록",
        done = photoUri != null,
        titleAction = if (photoUri != null) {
            {
                Button(
                    onClick = { onApplyPhotoCrop(photoScale, photoOffsetX, photoOffsetY, photoBoxSizePx) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("사진 확정")
                }
            }
        } else null
    ) {
        if (photoUri != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .onSizeChanged { photoBoxSizePx = it.width }
                    .pointerInput(photoUri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            photoScale = (photoScale * zoom).coerceIn(1f, 4f)
                            val maxOffset = photoBoxSizePx * (photoScale - 1f) / 2f
                            photoOffsetX = (photoOffsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                            photoOffsetY = (photoOffsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                        }
                    }
            ) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "선택된 사진",
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = photoScale; scaleY = photoScale
                        translationX = photoOffsetX; translationY = photoOffsetY
                    },
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = "두 손가락으로 확대·이동해 얼굴을 맞춘 뒤 위의 \"사진 확정\"을 누르세요.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLaunchGallery, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("갤러리")
            }
            Button(onClick = onLaunchCamera, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("카메라")
            }
        }
    }

    // 3. 질문 녹음
    SectionCard(title = "3. 질문 녹음", done = questionAudioPath != null) {
        Text(text = "예: \"해성아, 엄마 어딨어?\"", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RecordingButton(
            label = "질문 녹음",
            isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.QUESTION,
            hasRecording = questionAudioPath != null,
            audioPath = questionAudioPath,
            hasRecordPermission = hasRecordPermission,
            onRequestPermission = onRequestPermission,
            onHoldStart = { onStartRecording(RegisterViewModel.RecordingType.QUESTION) },
            onHoldEnd = onStopRecording
        )
        TextButton(onClick = onRevertQuestionToTts) {
            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("TTS(기계음)로 되돌리기")
        }
    }

    // 입력 상태 체크리스트
    SectionCard(title = "입력 상태") {
        StatusRow("관계", relationship.isNotBlank())
        StatusRow("사진", photoUri != null)
        StatusRow("질문 녹음", questionAudioPath != null)
        StatusRow("정답 반응 (공통)", correctAudioPath != null)
        StatusRow("오답 반응 (공통)", incorrectAudioPath != null)
    }

    // 등록/수정 버튼
    Button(
        onClick = {
            if (canSave) onSave(photoScale, photoOffsetX, photoOffsetY, photoBoxSizePx)
            else onShowError("아직 필요해요: " + missingItems.joinToString(", "))
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (canSave) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
        )
    ) {
        Icon(Icons.Default.Save, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        val saveLabel = if (editingId != null) "수정 저장" else "가족 등록"
        Text(
            text = if (canSave) saveLabel else "$saveLabel (미완료)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RegisteredMemberList(
    members: List<FamilyMember>,
    editingId: Int?,
    onEditMember: (FamilyMember) -> Unit,
    onDeleteMember: (FamilyMember) -> Unit
) {
    SectionCard(title = "등록된 가족 목록 (${members.size}명) · 탭하여 편집") {
        members.forEach { member ->
            val isBeingEdited = editingId == member.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isBeingEdited) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent
                    )
                    .clickable { onEditMember(member) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = File(member.photoPath),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = member.relationship, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF4CAF50))
                        Text(
                            text = if (isBeingEdited) "편집 중" else "탭하여 편집",
                            fontSize = 12.sp,
                            color = if (isBeingEdited) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
                        )
                    }
                }
                IconButton(onClick = { onDeleteMember(member) }) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (member != members.last()) HorizontalDivider()
        }
    }
}

// ── Reusable leaf composables ─────────────────────────────────────────────────

private val RELATIONSHIP_SUGGESTIONS = listOf(
    "엄마", "아빠", "할머니", "할아버지", "외할머니", "외할아버지",
    "누나", "형", "언니", "오빠", "여동생", "남동생",
    "이모", "고모", "삼촌", "외삼촌", "큰아빠", "작은아빠"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationshipChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        RELATIONSHIP_SUGGESTIONS.forEach { title ->
            FilterChip(selected = selected == title, onClick = { onSelect(title) }, label = { Text(title) })
        }
    }
}

private fun createCameraImageUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun StatusRow(label: String, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (done) FontWeight.Medium else FontWeight.Normal,
            color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    done: Boolean = false,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                if (done) Icon(Icons.Default.CheckCircle, contentDescription = "완료", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                if (titleAction != null) { Spacer(modifier = Modifier.weight(1f)); titleAction() }
            }
            content()
        }
    }
}

@Composable
fun RecordingButton(
    label: String,
    isCurrentlyRecording: Boolean,
    hasRecording: Boolean,
    audioPath: String?,
    hasRecordPermission: () -> Boolean,
    onRequestPermission: () -> Unit,
    onHoldStart: () -> Boolean,
    onHoldEnd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val containerColor = if (isCurrentlyRecording) Color(0xFFE53935) else MaterialTheme.colorScheme.primary

        Card(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val started = if (hasRecordPermission()) onHoldStart() else { onRequestPermission(); false }
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            if (started) onHoldEnd()
                        }
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(
                        text = when {
                            isCurrentlyRecording -> "녹음 중..."
                            hasRecording -> "다시 녹음 (누르는 동안)"
                            else -> "누르는 동안 녹음"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (hasRecording && audioPath != null && !isCurrentlyRecording) {
            IconButton(
                onClick = {
                    val mp = MediaPlayer()
                    try {
                        mp.setDataSource(audioPath)
                        mp.prepare()
                        mp.start()
                        mp.setOnCompletionListener { it.release() }
                    } catch (e: Exception) {
                        mp.release()
                    }
                }
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "재생", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        }
    }
}
