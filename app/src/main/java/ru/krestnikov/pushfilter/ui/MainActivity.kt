package ru.krestnikov.pushfilter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.krestnikov.pushfilter.ui.theme.PushFilterTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PushFilterTheme {
                SimpleFilterApp()
            }
        }
    }
}
