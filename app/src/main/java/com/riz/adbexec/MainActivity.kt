package com.riz.adbexec

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalServices
import io.github.muntashirakon.adb.android.AdbMdns
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val TEXT = 0xFFF4F6FB.toInt()
private val MUTED = 0x99F4F6FB.toInt()
private val OK = 0xFF7DE2B8.toInt()
private val DANGER = 0xFFFF9AA7.toInt()

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val log = StringBuilder()

    private lateinit var prefs: SharedPreferences
    private var manager: AbsAdbConnectionManager? = null
    private var shell: AdbStream? = null
    private var connected = false

    private lateinit var scroll: ScrollView
    private lateinit var statusView: TextView
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var commandInput: EditText
    private lateinit var outputView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("adb", Context.MODE_PRIVATE)
        setContentView(buildUi())

        setStatus("Mencari koneksi…")
        io.execute { connectInternal(silent = true) }
    }

    override fun onDestroy() {
        runCatching { shell?.close() }
        io.execute {
            runCatching { manager?.close() }
            io.shutdown()
        }
        super.onDestroy()
    }

    private fun buildUi(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
        }

        val statusCard = card()
        statusCard.addView(heading("Riz ADB"))
        statusCard.addView(sub("Shell lewat wireless debugging, tanpa PC."))
        statusView = TextView(this).apply {
            textSize = 13f
            setTextColor(TEXT)
            setPadding(0, dp(12), 0, 0)
        }
        statusCard.addView(statusView, wide())
        statusCard.addView(
            row(
                button("Hubungkan", primary = true) { doConnect() },
                button("Putuskan") { doDisconnect() },
            ),
            wide(dp(12)),
        )
        page.addView(statusCard, wide(dp(12)))

        val pairCard = card()
        pairCard.addView(heading("Pairing"))
        pairCard.addView(
            sub(
                "Developer options → Wireless debugging → Pair device with pairing code. " +
                    "Biarkan dialog tetap terbuka (split screen), isi kodenya di sini.",
            ),
        )
        hostInput = field("Host", InputType.TYPE_CLASS_TEXT).apply {
            setText(prefs.getString("host", "127.0.0.1"))
        }
        portInput = field("Pair port", InputType.TYPE_CLASS_NUMBER).apply {
            val p = prefs.getInt("port", 0)
            if (p > 0) setText(p.toString())
        }
        codeInput = field("6-digit code", InputType.TYPE_CLASS_NUMBER)
        pairCard.addView(hostInput, wide(dp(10)))
        pairCard.addView(portInput, wide(dp(8)))
        pairCard.addView(codeInput, wide(dp(8)))
        pairCard.addView(
            row(
                button("Cari port") { findPairPort() },
                button("Pair & connect", primary = true) { doPair() },
            ),
            wide(dp(12)),
        )
        page.addView(pairCard, wide(dp(12)))

        val actionCard = card()
        actionCard.addView(heading("Aksi cepat"))
        actionCard.addView(sub("Tombol cuma mengisi command. Tambah nama paket, lalu Jalankan."))
        actionCard.addView(
            row(
                button("List app") { commandInput.setText("pm list packages -3") },
                button("Freeze") { commandInput.setText("pm disable-user --user 0 ") },
                button("Unfreeze") { commandInput.setText("pm enable ") },
            ),
            wide(dp(10)),
        )
        actionCard.addView(
            row(
                button("Force stop") { commandInput.setText("am force-stop ") },
                button("Clear data") { commandInput.setText("pm clear ") },
                button("Battery") { commandInput.setText("dumpsys battery") },
            ),
            wide(dp(8)),
        )
        page.addView(actionCard, wide(dp(12)))

        val shellCard = card()
        shellCard.addView(heading("Shell"))
        commandInput = field("shell command", InputType.TYPE_CLASS_TEXT).apply {
            setText("pm list packages -3")
        }
        shellCard.addView(commandInput, wide(dp(10)))
        shellCard.addView(
            row(
                button("Jalankan", primary = true) { runCommand() },
                button("Bersihkan") { clearLog() },
            ),
            wide(dp(10)),
        )
        outputView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(MUTED)
            setTextIsSelectable(true)
            setPadding(0, dp(12), 0, 0)
        }
        shellCard.addView(outputView, wide())
        page.addView(shellCard, wide())

        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        return scroll
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GlassDrawable(dp(24).toFloat(), dp(1f), 0x2EFFFFFF, 0x0AFFFFFF, 0x306366F1, 0x1A22D3EE)
        setPadding(dp(18), dp(18), dp(18), dp(18))
    }

    private fun heading(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTextColor(TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun sub(t: String) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(MUTED)
        setPadding(0, dp(4), 0, 0)
    }

    private fun field(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText
        inputType = type
        setTextColor(TEXT)
        setHintTextColor(0x66F4F6FB.toInt())
        textSize = 14f
        maxLines = 1
        background = GlassDrawable(dp(12).toFloat(), dp(1f), 0x1AFFFFFF, 0x0AFFFFFF)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 13f
        setTextColor(if (primary) Color.WHITE else TEXT)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = GlassDrawable(
            dp(12).toFloat(),
            dp(1f),
            if (primary) 0xE66366F1.toInt() else 0x1FFFFFFF,
            if (primary) 0xA64F46E5.toInt() else 0x0FFFFFFF,
            if (primary) 0x55FFFFFF else Color.TRANSPARENT,
            Color.TRANSPARENT,
        )
        setPadding(dp(8), dp(11), dp(8), dp(11))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (i > 0) leftMargin = dp(8)
            })
        }
    }

    private fun wide(top: Int = 0) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = top }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // --- ADB ---

    private fun mgr(): AbsAdbConnectionManager =
        manager ?: AdbKeys.get(applicationContext).also { manager = it }

    private fun connectInternal(silent: Boolean = false) {
        val m = try {
            mgr()
        } catch (t: Throwable) {
            if (!silent) append("[key] gagal siapkan kunci: ${t.message}\n")
            setStatus("Belum terhubung")
            return
        }
        try {
            val ok = try {
                m.autoConnect(this, 5000)
            } catch (e: AdbPairingRequiredException) {
                setStatus("Belum di-pair. Isi kode pairing dulu.")
                markDisconnected()
                return
            }
            if (ok) {
                markConnected()
            } else {
                setStatus("Tidak menemukan adb. Nyalakan Wireless debugging lalu coba lagi.")
                markDisconnected()
            }
        } catch (t: Throwable) {
            setStatus("Gagal connect: ${t.message}")
            markDisconnected()
        }
    }

    private fun markConnected() {
        connected = true
        main.post {
            statusView.text = "Terhubung · shell siap"
            statusView.setTextColor(OK)
        }
    }

    private fun markDisconnected() {
        connected = false
        runCatching { shell?.close() }
        shell = null
    }

    private fun doConnect() {
        setStatus("Menghubungkan…")
        io.execute { connectInternal() }
    }

    private fun doDisconnect() {
        io.execute {
            runCatching { shell?.close() }
            shell = null
            runCatching { mgr().disconnect() }
            connected = false
            setStatus("Terputus")
            main.post { statusView.setTextColor(MUTED) }
        }
    }

    private fun doPair() {
        val host = hostInput.text.toString().trim().ifEmpty { "127.0.0.1" }
        val port = portInput.text.toString().trim().toIntOrNull()
        val code = codeInput.text.toString().trim()
        if (port == null || port !in 1..65535) {
            setStatus("Port pairing tidak valid")
            return
        }
        if (code.length != 6) {
            setStatus("Kode pairing harus 6 digit")
            return
        }

        prefs.edit().putString("host", host).putInt("port", port).apply()
        setStatus("Pairing…")
        io.execute {
            try {
                val ok = mgr().pair(host, port, code)
                append("\n[pair] ${if (ok) "berhasil" else "gagal"} ($host:$port)\n")
                if (ok) {
                    main.post { codeInput.setText("") }
                    connectInternal()
                } else {
                    setStatus("Pairing gagal")
                }
            } catch (t: Throwable) {
                append("[pair] gagal: ${t.message}\n")
                setStatus("Pairing gagal: ${t.message}")
            }
        }
    }

    private fun findPairPort() {
        setStatus("Mencari port pairing lewat mDNS…")
        io.execute {
            val port = AtomicInteger(-1)
            val latch = CountDownLatch(1)
            val mdns = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, p ->
                port.set(p)
                latch.countDown()
            }
            mdns.start()
            try {
                latch.await(20, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
            } finally {
                mdns.stop()
            }
            val p = port.get()
            if (p > 0) {
                prefs.edit().putInt("port", p).apply()
                main.post {
                    portInput.setText(p.toString())
                    statusView.text = "Port pairing: $p"
                }
            } else {
                setStatus("Port tidak ditemukan. Isi manual dari dialog pairing.")
                append("[mdns] port pairing tidak terdeteksi (20s)\n")
            }
        }
    }

    private fun runCommand() {
        val cmd = commandInput.text.toString().trim()
        if (cmd.isEmpty()) return
        if (!connected) {
            setStatus("Belum terhubung")
            return
        }
        append("\n$ $cmd\n")
        io.execute {
            try {
                val s = shell ?: mgr().openStream(LocalServices.SHELL).also { stream ->
                    shell = stream
                    startReader(stream)
                }
                s.openOutputStream().use { os ->
                    os.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                    os.flush()
                }
            } catch (t: Throwable) {
                append("[error] ${t.message}\n")
            }
        }
    }

    private fun startReader(stream: AdbStream) {
        Thread {
            try {
                BufferedReader(InputStreamReader(stream.openInputStream())).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        append(line + "\n")
                    }
                }
            } catch (_: Throwable) {
            } finally {
                append("\n[shell tertutup]\n")
            }
        }.apply { isDaemon = true }.start()
    }

    private fun clearLog() {
        log.setLength(0)
        outputView.text = ""
    }

    private fun setStatus(text: String) {
        main.post {
            statusView.text = text
            statusView.setTextColor(DANGER.takeIf { text.contains("gagal", true) } ?: TEXT)
        }
    }

    private fun append(text: String) {
        main.post {
            log.append(text)
            if (log.length > 200_000) log.delete(0, log.length - 200_000)
            outputView.text = log.toString()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }
}
