package com.example.ecn_ping

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ecn_ping.core.PingCommandBuilder
import com.example.ecn_ping.core.PingMode
import com.example.ecn_ping.core.PingParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val hostInput = findViewById<EditText>(R.id.hostInput)
        val countInput = findViewById<EditText>(R.id.countInput)
        val intervalInput = findViewById<EditText>(R.id.intervalInput)
        val timeoutInput = findViewById<EditText>(R.id.timeoutInput)

        val runDefaultBtn = findViewById<Button>(R.id.runDefaultBtn)
        val runEct1Btn = findViewById<Button>(R.id.runEct1Btn)
        val runEct0Btn = findViewById<Button>(R.id.runEct0Btn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)

        val output = findViewById<TextView>(R.id.output)

        fun log(line: String) {
            output.append(line + "\n")
        }

        fun setRunning(running: Boolean) {
            runDefaultBtn.isEnabled = !running
            runEct1Btn.isEnabled = !running
            runEct0Btn.isEnabled = !running
            stopBtn.isEnabled = running
        }

        suspend fun runPing(mode: PingMode) {
            val host = hostInput.text.toString().trim()
            val count = countInput.text.toString().toIntOrNull() ?: 5
            val interval = intervalInput.text.toString().toDoubleOrNull() ?: 0.2
            val timeout = timeoutInput.text.toString().toIntOrNull() ?: 2

            output.text = ""
            log("Mode: ${mode.label}")
            val pingBinary = withContext(Dispatchers.IO) { PingBinaryLocator.findPingBinary() }
            log("Using ping binary: ${pingBinary.display}")

            val cmd = PingCommandBuilder.build(
                pingBinary = pingBinary.command,
                host = host,
                mode = mode,
                count = count,
                intervalSeconds = interval,
                timeoutSeconds = timeout,
                payloadBytes = 56,
            )

            val argv = pingBinary.decorateArgv(cmd.argv)
            log("Command: " + argv.joinToString(" "))

            val raw = withContext(Dispatchers.IO) { ProcessRunner.run(argv) }
            log("")
            log("---- raw output ----")
            log(raw.trimEnd())
            log("---- summary ----")

            val summary = PingParser.parse(raw)
            log("tx=${summary.transmitted} rx=${summary.received} loss%=${summary.packetLossPct}")
            log("rtt ms: min=${summary.minMs} avg=${summary.avgMs} max=${summary.maxMs}")
        }

        fun start(mode: PingMode) {
            if (job?.isActive == true) return
            setRunning(true)
            job = scope.launch {
                try {
                    runPing(mode)
                } catch (e: Exception) {
                    output.append("\nERROR: ${e.message}\n")
                } finally {
                    setRunning(false)
                }
            }
        }

        runDefaultBtn.setOnClickListener { start(PingMode.DEFAULT) }
        runEct1Btn.setOnClickListener { start(PingMode.ECT1) }
        runEct0Btn.setOnClickListener { start(PingMode.ECT0) }

        stopBtn.setOnClickListener {
            job?.cancel()
            log("Stopped.")
            setRunning(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

private object ProcessRunner {
    fun run(argv: List<String>): String {
        val pb = ProcessBuilder(argv)
        pb.redirectErrorStream(true)
        val p = pb.start()

        val sb = StringBuilder()
        BufferedReader(InputStreamReader(p.inputStream)).use { br ->
            var line: String?
            while (true) {
                line = br.readLine() ?: break
                sb.append(line).append('\n')
            }
        }
        p.waitFor()
        return sb.toString()
    }
}

/**
 * Locates a usable ping command on Android without root.
 *
 * Pixel devices typically provide /system/bin/ping (toybox).
 */
private object PingBinaryLocator {

    data class Found(val command: String, val display: String, val needsToyboxSubcommand: Boolean) {
        fun decorateArgv(argv: List<String>): List<String> {
            return if (needsToyboxSubcommand) {
                listOf(command, "ping") + argv.drop(1) // replace first token with toybox + ping
            } else {
                argv
            }
        }
    }

    fun findPingBinary(): Found {
        val candidates = listOf(
            Found("/system/bin/ping", "/system/bin/ping", false),
            Found("ping", "ping (PATH)", false),
            Found("/system/bin/toybox", "/system/bin/toybox ping", true),
            Found("toybox", "toybox ping (PATH)", true),
        )

        for (c in candidates) {
            try {
                // minimal probe: ping -c 1 127.0.0.1
                val probeArgv = if (c.needsToyboxSubcommand) {
                    listOf(c.command, "ping", "-c", "1", "127.0.0.1")
                } else {
                    listOf(c.command, "-c", "1", "127.0.0.1")
                }
                val out = ProcessRunner.run(probeArgv)
                if (out.contains("1 packets transmitted") || out.contains("PING")) {
                    return c
                }
            } catch (_: Exception) {
                // try next
            }
        }
        // Fall back to /system/bin/ping even if probe failed; user may have no loopback privileges
        return Found("/system/bin/ping", "/system/bin/ping (fallback)", false)
    }
}
