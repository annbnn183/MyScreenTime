package com.atharok.screentime.common.extensions

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

fun PackageManager.getApplicationName(packageName: String): String? {
    return try {
        val applicationInfo = this.getApplicationInfo(packageName, 0)
        this.getApplicationLabel(applicationInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}

fun PackageManager.getApplicationIconSafety(packageName: String): Drawable? {
    return try {
        this.getApplicationIcon(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}

fun PackageManager.getPackageUid(packageName: String): Int? {
    return try {
        val applicationInfo = this.getApplicationInfo(packageName, 0)
        applicationInfo.uid
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}