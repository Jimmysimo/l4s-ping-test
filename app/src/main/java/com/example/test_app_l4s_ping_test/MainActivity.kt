package com.example.test_app_l4s_ping_test

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "ECN_TEST"
        private const val LOG_FILE_NAME = "ecn_test_log.txt"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runningJob: Job? = null

    private enum class TrafficClassMode(val label: String, val trafficClass: Int?) {
        DEFAULT("Default (do not set Traffic Class)", null),
        FORCE_NOT_ECT("Force Not-ECT (0x00)", 0x00),
        ECT0("ECT(0) (0x02)", 0x02),
        ECT1("ECT(1) / L4S (0x01)", 0x01),
    }

    private data class SampleResult(
        val dnsMs: Long,
        val tcSetMs: Long,
        val connectMs: Long,
        val totalMs: Long,
        val appliedTc: Boolean,
        val tcValue: Int?,
        val error: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Startup breadcrumb so you can prove you're running the APK that contains the ECN logging.
        // This should appear in logcat immediately after launching the app.
        Log.i(TAG, "MainActivity started (pid=${android.os.Process.myPid()})")

        val domainInput = findViewById<EditText>(R.id.domainInput)
        val portInput = findViewById<EditText>(R.id.portInput)
        val countInput = findViewById<EditText>(R.id.countInput)
        val intervalInput = findViewById<EditText>(R.id.intervalInput)

        val ecnGroup = findViewById<RadioGroup>(R.id.ecnGroup)
        val radioDefault = findViewById<RadioButton>(R.id.radioDefault)
        val radioNotEct = findViewById<RadioButton>(R.id.radioNotEct)
        val radioEct0 = findViewById<RadioButton>(R.id.radioEct0)
        val radioEct1 = findViewById<RadioButton>(R.id.radioEct1)

        val checkApplyBeforeConnect = findViewById<CheckBox>(R.id.checkApplyBeforeConnect)

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val runMatrixButton = findViewById<Button>(R.id.runMatrixButton)

        val output = findViewById<TextView>(R.id.output)

        fun appendLine(s: String) {
            output.append(s + "\n")
        }

        /**
         * Writes a line to:
         * 1) the on-screen output
         * 2) logcat (INFO level)
         * 3) a small persistent file (internal storage) so logs survive scrolling / filtering
         */
        fun logPersist(line: String) {
            appendLine(line)
            Log.i(TAG, line)
            try {
                openFileOutput(LOG_FILE_NAME, MODE_APPEND).use { fos ->
                    fos.write((line + "\n").toByteArray(Charsets.UTF_8))
                }
            } catch (t: Throwable) {
                // If file I/O fails, still keep UI/logcat output.
                Log.w(TAG, "Failed writing to $LOG_FILE_NAME: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        fun selectedMode(): TrafficClassMode {
            return when (ecnGroup.checkedRadioButtonId) {
                radioEct1.id -> TrafficClassMode.ECT1
                radioEct0.id -> TrafficClassMode.ECT0
                radioNotEct.id -> TrafficClassMode.FORCE_NOT_ECT
                radioDefault.id -> TrafficClassMode.DEFAULT
                else -> TrafficClassMode.DEFAULT
            }
        }

        fun printHeader(host: String, port: Int, mode: TrafficClassMode, applyBefore: Boolean, count: Int, intervalMs: Long) {
            output.text = ""

            // Reset persistent file for this run.
            try {
                openFileOutput(LOG_FILE_NAME, MODE_PRIVATE).use { /* truncate */ }
            } catch (_: Throwable) {
                // ignore
            }

            logPersist("Target: $host:$port")
            logPersist("Mode: ${mode.label}")
            logPersist("Apply TC before connect: $applyBefore")
            logPersist("Samples: $count   Interval: ${intervalMs}ms")
            logPersist("Log file: ${filesDir.absolutePath}/$LOG_FILE_NAME")
            logPersist("-----")
            logPersist("Per-sample timings (ms): dns, tcSet, connect, total")
            logPersist("Note: total includes dns + optional tcSet + connect")
            logPersist("-----")
        }

        fun setButtonsRunning(running: Boolean) {
            startButton.isEnabled = !running
            runMatrixButton.isEnabled = !running
            stopButton.isEnabled = running
        }

        suspend fun runSingleCase(host: String, port: Int, count: Int, intervalMs: Long, mode: TrafficClassMode, applyBefore: Boolean) {
            printHeader(host, port, mode, applyBefore, count, intervalMs)
            setButtonsRunning(true)

            val totals = mutableListOf<Long>()
            val connects = mutableListOf<Long>()
            val dnss = mutableListOf<Long>()
            val tcSets = mutableListOf<Long>()
            var fails = 0

            for (i in 1..count) {
                // `isActive` is an extension on CoroutineScope/CoroutineContext.
                // We're inside a suspend function, so use the current coroutine context.
                if (!currentCoroutineContext().isActive) break

                val res = withContext(Dispatchers.IO) {
                    tcpConnectTimings(host, port, mode.trafficClass, applyBefore)
                }

                if (res.error != null) {
                    fails += 1
                        logPersist("[$i] FAIL: ${res.error}")
                } else {
                    totals.add(res.totalMs)
                    connects.add(res.connectMs)
                    dnss.add(res.dnsMs)
                    tcSets.add(res.tcSetMs)
                        logPersist("[$i] dns=${res.dnsMs}  tc=${res.tcSetMs}  conn=${res.connectMs}  total=${res.totalMs}")
                }

                delay(intervalMs)
            }

            logPersist("-----")
            if (totals.isNotEmpty()) {
                fun stats(label: String, xs: List<Long>) {
                    val sorted = xs.sorted()
                    val avg = (sorted.sum().toDouble() / sorted.size).roundToLong()
                    val p50 = percentile(sorted, 50.0)
                    val p90 = percentile(sorted, 90.0)
                    val p99 = percentile(sorted, 99.0)
                    logPersist("$label: avg=${avg}  p50=${p50}  p90=${p90}  p99=${p99}")
                }
                logPersist("OK=${totals.size}  FAIL=$fails")
                stats("dns   ", dnss)
                stats("tcSet ", tcSets)
                stats("conn  ", connects)
                stats("total ", totals)
            } else {
                logPersist("No successful samples. FAIL=$fails")
            }

            setButtonsRunning(false)
        }

        startButton.setOnClickListener {
            val host = domainInput.text.toString().trim().ifEmpty { "google.com" }
            val port = portInput.text.toString().toIntOrNull() ?: 443
            val count = countInput.text.toString().toIntOrNull() ?: 10
            val intervalMs = intervalInput.text.toString().toLongOrNull() ?: 500L
            val mode = selectedMode()
            val applyBefore = checkApplyBeforeConnect.isChecked

            runningJob?.cancel()
            runningJob = scope.launch {
                runSingleCase(host, port, count, intervalMs, mode, applyBefore)
            }
        }

        runMatrixButton.setOnClickListener {
            val host = domainInput.text.toString().trim().ifEmpty { "google.com" }
            val port = portInput.text.toString().toIntOrNull() ?: 443

            // Quick diagnostic matrix to validate the test cases:
            // - DEFAULT (no setTrafficClass) baseline
            // - FORCE_NOT_ECT to detect "calling setTrafficClass" overhead/path effects
            // - ECT(1) and ECT(0) to detect ECN intolerance
            // Each is run with TC applied before and after connect.
            val modes = listOf(
                TrafficClassMode.DEFAULT,
                TrafficClassMode.FORCE_NOT_ECT,
                TrafficClassMode.ECT1,
                TrafficClassMode.ECT0
            )

            val perCaseCount = 8
            val intervalMs = 200L

            runningJob?.cancel()
            runningJob = scope.launch {
                output.text = ""
                // Reset persistent file for this run.
                try {
                    openFileOutput(LOG_FILE_NAME, MODE_PRIVATE).use { /* truncate */ }
                } catch (_: Throwable) {
                    // ignore
                }

                logPersist("Target: $host:$port")
                logPersist("Running ECN test matrix (each case: n=$perCaseCount, interval=${intervalMs}ms)")
                logPersist("Log file: ${filesDir.absolutePath}/$LOG_FILE_NAME")
                logPersist("-----")

                setButtonsRunning(true)

                for (mode in modes) {
                    for (applyBefore in listOf(true, false)) {
                        if (!currentCoroutineContext().isActive) break
                        logPersist("")
                        logPersist("Case: ${mode.label} | applyBefore=$applyBefore")

                        val totals = mutableListOf<Long>()
                        val connects = mutableListOf<Long>()
                        var fails = 0

                        for (i in 1..perCaseCount) {
                            if (!currentCoroutineContext().isActive) break
                            val res = withContext(Dispatchers.IO) {
                                tcpConnectTimings(host, port, mode.trafficClass, applyBefore)
                            }
                            if (res.error != null) {
                                fails += 1
                            } else {
                                totals.add(res.totalMs)
                                connects.add(res.connectMs)
                            }
                            delay(intervalMs)
                        }

                        if (totals.isNotEmpty()) {
                            val avgTotal = (totals.sum().toDouble() / totals.size).roundToLong()
                            val avgConn = (connects.sum().toDouble() / connects.size).roundToLong()
                            logPersist("Result: OK=${totals.size} FAIL=$fails | avgConn=${avgConn}ms avgTotal=${avgTotal}ms")
                        } else {
                            logPersist("Result: OK=0 FAIL=$fails")
                        }
                    }
                }

                logPersist("")
                logPersist("-----")
                logPersist("Interpretation tips:")
                logPersist("- If FORCE Not-ECT is slower than DEFAULT, the act of calling setTrafficClass (even to 0x00) changes behavior.")
                logPersist("- If applyBefore=true is worse than applyBefore=false for ECT(0/1), the path may be intolerant to marked SYN/handshake.")
                logPersist("- If ECT(0/1) are worse than FORCE Not-ECT, you likely have ECN-intolerant middleboxes on that network.")
                setButtonsRunning(false)
            }
        }

        stopButton.setOnClickListener {
            runningJob?.cancel()
            runningJob = null
            setButtonsRunning(false)
            logPersist("Stopped.")
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
     * Measures TCP timings by splitting:
     * - DNS resolution time (InetAddress.getByName)
     * - Optional traffic class set time (Socket#setTrafficClass)
     * - TCP connect time (Socket.connect)
     *
     * IMPORTANT:
     * - trafficClass is the full DS byte: DSCP (6 bits) + ECN (2 bits).
     * - If trafficClass is null, we do not call setTrafficClass at all (true baseline).
     * - applyBeforeConnect=true applies traffic class before connect() (affects SYN/handshake).
     */
    private fun tcpConnectTimings(host: String, port: Int, trafficClass: Int?, applyBeforeConnect: Boolean): SampleResult {
        val totalStartNs = SystemClock.elapsedRealtimeNanos()

        // DNS phase
        val dnsStartNs = SystemClock.elapsedRealtimeNanos()
        val inet: InetAddress = try {
            InetAddress.getByName(host)
        } catch (t: Throwable) {
            val dnsMs = (SystemClock.elapsedRealtimeNanos() - dnsStartNs) / 1_000_000L
            val totalMs = (SystemClock.elapsedRealtimeNanos() - totalStartNs) / 1_000_000L
            return SampleResult(dnsMs, 0L, 0L, totalMs, appliedTc = false, tcValue = trafficClass, error = "DNS: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}")
        }
        val dnsMs = (SystemClock.elapsedRealtimeNanos() - dnsStartNs) / 1_000_000L

        return try {
            Socket().use { sock ->
                var tcSetMs = 0L
                var applied = false

                fun maybeSetTc(phase: String) {
                    if (trafficClass != null) {
                        val tcStartNs = SystemClock.elapsedRealtimeNanos()
                        try {
                            sock.trafficClass = trafficClass
                        } catch (t: Throwable) {
                            Log.e(TAG, "$phase setTrafficClass FAILED for 0x${trafficClass.toString(16)}: ${t.javaClass.simpleName}: ${t.message}")
                            throw t
                        }
                        val tcEndNs = SystemClock.elapsedRealtimeNanos()

                        // Read back what the OS thinks the TrafficClass is.
                        // Note: readback matching does NOT guarantee the value appears on the wire,
                        // but readback mismatching is a strong signal something is off.
                        val readBack = try { sock.trafficClass } catch (_: Throwable) { -1 }
                        val ecn = if (readBack >= 0) (readBack and 0b11) else -1
                        val dscp = if (readBack >= 0) ((readBack ushr 2) and 0b111111) else -1

                        tcSetMs += (tcEndNs - tcStartNs) / 1_000_000L
                        applied = true

                        Log.i(
                            TAG,
                            "$phase setTrafficClass=0x${trafficClass.toString(16)} readBack=0x${readBack.toString(16)} DSCP=$dscp ECN=$ecn tcSetMs=${(tcEndNs - tcStartNs) / 1_000_000.0}ms"
                        )
                    } else {
                        Log.i(TAG, "$phase TrafficClass not set (DEFAULT mode)")
                    }
                }

                if (applyBeforeConnect) {
                    maybeSetTc("PRE")
                }

                val addr = InetSocketAddress(inet, port)
                val connStartNs = SystemClock.elapsedRealtimeNanos()
                sock.connect(addr, 3000) // 3s timeout
                val connectMs = (SystemClock.elapsedRealtimeNanos() - connStartNs) / 1_000_000L

                // Breadcrumb to correlate what connect() just did with your packet capture.
                // The SYN for this connect attempt is what you should inspect in Wireshark.
                Log.i(TAG, "CONNECT host=$host port=$port local=${sock.localAddress?.hostAddress}:${sock.localPort} remote=${sock.inetAddress?.hostAddress}:${sock.port} connectMs=${connectMs}ms")

                if (!applyBeforeConnect) {
                    maybeSetTc("POST")
                }

                val totalMs = (SystemClock.elapsedRealtimeNanos() - totalStartNs) / 1_000_000L

                SampleResult(
                    dnsMs = dnsMs,
                    tcSetMs = tcSetMs,
                    connectMs = connectMs,
                    totalMs = totalMs,
                    appliedTc = applied,
                    tcValue = trafficClass,
                    error = null
                )
            }
        } catch (t: Throwable) {
            val totalMs = (SystemClock.elapsedRealtimeNanos() - totalStartNs) / 1_000_000L
            SampleResult(dnsMs, 0L, 0L, totalMs, appliedTc = false, tcValue = trafficClass, error = "${t.javaClass.simpleName}: ${t.message ?: "unknown error"}")
        }
    }
}