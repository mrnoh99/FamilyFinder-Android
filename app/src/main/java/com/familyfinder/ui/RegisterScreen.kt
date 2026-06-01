package com.familyfinder.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.familyfinder.R
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
    val questionRecorded by viewModel.questionRecorded.collectAsStateWithLifecycle()
    val correctRecorded by viewModel.correctRecorded.collectAsStateWithLifecycle()
    val incorrectRecorded by viewModel.incorrectRecorded.collectAsStateWithLifecycle()
    val allMembers by viewModel.allMembers.collectAsStateWithLifecycle(emptyList())
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()
    val recordingError by viewModel.recordingError.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // 편집 시작 시 폼이 보이도록 맨 위로 스크롤
    LaunchedEffect(editingId) {
        if (editingId != null) scrollState.animateScrollTo(0)
    }

    // "추가"를 눌렀을 때만 입력 폼을 표시 (편집 중에도 표시)
    var showForm by rememberSaveable { mutableStateOf(false) }

    // 사진 크롭(확대/이동) 상태 — 사진이 바뀌면 초기화
    var photoScale by remember { mutableFloatStateOf(1f) }
    var photoOffsetX by remember { mutableFloatStateOf(0f) }
    var photoOffsetY by remember { mutableFloatStateOf(0f) }
    var photoBoxSizePx by remember { mutableIntStateOf(0) }
    LaunchedEffect(photoUri) {
        photoScale = 1f
        photoOffsetX = 0f
        photoOffsetY = 0f
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val hasRecordPermission: () -> Boolean = {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    val onRequestPermission: () -> Unit = {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // 갤러리(시스템 포토 피커): 선택 후 자동으로 이 화면으로 복귀
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) viewModel.setPhotoUri(uri) }

    // 카메라 촬영: 촬영한 사진을 임시 파일에 저장 후 이 화면으로 복귀
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean -> if (success) cameraUri?.let { viewModel.setPhotoUri(it) } }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

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
            // ── 공통 반응 음성 패널 (가족 등록 위 · 한 번만 녹음하면 전체 공통) ──
            SectionCard(
                title = "정답·오답 칭찬 녹음"
            ) {
                ReactionAudioPanel(
                    label = "정답 칭찬",
                    example = "잘 했어요! 맞아요!",
                    isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.CORRECT,
                    hasRecording = correctAudioPath != null,
                    isRecorded = correctRecorded,
                    audioPath = correctAudioPath,
                    signalResId = R.raw.signal_correct,
                    hasRecordPermission = hasRecordPermission,
                    onRequestPermission = onRequestPermission,
                    onHoldStart = { viewModel.startRecording(RegisterViewModel.RecordingType.CORRECT) },
                    onHoldEnd = viewModel::stopRecording,
                    onRevertToTts = { viewModel.revertCorrectToTts() }
                )

                Spacer(modifier = Modifier.height(4.dp))

                ReactionAudioPanel(
                    label = "오답 반응",
                    example = "아닌데~ 다시 봐봐!",
                    isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.INCORRECT,
                    hasRecording = incorrectAudioPath != null,
                    isRecorded = incorrectRecorded,
                    audioPath = incorrectAudioPath,
                    signalResId = R.raw.signal_wrong,
                    hasRecordPermission = hasRecordPermission,
                    onRequestPermission = onRequestPermission,
                    onHoldStart = { viewModel.startRecording(RegisterViewModel.RecordingType.INCORRECT) },
                    onHoldEnd = viewModel::stopRecording,
                    onRevertToTts = { viewModel.revertIncorrectToTts() }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            val missing = buildList {
                if (relationship.isBlank()) add("관계")
                if (photoUri == null) add("사진")
                if (questionAudioPath == null) add("질문 녹음")
                if (correctAudioPath == null) add("정답 반응(공통)")
                if (incorrectAudioPath == null) add("오답 반응(공통)")
            }
            val canSave = missing.isEmpty()

            // 제목 + "추가" 버튼 (누르면 입력 폼이 열린다)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (editingId != null) "가족 정보 수정" else "가족 등록",
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                val formOpen = showForm || editingId != null
                Button(
                    onClick = {
                        viewModel.cancelEditing()
                        showForm = !formOpen
                    },
                    colors = if (formOpen) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Icon(
                        if (formOpen) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (formOpen) "취소" else "추가")
                }
            }

            Text(
                text = "등록된 가족: ${allMembers.size}명 ${if (allMembers.size < 4) "(게임을 시작하려면 4명 이상 필요)" else "✓ 게임 가능!"}",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                color = if (allMembers.size >= 4) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )

            // "추가"를 눌렀거나 가족을 선택(편집)했을 때만 입력 폼을 보여준다.
            if (showForm || editingId != null) {

            // 1. 관계 입력 — 자주 쓰는 호칭을 칩으로 고르거나 직접 입력
            SectionCard(title = "1. 관계 입력", done = relationship.isNotBlank()) {
                Text(
                    text = "아래에서 호칭을 고르거나 직접 입력하세요.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RelationshipChips(
                    selected = relationship,
                    onSelect = viewModel::setRelationship
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = viewModel::setRelationship,
                    label = { Text("직접 입력 (예: 엄마, 아빠, 할머니, 오빠)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
            }

            // 2. 사진 등록 — 두 손가락으로 확대/이동해 정사각형으로 맞춘 뒤 "사진 확정"
            SectionCard(
                title = "2. 사진 등록",
                done = photoUri != null,
                titleAction = if (photoUri != null) {
                    {
                        Button(
                            onClick = {
                                viewModel.applyPhotoCrop(
                                    photoScale = photoScale,
                                    photoOffsetX = photoOffsetX,
                                    photoOffsetY = photoOffsetY,
                                    photoBoxSizePx = photoBoxSizePx
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            )
                        ) {
                            Icon(
                                Icons.Default.Crop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
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
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = photoScale
                                    scaleY = photoScale
                                    translationX = photoOffsetX
                                    translationY = photoOffsetY
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = "두 손가락으로 확대·이동해 얼굴을 맞춘 뒤 위의 \"사진 확정\"을 누르세요.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("갤러리")
                    }
                    Button(
                        onClick = {
                            val uri = createCameraImageUri(context)
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("카메라")
                    }
                }
            }

            // 3. 질문 녹음
            SectionCard(title = "3. 질문 녹음", done = questionAudioPath != null) {
                ReactionAudioPanel(
                    label = "질문 녹음",
                    example = "해준아, 엄마 어딨어?",
                    isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.QUESTION,
                    hasRecording = questionAudioPath != null,
                    isRecorded = questionRecorded,
                    audioPath = questionAudioPath,
                    allowTts = false,
                    hasRecordPermission = hasRecordPermission,
                    onRequestPermission = onRequestPermission,
                    onHoldStart = { viewModel.startRecording(RegisterViewModel.RecordingType.QUESTION) },
                    onHoldEnd = viewModel::stopRecording,
                    onRevertToTts = { viewModel.revertQuestionToTts() }
                )
            }

            // 하단 등록 버튼 — 전체 내용을 확정해 가족을 등록/수정
            Button(
                onClick = {
                    if (canSave) {
                        viewModel.saveMember(
                            photoScale = photoScale,
                            photoOffsetX = photoOffsetX,
                            photoOffsetY = photoOffsetY,
                            photoBoxSizePx = photoBoxSizePx
                        )
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "아직 필요해요: " + missing.joinToString(", ")
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 10.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSave) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                val saveLabel = if (editingId != null) "수정 저장" else "가족 등록"
                Column {
                    Text(
                        text = saveLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!canSave) {
                        Text(
                            text = "미완료: " + missing.joinToString(", "),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            } // 입력 폼 끝

            // 등록된 가족 목록 — 탭하면 편집
            if (allMembers.isNotEmpty()) {
                SectionCard(title = "등록된 가족 목록 (${allMembers.size}명) · 탭하여 편집") {
                    allMembers.forEach { member ->
                        val isBeingEdited = editingId == member.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isBeingEdited) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent
                                )
                                .clickable { viewModel.startEditing(member) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = File(member.photoPath),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.relationship,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = if (isBeingEdited) "편집 중" else "탭하여 편집",
                                        fontSize = 13.sp,
                                        color = if (isBeingEdited) MaterialTheme.colorScheme.primary
                                        else Color(0xFF4CAF50)
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteMember(member) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "삭제",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (member != allMembers.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** 가족 간 자주 쓰는 호칭 목록 (한정적이므로 선택지로 제시). */
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
            FilterChip(
                selected = selected == title,
                onClick = { onSelect(title) },
                label = { Text(title) }
            )
        }
    }
}

/** 카메라 촬영 결과를 저장할 임시 파일의 FileProvider Uri를 만든다. */
private fun createCameraImageUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun SectionCard(
    title: String,
    done: Boolean = false,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (done) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "완료",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (titleAction != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    titleAction()
                }
            }
            content()
        }
    }
}

/**
 * Reaction audio panel (정답/오답 반응):
 * - No recording → status "없음 (TTS)" + full-width hold-to-record button
 * - Has recording → status "저장됨" + [듣기] [TTS로 돌아가기] row + hold-to-record button below
 */
@Composable
private fun ReactionAudioPanel(
    label: String,
    example: String,
    isCurrentlyRecording: Boolean,
    hasRecording: Boolean,
    isRecorded: Boolean,
    audioPath: String?,
    signalResId: Int? = null,
    allowTts: Boolean = true,
    hasRecordPermission: () -> Boolean,
    onRequestPermission: () -> Unit,
    onHoldStart: () -> Boolean,
    onHoldEnd: () -> Unit,
    onRevertToTts: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$label  ·  예: \"$example\"",
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold
        )

        if (!hasRecording) {
            Text(
                text = if (allowTts) "없음 (TTS)" else "없음 (직접 녹음 필요)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Hold-to-record button (always visible)
        val recordBg = if (isCurrentlyRecording) Color(0xFFE53935) else primary.copy(alpha = 0.12f)
        val recordTextColor = if (isCurrentlyRecording) Color.White else primary

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val started = if (hasRecordPermission()) {
                            onHoldStart()
                        } else {
                            onRequestPermission()
                            false
                        }
                        try {
                            // 스크롤 컨테이너 안에서도 '누르는 동안' 제스처가 취소되지 않도록
                            // 손가락 이동/떼기 이벤트를 직접 소비하며 손을 뗄 때까지 기다린다.
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    change?.consume()
                                    break
                                }
                                change.consume()
                            }
                        } finally {
                            if (started) onHoldEnd()
                        }
                    }
                },
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(containerColor = recordBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCurrentlyRecording) "녹음 중..." else "누르는 동안 녹음",
                    color = recordTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 듣기·TTS로 돌아가기 버튼은 사용자가 직접 녹음한 음성이 있을 때만 보여준다.
        if (isRecorded && audioPath != null && !isCurrentlyRecording) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // 실제 게임과 동일하게 신호음(딩동/삑)을 먼저 들려준 뒤 반응 음성을 재생한다.
                        val playReaction = {
                            try {
                                MediaPlayer().apply {
                                    setDataSource(audioPath)
                                    prepare()
                                    start()
                                    setOnCompletionListener { release() }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (signalResId != null) {
                            try {
                                val signal = MediaPlayer.create(context, signalResId)
                                if (signal != null) {
                                    signal.setOnCompletionListener {
                                        it.release()
                                        playReaction()
                                    }
                                    signal.start()
                                } else {
                                    playReaction()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                playReaction()
                            }
                        } else {
                            playReaction()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("듣기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                if (allowTts) {
                    Button(
                        onClick = onRevertToTts,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF616161),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("TTS로 돌아가기", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}


