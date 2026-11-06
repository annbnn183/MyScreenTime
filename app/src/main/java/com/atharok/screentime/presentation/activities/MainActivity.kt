package com.atharok.screentime.presentation.activities

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.common.utils.DataBackupManager
import com.atharok.screentime.common.utils.TouchTracker
import com.atharok.screentime.data.dataStore.SettingsDataStore
import com.atharok.screentime.ui.app.ComposeRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    /**
     * 每次回到前台：
     *   1) 若上次「恢复」在中途被杀死/断电，先回滚到恢复前快照（保证不留下只恢复一半的状态）。
     *   2) 仅在用户开启「自动本地备份」时，每天写一份到应用私有外部目录（一天一份，已存在跳过）。
     *      换手机迁移请用设置里的「备份数据」显式导出到应用外部。
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { DataBackupManager.recoverIfInterrupted(this@MainActivity) }
            val autoBackupEnabled = runCatching {
                SettingsDataStore(this@MainActivity).autoBackupEnabledFlow.first()
            }.getOrDefault(false)
            if (autoBackupEnabled) {
                runCatching { DataBackupManager.writeAutoBackupIfNeeded(this@MainActivity) }
            }
        }
    }

    /** Single entry point for all touches: coordinates, gesture, screen zone, page and latency. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        TouchTracker.dispatch(ev)
        return super.dispatchTouchEvent(ev)
    }
}
