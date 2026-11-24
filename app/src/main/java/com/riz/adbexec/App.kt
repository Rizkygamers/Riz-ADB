package com.riz.adbexec

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.Date

class App : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installCrashHandler()
        // Bypass hidden API wajib untuk sun-security; kalau gagal jangan bikin app mati.
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onFailure { writeCrash(it) }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            writeCrash(error)
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun writeCrash(error: Throwable) {
        val text = "time=" + Date() + "\n" +
            "device=" + Build.MANUFACTURER + " " + Build.MODEL +
            " / API " + Build.VERSION.SDK_INT + "\n\n" +
            Log.getStackTraceString(error)
        runCatching { File(filesDir, "crash.txt").writeText(text) }
        runCatching { getExternalFilesDir(null)?.let { File(it, "crash.txt").writeText(text) } }
    }

    companion object {
        /** Crash dari run sebelumnya, ditampilkan di layar supaya gampang di-screenshot. */
        fun lastCrash(context: Context): String? = runCatching {
            File(context.filesDir, "crash.txt").takeIf { it.exists() }?.readText()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
