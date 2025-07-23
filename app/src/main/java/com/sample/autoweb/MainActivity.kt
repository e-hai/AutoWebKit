package com.sample.autoweb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sample.autoweb.ui.screen.WebViewScreen
import com.sample.autoweb.ui.theme.AutoWebKitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoWebKitTheme {
                WebViewScreen("https://animalface022506.minigame.vip")
            }
        }
    }
}

