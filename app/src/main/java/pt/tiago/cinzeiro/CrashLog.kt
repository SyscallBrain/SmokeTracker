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
 * Grava o stack trace de cada crash em Download/crashlogs/ do telemóvel,
 * para ser lido por SSH (Termux) — não há adb/logcat disponível.
 *
 * Só actua em builds debuggable: uma release nunca escreve na pasta do utilizador.
 * Instalar o mais cedo possível: Application.onCreate().
 */
object CrashLog {
    private const val SUBDIR = "crashlogs"

    fun install(context: Context) {
        val app = context.applicationContext
        if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Nunca deixar uma falha ao gravar esconder o crash original.
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Acrescenta [lines] a um ficheiro de texto em Download/crashlogs/[name] (mesmo sítio dos
     * crashes), guardando só as últimas [maxLines]. Para diagnósticos «silenciosos» (ex.: log de
     * feedback do +1). Só em builds debuggable. Chamar fora da thread principal se possível.
     *
     * [name] tem de terminar em `.txt`: com MIME `text/plain` o MediaStore acrescenta `.txt` a
     * qualquer outra extensão (`x.log` → `x.log.txt`), a consulta pelo nome pedido deixava de
     * encontrar o ficheiro e cada chamada criava um novo (`x.log (1).txt`…) — bug da 0.5.0.
     */
    @Synchronized
    fun appendLines(context: Context, name: String, lines: List<String>, maxLines: Int) {
        require(name.endsWith(".txt")) { "O MediaStore renomeia para .txt; usar um nome .txt: $name" }
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

    /** Conteúdo antigo + novas linhas, só as últimas [maxLines], terminado em \n. */
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
            appendLine("quando:  ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now)}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("thread:  ${thread.name}")
            appendLine()
            append(trace)
        }.toByteArray()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: MediaStore, sem permissões. O ficheiro fica em /storage/emulated/0/Download/crashlogs/.
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { it.write(text) }
        } else {
            // Android 8–9: pasta privada da app (Android/data/<pkg>/files/crashlogs/), sem permissões.
            val dir = File(context.getExternalFilesDir(null), SUBDIR).apply { mkdirs() }
            File(dir, name).writeBytes(text)
        }
    }
}
