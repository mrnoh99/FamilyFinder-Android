package com.familyfinder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyfinder.R
import com.familyfinder.data.FamilyMember
import java.io.File

/** 가족찾기 아이콘과 어울리는 따뜻한 주황 강조색 (NumberCount 앱의 AppOrange와 동일 계열). */
private val SettingsAccent = Color(0xFFE08600)

/** 시스템 진동기를 가져온다(없으면 null). */
private fun obtainVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

/**
 * 정답/오답에 따라 서로 다른 진동 패턴을 재생한다.
 * - 정답: 가볍고 경쾌한 두 번의 짧은 진동
 * - 오답: 묵직한 한 번의 긴 진동
 */
private fun vibrateForResult(context: Context, result: GameResult) {
    val vibrator = obtainVibrator(context)?.takeIf { it.hasVibrator() } ?: return
    val effect = when (result) {
        GameResult.CORRECT -> VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1)
        GameResult.INCORRECT -> VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE)
        GameResult.NONE -> return
    }
    vibrator.vibrate(effect)
}

/** NumberCount 앱의 SettingsGear 스타일: 원형 + 옅은 강조색 배경의 설정 기어 버튼. */
@Composable
private fun SettingsGear(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(SettingsAccent.copy(alpha = 0.12f))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "가족 등록",
            tint = SettingsAccent,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel, onOpenRegister: () -> Unit = {}) {
    val currentSet by viewModel.currentSet.collectAsStateWithLifecycle()
    val targetMember by viewModel.targetMember.collectAsStateWithLifecycle()
    val gameResult by viewModel.gameResult.collectAsStateWithLifecycle()
    val memberCount by viewModel.memberCount.collectAsStateWithLifecycle()
    val selectedMemberId by viewModel.selectedMemberId.collectAsStateWithLifecycle()
    val resultPlaying by viewModel.resultPlaying.collectAsStateWithLifecycle()

    val answered = gameResult != GameResult.NONE

    // 정답/오답이 확정되는 순간 결과에 맞는 진동을 한 번 재생한다.
    val context = LocalContext.current
    LaunchedEffect(gameResult) {
        if (gameResult != GameResult.NONE) {
            vibrateForResult(context, gameResult)
        }
    }

    // 가족이 1명 이상 준비되고 진행 중인 문제가 없으면 자동으로 시작한다.
    // (앱 첫 실행 시 씨딩이 끝난 뒤, 또는 가족 등록 화면에서 돌아왔을 때 자동 시작)
    // 사진 그리드는 즉시 보여주고, 질문 소리만 1초 뒤에 재생한다(지연은 startGame 내부).
    LaunchedEffect(memberCount, currentSet.isEmpty()) {
        if (memberCount >= 1 && currentSet.isEmpty()) {
            viewModel.startGame()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        // 전체를 safeDrawingPadding 안에 두어 상태바·내비게이션 바·노치를 모두 피한다.
        // 설정 기어를 떠있는(overlay) 요소가 아니라 상단 바의 일반 레이아웃 요소로 배치하므로
        // 가로/세로 어느 방향에서도 측면 내비게이션 바에 가려지지 않는다.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // 상단 바: 오른쪽 끝 설정 기어 → 가족 등록 화면으로 이동
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsGear(onClick = onOpenRegister)
            }

            // 본문: 문제가 준비되면 항상 4칸 사진 그리드를 가운데 배치(빈 칸 포함).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentSet.isNotEmpty() && targetMember != null) {
                    SquarePhotoGrid(
                        members = currentSet,
                        selectedMemberId = selectedMemberId,
                        targetMemberId = targetMember!!.id,
                        gameResult = gameResult,
                        onSelectMember = viewModel::selectMember
                    )
                }
            }
        }

        // 정답/오답 결과 화면 (해당 이미지가 있으면 전체 오버레이로 표시)
        // 음성이 재생되는 동안(resultPlaying)은 터치를 막고, 끝나면 다음 버튼을 띄운다.
        if (answered) {
            ResultOverlay(
                result = gameResult,
                interactive = !resultPlaying,
                onNext = { viewModel.startGame() }
            )
        }
    }
}

