package moe.fuqiuluo.mamu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import moe.fuqiuluo.mamu.ui.screen.MainScreen
import moe.fuqiuluo.mamu.ui.theme.MXTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MXTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                MainScreen(windowSizeClass = windowSizeClass)
            }
        }
    }
}
