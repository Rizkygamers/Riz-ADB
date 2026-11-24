package com.riz.adbexec

import android.app.Application
import android.content.Context
import org.lsposed.hiddenapibypass.HiddenApiBypass
import io.github.muntashirakon.adb.PRNGFixes

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PRNGFixes.apply()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        HiddenApiBypass.addHiddenApiExemptions("L")
    }
}