/**
 * 정답이면 "praise", 오답이면 "wrong" 드로어블을 전체 화면 오버레이로 보여준다.
 * 해당 이미지가 없으면 아무것도 그리지 않아 기존 피드백 카드가 사용된다.
 * 화면을 누르면 다음 문제로 진행.
 */
@Composable
private fun ResultOverlay(result: GameResult, interactive: Boolean, onNext: () -> Unit) {
    val resId = when (result) {
        GameResult.CORRECT -> R.drawable.praise
        GameResult.INCORRECT -> R.drawable.wrong
        else -> return
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 아래 사진이 비치는 옅은 스크림 + 반투명 글래스 패널.
    // 음성이 끝나기 전(interactive=false)에는 터치를 막는다.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.50f))
            // 항상 터치를 소비해 뒤쪽 요소가 눌리지 않게 하고, 음성이 끝난 뒤에만 다음으로 진행
            .pointerInput(interactive) {
                detectTapGestures { if (interactive) onNext() }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            // 가로 모드: 이미지(좌) + 텍스트·버튼(우) 을 fillMaxHeight로 제한해 화면 밖으로 나가지 않게 한다.
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.90f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 왼쪽: 이미지
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (result == GameResult.CORRECT) {
                        Image(
                            painter = painterResource(resId),
                            contentDescription = "정답",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(resId),
                            contentDescription = "오답",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxHeight(0.85f)
                        )
                    }
                }
                // 오른쪽: 텍스트 + 다음 버튼
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (interactive) {
                        NextButton(onClick = onNext)
                    }
                }
            }
        } else {
            // 세로 모드: 정답/오답 그림(정답 feedback)은 가운데에 두고,
            // 파란(다음) 버튼은 "그림 아래쪽 ~ 내비게이션 바" 사이의 정확히 1/2 지점에 놓는다.
            // (위쪽 Spacer와 아래쪽 Box의 weight를 1:1로 두면, 그림은 가운데 정렬되고
            //  아래 영역의 한가운데에 버튼이 오므로 그림과 내비게이션 바 사이 1/2 위치가 된다.)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                if (result == GameResult.CORRECT) {
                    // "참 잘했어요!" 그림은 자체 배경(크림색)이 있어 흰 테두리 없이 꽉 차게 보여준다.
                    Image(
                        painter = painterResource(resId),
                        contentDescription = "정답",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                    )
                } else {
                    // 오답 그림도 자체 배경(크림색)이 있어 흰 테두리 없이 꽉 차게 보여준다.
                    Image(
                        painter = painterResource(resId),
                        contentDescription = "오답",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                    )
                }

                // 그림 아래 ~ 내비게이션 바 사이 영역. 그 한가운데(1/2)에 파란 버튼을 둔다.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (interactive) {
                        NextButton(onClick = onNext)
                    }
                }
            }
        }
    }
}

/** 올려주신 파란 버튼 이미지(btn_blue) 위에 글자를 얹은 어린이용 다음 버튼. */
@Composable
private fun RoundGlossyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(160.dp)
            // 포커스/터치 시 ripple·하이라이트(둥근 버튼 뒤 사각형 그림자)가 생기지 않도록 indication 제거.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.btn_blue),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun NextButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RoundGlossyButton(label = "", onClick = onClick, modifier = modifier)
}

@Composable
fun FeedbackCard(result: GameResult) {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce_y"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (result == GameResult.CORRECT)
                Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (result == GameResult.CORRECT) "🎉" else "😅",
                fontSize = 40.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (result == GameResult.CORRECT) "잘 했어요!" else "아닌데~",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (result == GameResult.CORRECT) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Text(
                    text = if (result == GameResult.CORRECT) "정답이에요! 👍" else "다시 해봐요! 💪",
                    fontSize = 15.sp,
                    color = if (result == GameResult.CORRECT) Color(0xFF388E3C) else Color(0xFFD32F2F)
                )
            }
        }
    }
}

