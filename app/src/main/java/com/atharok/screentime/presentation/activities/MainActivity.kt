package com.atharok.screentime.presentation.activities

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.TouchTracker
import com.atharok.screentime.ui.app.ComposeRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("MainActivity", "onCreate")
        TouchTracker.attach(this)
        enableEdgeToEdge()
        setContent {
            ComposeRoot()
        }
    }

    /** Single entry point for all touches: coordinates, gesture, screen zone, page and latency. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        TouchTracker.dispatch(ev)
        return super.dispatchTouchEvent(ev)
    }
}
