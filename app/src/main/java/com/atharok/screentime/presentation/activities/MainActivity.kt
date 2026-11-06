package com.atharok.screentime.presentation.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.ui.app.ComposeRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("MainActivity", "onCreate")
        enableEdgeToEdge()
        setContent {
            ComposeRoot()
        }
    }
}