package com.atharok.screentime

import android.app.Application
import com.atharok.screentime.common.injections.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ScreenTimeApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ScreenTimeApplication)
            modules(appModules)
        }
    }
}