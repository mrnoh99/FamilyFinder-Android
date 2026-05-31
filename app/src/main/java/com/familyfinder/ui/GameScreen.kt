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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyfinder.data.FamilyMember
import java.io.File

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val currentSet by viewModel.currentSet.collectAsStateWithLifecycle()
    val targetMember by viewModel.targetMember.collectAsStateWithLifecycle()
    val gameResult by viewModel.gameResult.collectAsStateWithLifecycle()
    val memberCount by viewModel.memberCount.collectAsStateWithLifecycle()
    val selectedMemberId by viewModel.selectedMemberId.collectAsStateWithLifecycle()

    LaunchedEffect(memberCount) {
        if (memberCount >= 4 && currentSet.isEmpty()) {
            viewModel.startGame()
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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
        when {
            memberCount < 4 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GameTitle()
                    Spacer(modifier = Modifier.height(24.dp))
                    InsufficientMembersCard(memberCount)
                }
            }

            isLandscape -> {
                // 가로 모드: 왼쪽 컨트롤 + 오른쪽 2x2 그리드
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GameTitle()
                        AnimatedVisibility(
                            visible = gameResult != GameResult.NONE,
                            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            FeedbackCard(result = gameResult)
                        }
                        SoundButton(onClick = { viewModel.playQuestion() })
                        AnimatedVisibility(
                            visible = gameResult != GameResult.NONE,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            NextButton(onClick = { viewModel.startGame() })
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentSet.size == 4 && targetMember != null) {
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
            }

            else -> {
                // 세로 모드: 위에서 아래로 컨트롤 + 2x2 그리드
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GameTitle()
                    AnimatedVisibility(
                        visible = gameResult != GameResult.NONE,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        FeedbackCard(result = gameResult)
                    }
                    SoundButton(onClick = { viewModel.playQuestion() })
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentSet.size == 4 && targetMember != null) {
                            SquarePhotoGrid(
                                members = currentSet,
                                selectedMemberId = selectedMemberId,
                                targetMemberId = targetMember!!.id,
                                gameResult = gameResult,
                                onSelectMember = viewModel::selectMember
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = gameResult != GameResult.NONE,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        NextButton(onClick = { viewModel.startGame() })
                    }
                }
            }
        }
    }
}

@Composable
private fun GameTitle() {
    Text(
        text = "가족 찾기 🏠",
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SoundButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = RoundedCornerShape(31.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary
        )
    ) {
        Icon(
            Icons.Default.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(30.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "소리 듣기",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NextButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Icon(Icons.Default.NavigateNext, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "다음 문제",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InsufficientMembersCard(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.FamilyRestroom,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "가족을 4명 이상\n등록해 주세요!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "현재 ${count}명 등록됨 (${4 - count}명 더 필요)",
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
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
 * 사용 가능한 공간 안에서 2x2 그리드를 항상 정사각형으로 맞춘다. 세로/가로 어느 방향이든
 * min(가로, 세로) 변을 한 변으로 하는 정사각 영역에 그려 카드가 찌그러지지 않는다.
 */
@Composable
fun SquarePhotoGrid(
    members: List<FamilyMember>,
    selectedMemberId: Int?,
    targetMemberId: Int,
    gameResult: GameResult,
    onSelectMember: (FamilyMember) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        PhotoGrid(
            members = members,
            selectedMemberId = selectedMemberId,
            targetMemberId = targetMemberId,
            gameResult = gameResult,
            onSelectMember = onSelectMember,
            modifier = Modifier.size(side)
        )
    }
}

@Composable
fun PhotoGrid(
    members: List<FamilyMember>,
    selectedMemberId: Int?,
    targetMemberId: Int,
    gameResult: GameResult,
    onSelectMember: (FamilyMember) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        members.chunked(2).forEach { rowMembers ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowMembers.forEach { member ->
                    PhotoCard(
                        member = member,
                        isSelected = selectedMemberId == member.id,
                        isTarget = targetMemberId == member.id,
                        gameResult = gameResult,
                        onClick = { onSelectMember(member) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
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

    Card(
        onClick = onClick,
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

            // 관계 라벨 (하단)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = member.relationship,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
