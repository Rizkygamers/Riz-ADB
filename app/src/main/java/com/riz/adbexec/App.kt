package com.riz.adbexec

import android.app.Application
import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(filesDir, "crash.txt").writeText(Log.getStackTraceString(error)) }
            previous?.uncaughtException(thread, error) ?: run {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    companion object {
        fun lastCrash(context: Context): String? = runCatching {
            File(context.filesDir, "crash.txt").takeIf { it.exists() }?.readText()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
