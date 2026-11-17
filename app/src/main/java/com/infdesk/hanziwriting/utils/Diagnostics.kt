package com.infdesk.hanziwriting.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StrictMode
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import com.infdesk.hanziwriting.BuildConfig
import com.infdesk.hanziwriting.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Measurement-only instrumentation. Nothing here changes app behaviour.
 * Everything is written to DebugLog so one exported report contains it all.
 */
object Diagnostics {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val marks = ConcurrentHashMap<String, Long>()
    private val openCounts = ConcurrentHashMap<Int, Int>()
    private val strictLogged = AtomicInteger(0)

    // Live-work counters. If these stay > 0 on a screen that should not have
    // them, work is leaking across navigation.
    val anim = AtomicInteger(0)
    val vm = AtomicInteger(0)
    val vmJobs = AtomicInteger(0)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        DebugLog.d("Boot", installSummary(app))
        DebugLog.d("Boot", accessibilitySummary(app))
        DebugLog.d(
            "Boot",
            "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "abis=${Build.SUPPORTED_ABIS.joinToString()} pid=${Process.myPid()} " +
                "prebuiltAsset=${AppDatabase.hasPrebuiltDatabase(app)} " +
                "metadataAsset=${AppDatabase.hasVocabularyMetadata(app)} " +
                "maxHeapMb=${Runtime.getRuntime().maxMemory() / 1048576}"
        )
        setupStrictMode()
        startWatchdog()
        startCpuProbe()
    }

    /**
     * Marks an event timestamp. List taps also advance the open counter here, so
     * callers that only read openSequence() cannot double-count an open.
     */
    fun mark(key: String) {
        marks[key] = SystemClock.uptimeMillis()
        if (key.startsWith("listTap:")) {
            val id = key.removePrefix("listTap:").toIntOrNull()
            if (id != null) openCounts[id] = (openCounts[id] ?: 0) + 1
        }
    }

    fun sinceMark(key: String): Long {
        val t = marks[key] ?: return -1L
        return SystemClock.uptimeMillis() - t
    }

    /** How many times this list has been opened in this process (read-only). */
    fun openSequence(listId: Int): Int = openCounts[listId] ?: 1

    fun installSummary(context: Context): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val debuggable =
                (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            "build=${BuildConfig.BUILD_ID} debuggable=$debuggable " +
                "firstInstall=${fmt.format(Date(pi.firstInstallTime))} " +
                "lastUpdate=${fmt.format(Date(pi.lastUpdateTime))} " +
                "freshInstall=${pi.firstInstallTime == pi.lastUpdateTime}"
        }.getOrElse { "installSummary failed: $it" }
    }

    /**
     * Compose's accessibility traversal (the 300-700 ms
     * AndroidComposeViewAccessibilityDelegateCompat stalls) only runs when an
     * accessibility service is active. This reports that state without ADB.
     */
    fun accessibilitySummary(context: Context): String = runCatching {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        "accessibilityEnabled=${am.isEnabled} " +
            "touchExploration=${am.isTouchExplorationEnabled} " +
            "serviceCount=${services.size} services=${services.joinToString { it.id }}"
    }.getOrElse { "accessibilitySummary failed: $it" }

    private fun setupStrictMode() {
        val thread = StrictMode.ThreadPolicy.Builder().detectAll()
        if (Build.VERSION.SDK_INT >= 28) {
            thread.penaltyListener(Executors.newSingleThreadExecutor()) { v ->
                if (strictLogged.incrementAndGet() <= 80) {
                    DebugLog.e("StrictMode", v.toString())
                }
            }
        } else {
            thread.penaltyLog()
        }
        StrictMode.setThreadPolicy(thread.build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
    }

    private fun startWatchdog() {
        val main = Handler(Looper.getMainLooper())
        val mainThread = Looper.getMainLooper().thread
        Thread({
            while (true) {
                val ran = AtomicBoolean(false)
                val t0 = SystemClock.uptimeMillis()
                main.post { ran.set(true) }
                Thread.sleep(200)
                if (!ran.get()) {
                    DebugLog.d(
                        "Stall",
                        "main blocked >200ms:\n" +
                            mainThread.stackTrace.take(30).joinToString("\n")
                    )
                    while (!ran.get()) Thread.sleep(50)
                    DebugLog.d("Stall", "cleared after ${SystemClock.uptimeMillis() - t0}ms")
                }
            }
        }, "stall-watchdog").apply { isDaemon = true }.start()
    }

    private fun startCpuProbe() {
        scope.launch {
            var lastCpu = Process.getElapsedCpuTime()
            var lastTs = SystemClock.uptimeMillis()
            while (true) {
                delay(1000)
                val cpu = Process.getElapsedCpuTime()
                val ts = SystemClock.uptimeMillis()
                val cpuMs = cpu - lastCpu
                val wall = ts - lastTs
                lastCpu = cpu
                lastTs = ts
                val rt = Runtime.getRuntime()
                val heapMb = (rt.totalMemory() - rt.freeMemory()) / 1048576
                val nativeMb = Debug.getNativeHeapAllocatedSize() / 1048576
                DebugLog.d(
                    "Probe",
                    "cpuMs/s=$cpuMs wall=${wall}ms anim=${anim.get()} vm=${vm.get()} " +
                        "vmJobs=${vmJobs.get()} heapMb=$heapMb nativeMb=$nativeMb"
                )
            }
        }
    }

    private fun rows(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String =
        runCatching {
            db.query(sql).use { c ->
                val sb = StringBuilder()
                while (c.moveToNext()) {
                    for (i in 0 until c.columnCount) {
                        if (i > 0) sb.append(" | ")
                        sb.append(c.getString(i))
                    }
                    sb.append('\n')
                }
                sb.toString().ifEmpty { "(no rows)" }
            }
        }.getOrElse { "query failed: $it" }

    suspend fun dbReport(context: Context): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val app = context.applicationContext
        for (name in listOf("hanzi_database", "hanzi_database-wal", "hanzi_database-shm")) {
            val f = app.getDatabasePath(name)
            sb.append("file $name exists=${f.exists()} bytes=${if (f.exists()) f.length() else 0}\n")
        }
        runCatching {
            val db = AppDatabase.getDatabase(app).openHelper.readableDatabase
            sb.append("page_count=").append(rows(db, "PRAGMA page_count").trim()).append('\n')
            sb.append("page_size=").append(rows(db, "PRAGMA page_size").trim()).append('\n')
            sb.append("journal_mode=").append(rows(db, "PRAGMA journal_mode").trim()).append('\n')
            sb.append("list_items indexes:\n").append(
                rows(db, "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='list_items'")
            )
            sb.append("list_items total rows:\n").append(rows(db, "SELECT COUNT(*) FROM list_items"))
            sb.append("largest lists:\n").append(
                rows(db, "SELECT listId, COUNT(*) c FROM list_items GROUP BY listId ORDER BY c DESC LIMIT 5")
            )
            sb.append("PLAN list open:\n").append(
                rows(db, "EXPLAIN QUERY PLAN SELECT character FROM list_items WHERE listId=1 ORDER BY rowid")
            )
            sb.append("PLAN graphics pk:\n").append(
                rows(db, "EXPLAIN QUERY PLAN SELECT * FROM hanzi_graphics WHERE character='好'")
            )
            sb.append("PLAN vocab level:\n").append(
                rows(db, "EXPLAIN QUERY PLAN SELECT preferredForm FROM vocabulary_entries WHERE source='HSK' AND level='1' ORDER BY sortOrder, entryId")
            )
        }.onFailure { sb.append("dbReport failed: $it\n") }
        sb.toString()
    }

    fun memorySummary(): String {
        val rt = Runtime.getRuntime()
        return "heapUsedMb=${(rt.totalMemory() - rt.freeMemory()) / 1048576} " +
            "heapTotalMb=${rt.totalMemory() / 1048576} maxMb=${rt.maxMemory() / 1048576} " +
            "nativeMb=${Debug.getNativeHeapAllocatedSize() / 1048576}"
    }

    suspend fun fullReport(context: Context): String =
        installSummary(context) + "\n" +
            accessibilitySummary(context) + "\n" +
            memorySummary() + "\n" +
            dbReport(context)
}
