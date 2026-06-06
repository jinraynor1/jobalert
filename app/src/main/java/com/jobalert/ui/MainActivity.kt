package com.jobalert.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jobalert.JobAlertApp
import com.jobalert.ui.theme.JobAlertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = applicationContext as JobAlertApp
            val darkMode by app.darkMode.collectAsState()
            JobAlertTheme(darkTheme = darkMode) {
                AppNavigation()
            }
        }
    }
}
