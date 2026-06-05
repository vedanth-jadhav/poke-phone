package co.interaction.pokephone.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

data class AudioNoteFile(
    val file: File,
    val durationMs: Long
)

class AudioNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun start() {
        val notesDir = File(context.cacheDir, "voice-notes").also { it.mkdirs() }
        val file = File(notesDir, "poke-${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = mediaRecorder
        outputFile = file
        startedAtMs = System.currentTimeMillis()
    }

    fun maxAmplitude(): Int = runCatching {
        recorder?.maxAmplitude ?: 0
    }.getOrDefault(0)

    fun stop(): AudioNoteFile {
        val currentRecorder = recorder ?: error("Recorder was not started")
        val currentFile = outputFile ?: error("Output file was not set")
        val durationMs = System.currentTimeMillis() - startedAtMs
        recorder = null
        outputFile = null

        runCatching { currentRecorder.stop() }
        currentRecorder.reset()
        currentRecorder.release()

        return AudioNoteFile(currentFile, durationMs)
    }

    fun cancel() {
        val currentRecorder = recorder
        val currentFile = outputFile
        recorder = null
        outputFile = null

        if (currentRecorder != null) {
            runCatching { currentRecorder.stop() }
            runCatching { currentRecorder.reset() }
            runCatching { currentRecorder.release() }
        }
        currentFile?.delete()
    }
}
