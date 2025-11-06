// File: AudioRecorderManager.kt
package com.example.ee012

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * A robust class to manage audio recording using MediaRecorder.
 * This class handles initialization, starting, stopping, and cleanup
 * to prevent crashes related to improper state management.
 */
class AudioRecorderManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    companion object {
        private const val TAG = "AudioRecorderManager"
    }

    fun getOutputFile(): File? {
        return outputFile
    }

    /**
     * Safely releases the MediaRecorder instance.
     */
    private fun releaseRecorder() {
        if (recorder != null) {
            try {
                recorder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Exception while releasing recorder", e)
            } finally {
                recorder = null
            }
        }
    }

    /**
     * Starts a new audio recording.
     * @return The File object for the recording, or null if starting fails.
     */
    fun startRecording(): File? {
        if (recorder != null) {
            Log.w(TAG, "Recorder was already active. Releasing previous instance.")
            releaseRecorder()
        }

        outputFile = File(context.cacheDir, "final_voice_recording.mp3")

        val currentRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.also { recorder = it }

        try {
            currentRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            currentRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            currentRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            currentRecorder.setOutputFile(outputFile!!.absolutePath)

            currentRecorder.prepare()
            currentRecorder.start()

            Log.d(TAG, "MediaRecorder started successfully.")
            return outputFile

        } catch (e: IOException) {
            Log.e(TAG, "MediaRecorder prepare() failed", e)
            releaseRecorder()
            return null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaRecorder start() failed, likely due to a state issue.", e)
            releaseRecorder()
            return null
        }
    }

    /**
     * Stops the current audio recording and releases resources.
     */
    fun stopRecording() {
        if (recorder == null) {
            return // Nothing to stop
        }

        try {
            recorder?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException on stop(). Recorder may have already been stopped.", e)
        } finally {
            releaseRecorder()
            Log.d(TAG, "MediaRecorder stopped and released.")
        }
    }
}
