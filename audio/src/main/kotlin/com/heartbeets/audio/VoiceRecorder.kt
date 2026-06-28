package com.heartbeets.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Records voice messages to M4A files stored in the app's internal storage.
 * Files are saved under `recordings/{packId}/{index}.m4a`.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Start recording a voice message.
     * @param packId The sound pack ID (used as folder name).
     * @param index The message index.
     * @return The file path where the recording will be saved, or null on failure.
     */
    fun start(packId: String, index: Int): String? {
        if (isRecording) stop()

        val dir = File(context.filesDir, "recordings/$packId")
        dir.mkdirs()
        val file = File(dir, "$index.m4a")
        currentFile = file

        return try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            isRecording = true
            file.absolutePath
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to start recording", e)
            null
        }
    }

    /**
     * Stop the current recording.
     * @return The file path of the completed recording, or null if not recording.
     */
    fun stop(): String? {
        if (!isRecording) return null
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            currentFile?.absolutePath
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to stop recording", e)
            recorder?.release()
            recorder = null
            isRecording = false
            null
        }
    }

    /**
     * Delete a recording file.
     */
    fun deleteRecording(filePath: String) {
        File(filePath).delete()
    }

    /**
     * Delete all recordings for a pack.
     */
    fun deletePackRecordings(packId: String) {
        val dir = File(context.filesDir, "recordings/$packId")
        dir.deleteRecursively()
    }
}
