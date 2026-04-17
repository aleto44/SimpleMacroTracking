package com.example.simplemacrotracking.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoskRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Primary + fallback mirrors for the small English Vosk model
        private val MODEL_URLS = listOf(
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "https://github.com/alphacep/vosk-api/releases/download/v0.3.45/vosk-model-small-en-us-0.15.zip"
        )
        private const val MODEL_DIR_NAME = "vosk-model"
        // File that only exists once the model is fully extracted
        private const val SENTINEL = "conf/model.conf"
    }

    val modelDir: File get() = File(context.filesDir, MODEL_DIR_NAME)

    val isModelReady: Boolean
        get() = File(modelDir, SENTINEL).exists()

    private var model: Model? = null
    private var speechService: SpeechService? = null

    // ── Download ──────────────────────────────────────────────────────────────

    sealed class DownloadState {
        data class Progress(val percent: Int) : DownloadState()
        object Done : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    fun downloadModel(): Flow<DownloadState> = flow {
        var lastError = ""
        for (url in MODEL_URLS) {
            try {
                val zipFile = File(context.cacheDir, "vosk-model.zip")

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.connect()
                val total = conn.contentLengthLong
                var downloaded = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                emit(DownloadState.Progress(((downloaded * 100) / total).toInt()))
                            }
                        }
                    }
                }

                // Unzip (strip the top-level folder in the zip)
                modelDir.deleteRecursively()
                modelDir.mkdirs()
                ZipInputStream(FileInputStream(zipFile)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val relativePath = entry.name.substringAfter('/')
                        if (relativePath.isNotEmpty()) {
                            val outFile = File(modelDir, relativePath)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { zis.copyTo(it) }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                zipFile.delete()
                emit(DownloadState.Done)
                return@flow  // success — stop trying mirrors
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                // try next mirror
            }
        }
        // All mirrors failed
        modelDir.deleteRecursively()
        emit(DownloadState.Error("No network connection or server unreachable.\n\nDetails: $lastError\n\nMake sure the device has internet access and try again."))
    }.flowOn(Dispatchers.IO)

    // ── Recognition ───────────────────────────────────────────────────────────

    /** Must be called off the main thread (model loading takes ~1-2 seconds). */
    fun initModel() {
        if (model == null && isModelReady) {
            model = Model(modelDir.absolutePath)
        }
    }

    fun startListening(listener: RecognitionListener) {
        val m = model ?: throw IllegalStateException("Model not initialised")
        val recognizer = Recognizer(m, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f)
        speechService!!.startListening(listener)
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
    }

    fun cancel() {
        speechService?.cancel()
        speechService = null
    }

    /** Extract plain text from Vosk's final result JSON: {"text": "add milk 100 grams"} */
    fun parseResult(json: String): String =
        Regex(""""text"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)?.trim() ?: ""

    /** Extract plain text from Vosk's partial result JSON: {"partial": "add milk"} */
    fun parsePartialResult(json: String): String =
        Regex(""""partial"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)?.trim() ?: ""
}



