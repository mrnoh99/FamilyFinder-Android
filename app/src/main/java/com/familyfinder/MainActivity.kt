package com.familyfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familyfinder.audio.BgmController
import com.familyfinder.ui.GameScreen
import com.familyfinder.ui.GameViewModel
import com.familyfinder.ui.RegisterScreen
import com.familyfinder.ui.RegisterViewModel
import com.familyfinder.ui.theme.FamilyFinderTheme

class MainActivity : ComponentActivity() {

    // 숫자세기와 같은 방식의 배경 음악: 앱이 포그라운드일 때만 재생한다.
    private lateinit var bgmController: BgmController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bgmController = BgmController(applicationContext)
        enableEdgeToEdge()
        setContent {
            FamilyFinderTheme {
                FamilyFinderApp(bgmController = bgmController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bgmController.resumeBgm()
    }

    override fun onPause() {
        super.onPause()
        bgmController.pauseBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        bgmController.release()
    }
}

@Composable
fun FamilyFinderApp(bgmController: BgmController) {
    val navController = rememberNavController()
    val registerViewModel: RegisterViewModel = viewModel()
    val gameViewModel: GameViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "game"
    ) {
        composable("game") {
            GameScreen(
                viewModel = gameViewModel,
                onOpenRegister = {
                    navController.navigate("register") { launchSingleTop = true }
                }
            )
        }
        composable("register") {
            RegisterScreen(
                viewModel = registerViewModel,
                bgmController = bgmController,
                onBack = {
                    // 설정(가족 등록)에서 나오면 진행 중이던 문제를 비우고,
                    // 게임 화면의 자동 시작(1초 뒤)으로 새 질문을 다시 시작한다.
                    gameViewModel.resetToStart()
                    navController.popBackStack()
                }
            )
        }
    }
}
