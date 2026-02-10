package com.example.test_app_l4s_ping_test

import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runningJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val domainInput = findViewById<EditText>(R.id.domainInput)
        val portInput = findViewById<EditText>(R.id.portInput)
        val countInput = findViewById<EditText>(R.id.countInput)
        val intervalInput = findViewById<EditText>(R.id.intervalInput)

        val ecnGroup = findViewById<RadioGroup>(R.id.ecnGroup)
        val radioNotEct = findViewById<RadioButton>(R.id.radioNotEct)
        val radioEct0 = findViewById<RadioButton>(R.id.radioEct0)
        val radioEct1 = findViewById<RadioButton>(R.id.radioEct1)

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val output = findViewById<TextView>(R.id.output)

        fun appendLine(s: String) {
            output.append(s + "\n")
        }

        fun selectedEcnByte(): Int {
            return when (ecnGroup.checkedRadioButtonId) {
                radioEct1.id -> 0x01 // ECT(1) - L4S
                radioEct0.id -> 0x02 // ECT(0) - classic ECN-capable
                else -> 0x00         // Not-ECT
            }
        }

        startButton.setOnClickListener {
            val host = domainInput.text.toString().trim().ifEmpty { "google.com" }
            val port = portInput.text.toString().toIntOrNull() ?: 443
            val count = countInput.text.toString().toIntOrNull() ?: 10
            val intervalMs = intervalInput.text.toString().toLongOrNull() ?: 500L
            val ecn = selectedEcnByte()

            output.text = ""
            appendLine("Target: $host:$port")
            appendLine("ECN byte (TrafficClass): 0x${ecn.toString(16).padStart(2, '0')}")
            appendLine("Test: TCP connect RTT (not ICMP ping)")
            appendLine("-----")

            startButton.isEnabled = false
            stopButton.isEnabled = true

            runningJob?.cancel()
            runningJob = scope.launch {
                val rtts = mutableListOf<Long>()
                val failures = mutableListOf<String>()

                for (i in 1..count) {
                    if (!isActive) break

                    val (rttMs, err) = withContext(Dispatchers.IO) {
                        tcpConnectRttMs(host, port, ecn)
                    }

                    if (err != null) {
                        failures.add("[$i] FAIL: $err")
                        appendLine("[$i] FAIL: $err")
                    } else {
                        rtts.add(rttMs!!)
                        appendLine("[$i] RTT: $rttMs ms")
                    }

                    delay(intervalMs)
                }

                appendLine("-----")
                if (rtts.isNotEmpty()) {
                    val sorted = rtts.sorted()
                    val avg = (sorted.sum().toDouble() / sorted.size).roundToLong()
                    val p50 = percentile(sorted, 50.0)
                    val p90 = percentile(sorted, 90.0)
                    val p99 = percentile(sorted, 99.0)
                    appendLine("OK: ${rtts.size}   FAIL: ${failures.size}")
                    appendLine("avg=${avg}ms  p50=${p50}ms  p90=${p90}ms  p99=${p99}ms")
                } else {
                    appendLine("No successful samples.")
                }

                startButton.isEnabled = true
                stopButton.isEnabled = false
            }
        }

        stopButton.setOnClickListener {
            runningJob?.cancel()
            runningJob = null
            startButton.isEnabled = true
            stopButton.isEnabled = false
            output.append("Stopped.\n")
        }
    }

    override fun onDestroy() {
        runningJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun percentile(sorted: List<Long>, p: Double): Long {
        if (sorted.isEmpty()) return 0
        val idx = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    /**
     * Measures RTT by timing socket.connect().
     *
     * Sets ECN via Socket#setTrafficClass.
     * The traffic class byte is DSCP(6 bits) + ECN(2 bits). We set DSCP=0 and ECN=(0, 1, 2).
     */
    private fun tcpConnectRttMs(host: String, port: Int, ecnByte: Int): Pair<Long?, String?> {
        return try {
            Socket().use { sock ->
                sock.trafficClass = ecnByte

                val addr = InetSocketAddress(host, port)
                val startNs = SystemClock.elapsedRealtimeNanos()
                sock.connect(addr, 3000) // 3s timeout
                val endNs = SystemClock.elapsedRealtimeNanos()

                val rttMs = ((endNs - startNs) / 1_000_000L)
                Pair(rttMs, null)
            }
        } catch (t: Throwable) {
            Pair(null, t.javaClass.simpleName + ": " + (t.message ?: "unknown error"))
        }
    }
}