/**
 * 사용 가능한 공간 안에서 2x2 그리드를 항상 정사각형으로 맞춘다. 세로/가로(태블릿 포함)
 * 어느 방향이든 min(가로, 세로) 변을 한 변으로 하는 정사각 영역을 잡은 뒤, 각 셀 크기를
 * (변 - 간격) / 2 로 **직접 계산해 size()로 고정**한다. weight + aspectRatio 조합은 기기/제약
 * 조건에 따라 셀이 1:1로 안 떨어지는 경우가 있어, 명시적 크기로 모든 기기에서 1:1을 보장한다.
 */
@Composable
fun SquarePhotoGrid(
    members: List<FamilyMember?>,
    selectedMemberId: Int?,
    targetMemberId: Int,
    gameResult: GameResult,
    onSelectMember: (FamilyMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = 10.dp
    // 패널은 항상 4칸. 빈 칸(null)은 빈 패널로 표시한다.
    val count = members.size.coerceIn(1, 4)
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val w = maxWidth
        val h = maxHeight
        // 가능한 열 수(1..count) 중에서 정사각형 셀이 가장 커지는 배치를 고른다.
        // 가로 모드처럼 폭이 넓고 높이가 낮으면 1행(예: 1x4)이 더 큰 사진을 주고,
        // 세로 모드에서는 2x2가 더 큰 사진을 준다 — 자동으로 더 큰 쪽을 선택한다.
        var cols = 1
        var cell = 0.dp
        for (c in 1..count) {
            val r = (count + c - 1) / c
            val candidate = minOf((w - gap * (c - 1)) / c, (h - gap * (r - 1)) / r)
            if (candidate > cell) {
                cell = candidate
                cols = c
            }
        }
        cell = cell.coerceAtLeast(0.dp)
        val gridWidth = cell * cols + gap * (cols - 1)
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            members.chunked(cols).forEach { rowMembers ->
                Row(
                    modifier = Modifier.width(gridWidth),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
                ) {
                    rowMembers.forEach { member ->
                        if (member != null) {
                            PhotoCard(
                                member = member,
                                isSelected = selectedMemberId == member.id,
                                isTarget = targetMemberId == member.id,
                                gameResult = gameResult,
                                onClick = { onSelectMember(member) },
                                modifier = Modifier.size(cell)
                            )
                        } else {
                            BlankPanel(modifier = Modifier.size(cell))
                        }
                    }
                }
            }
        }
    }
}

/** 등록된 가족이 4명보다 적을 때 그리드의 남는 칸을 채우는 빈 패널(선택 불가). */
@Composable
private fun BlankPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x11000000))
    )
}

@Composable
fun PhotoCard(
    member: FamilyMember,
    isSelected: Boolean,
    isTarget: Boolean,
    gameResult: GameResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        gameResult != GameResult.NONE && isSelected && isTarget -> Color(0xFF4CAF50)
        gameResult != GameResult.NONE && isSelected && !isTarget -> Color.Red
        gameResult != GameResult.NONE && isTarget -> Color(0xFF4CAF50)
        else -> Color.Transparent
    }
    val borderWidth = if (gameResult != GameResult.NONE && (isSelected || isTarget)) 4.dp else 0.dp

    val cardScale by animateFloatAsState(
        targetValue = if (isSelected && gameResult == GameResult.INCORRECT) 0.93f else 1f,
        label = "card_scale"
    )

    val haptic = LocalHapticFeedback.current
    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .aspectRatio(1f)
            .scale(cardScale)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 10.dp else 4.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(member.photoPath),
                contentDescription = member.relationship,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 결과 오버레이
            if (gameResult != GameResult.NONE) {
                when {
                    isSelected && isTarget -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x554CAF50))
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(72.dp),
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    }
                    isSelected && !isTarget -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x55FF0000))
                        ) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(72.dp),
                                tint = Color.Red
                            )
                        }
                    }
                    !isSelected && isTarget && gameResult == GameResult.INCORRECT -> {
                        // 오답 선택 시 정답 위치 표시
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x334CAF50))
                        ) {
                            Text(
                                text = "정답!",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}
