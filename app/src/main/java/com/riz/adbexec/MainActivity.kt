package com.riz.adbexec

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF070B16.toInt())
        }
        setContentView(ScrollView(this).apply { addView(out) })

        out.append("onCreate OK\n")
        probe(out, "libadb class") { Class.forName("io.github.muntashirakon.adb.AbsAdbConnectionManager") }
        probe(out, "sun-security class") { Class.forName("android.sun.security.x509.X509CertImpl") }
        probe(out, "hiddenapibypass class") { Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass") }
        probe(out, "GlassDrawable") { GlassDrawable(10f, 1f, 0x22FFFFFF, 0x11FFFFFF) }
        probe(out, "AdbKeys init") { AdbKeys.get(this) }
        out.append("SELESAI\n")
    }

    private fun probe(out: TextView, name: String, block: () -> Unit) {
        val result = runCatching(block).fold(
            onSuccess = { "OK" },
            onFailure = { "GAGAL: ${it.javaClass.name}: ${it.message}" },
        )
        out.append("$name -> $result\n")
    }
}
