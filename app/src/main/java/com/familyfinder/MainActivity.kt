package com.familyfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.familyfinder.ui.GameScreen
import com.familyfinder.ui.GameViewModel
import com.familyfinder.ui.RegisterScreen
import com.familyfinder.ui.RegisterViewModel
import com.familyfinder.ui.theme.FamilyFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyFinderTheme {
                FamilyFinderApp()
            }
        }
    }
}

@Composable
fun FamilyFinderApp() {
    val navController = rememberNavController()
    val registerViewModel: RegisterViewModel = viewModel()
    val gameViewModel: GameViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Games, contentDescription = null) },
                    label = { Text("게임") },
                    selected = currentRoute == "game",
                    onClick = {
                        navController.navigate("game") { launchSingleTop = true }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    label = { Text("가족 등록") },
                    selected = currentRoute == "register",
                    onClick = {
                        navController.navigate("register") { launchSingleTop = true }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "game",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("game") {
                GameScreen(viewModel = gameViewModel)
            }
            composable("register") {
                RegisterScreen(viewModel = registerViewModel)
            }
        }
    }
}
