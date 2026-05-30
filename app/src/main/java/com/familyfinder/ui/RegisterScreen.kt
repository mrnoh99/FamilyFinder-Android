package com.familyfinder.ui

import android.Manifest
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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

    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> viewModel.setPhotoUri(uri) }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar("가족이 등록되었습니다! ✓")
            viewModel.clearSaveSuccess()
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "가족 등록",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "등록된 가족: ${allMembers.size}명 ${if (allMembers.size < 4) "(게임을 시작하려면 4명 이상 필요)" else "✓ 게임 가능!"}",
                fontSize = 14.sp,
                color = if (allMembers.size >= 4) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )

            // 1. 관계 입력
            SectionCard(title = "1. 관계 입력") {
                OutlinedTextField(
                    value = relationship,
                    onValueChange = viewModel::setRelationship,
                    label = { Text("관계 (예: 엄마, 아빠, 할머니, 오빠)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
            }

            // 2. 사진 등록
            SectionCard(title = "2. 사진 등록") {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "선택된 사진",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Button(
                    onClick = { photoLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (photoUri == null) "사진 선택" else "사진 변경")
                }
            }

            // 3. 질문 녹음
            SectionCard(title = "3. 질문 녹음") {
                Text(
                    text = "예: \"해성아, 엄마 어딨어?\"",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecordingButton(
                    label = "질문 녹음",
                    isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.QUESTION,
                    hasRecording = questionAudioPath != null,
                    audioPath = questionAudioPath,
                    onStartRecording = { viewModel.startRecording(RegisterViewModel.RecordingType.QUESTION) },
                    onStopRecording = viewModel::stopRecording
                )
            }

            // 4. 정답 반응 녹음
            SectionCard(title = "4. 정답 반응 녹음") {
                Text(
                    text = "예: \"잘 했어요! 맞아요!\"",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecordingButton(
                    label = "정답 반응 녹음",
                    isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.CORRECT,
                    hasRecording = correctAudioPath != null,
                    audioPath = correctAudioPath,
                    onStartRecording = { viewModel.startRecording(RegisterViewModel.RecordingType.CORRECT) },
                    onStopRecording = viewModel::stopRecording
                )
            }

            // 5. 오답 반응 녹음
            SectionCard(title = "5. 오답 반응 녹음") {
                Text(
                    text = "예: \"아닌데~ 다시 봐봐!\"",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RecordingButton(
                    label = "오답 반응 녹음",
                    isCurrentlyRecording = isRecording && currentRecordingType == RegisterViewModel.RecordingType.INCORRECT,
                    hasRecording = incorrectAudioPath != null,
                    audioPath = incorrectAudioPath,
                    onStartRecording = { viewModel.startRecording(RegisterViewModel.RecordingType.INCORRECT) },
                    onStopRecording = viewModel::stopRecording
                )
            }

            // 저장 버튼
            Button(
                onClick = { viewModel.saveMember() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = viewModel.canSave(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("가족 등록 저장", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // 등록된 가족 목록
            if (allMembers.isNotEmpty()) {
                SectionCard(title = "등록된 가족 목록 (${allMembers.size}명)") {
                    allMembers.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
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
                                    fontSize = 16.sp,
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
                                        text = "녹음 완료",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
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

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
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
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledIconButton(
            onClick = { if (isCurrentlyRecording) onStopRecording() else onStartRecording() },
            modifier = Modifier
                .size(60.dp)
                .then(if (isCurrentlyRecording) Modifier.scale(scale) else Modifier),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isCurrentlyRecording) Color.Red else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isCurrentlyRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = label,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    isCurrentlyRecording -> "녹음 중... (탭하여 중지)"
                    hasRecording -> "녹음 완료 ✓"
                    else -> "탭하여 녹음 시작"
                },
                fontSize = 14.sp,
                fontWeight = if (hasRecording) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    isCurrentlyRecording -> Color.Red
                    hasRecording -> Color(0xFF4CAF50)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (hasRecording && audioPath != null && !isCurrentlyRecording) {
            IconButton(
                onClick = {
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
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "재생",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
