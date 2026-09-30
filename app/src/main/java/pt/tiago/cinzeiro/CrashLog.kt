package pt.tiago.cinzeiro

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the stack trace of every crash to the device's Download/crashlogs/ folder, so it can
 * be read without adb/logcat (e.g. with any file manager or over a shell on the device).
 *
 * Only active in debuggable builds: a release build never writes to the user's folders.
 * Install as early as possible: Application.onCreate().
 */
object CrashLog {
    private const val SUBDIR = "crashlogs"

    fun install(context: Context) {
        val app = context.applicationContext
        if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Never let a failure while writing hide the original crash.
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Appends [lines] to a text file in Download/crashlogs/[name] (same place as the crashes),
     * keeping only the last [maxLines]. For "silent" diagnostics (e.g. the +1 feedback log).
     * Debuggable builds only. Call off the main thread if possible.
     *
     * [name] must end in `.txt`: with MIME `text/plain`, MediaStore appends `.txt` to any other
     * extension (`x.log` → `x.log.txt`), so the lookup by the requested name no longer found the
     * file and every call created a new one (`x.log (1).txt`…) — the 0.5.0 bug.
     */
    @Synchronized
    fun appendLines(context: Context, name: String, lines: List<String>, maxLines: Int) {
        require(name.endsWith(".txt")) { "MediaStore renames files to .txt; use a .txt name: $name" }
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/"
            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(name, relative),
                null,
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
            val old = existing?.let { uri ->
                resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.orEmpty()
            val uri = existing ?: resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
                },
            ) ?: return
            val text = keepLast(old, lines, maxLines)
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        } else {
            val dir = File(context.getExternalFilesDir(null), SUBDIR).apply { mkdirs() }
            val file = File(dir, name)
            val old = if (file.exists()) file.readText() else ""
            file.writeText(keepLast(old, lines, maxLines))
        }
    }

    /** Old content + new lines, only the last [maxLines], terminated by \n. */
    internal fun keepLast(old: String, lines: List<String>, maxLines: Int): String =
        (old.lines().filter { it.isNotEmpty() } + lines).takeLast(maxLines).joinToString("\n", postfix = "\n")

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val now = Date()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        val name = "${context.packageName}-$stamp.txt"

        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val text = buildString {
            appendLine("app:     ${context.packageName} $versionName")
            appendLine("time:    ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now)}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("thread:  ${thread.name}")
            appendLine()
            append(trace)
        }.toByteArray()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: MediaStore, no permissions needed. The file ends up in /storage/emulated/0/Download/crashlogs/.
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { it.write(text) }
        } else {
            // Android 8–9: the app's private folder (Android/data/<pkg>/files/crashlogs/), no permissions needed.
            val dir = File(context.getExternalFilesDir(null), SUBDIR).apply { mkdirs() }
            File(dir, name).writeBytes(text)
        }
    }
}
