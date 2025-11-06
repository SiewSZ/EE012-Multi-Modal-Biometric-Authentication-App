package com.example.ee012

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.annotation.RequiresPermission
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class VoiceUploadManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var outputFile: File? = null

    // Property to store the buffer size
    private var bufferSizeInBytes: Int = 0

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(): Boolean {
        // Calculate and store the buffer size
        bufferSizeInBytes = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

        // Check if bufferSize is valid, including for non-positive values which can cause crashes
        if (bufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE || bufferSizeInBytes <= 0) {
            return false // Indicate that recording could not start
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes // Use the stored value
        )

        // Use the context to access the cache directory
        outputFile = File.createTempFile("recording_raw", ".pcm", context.cacheDir)

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            writeAudioDataToFile(outputFile!!)
        }
        recordingThread?.start()
        return true
    }

    fun stopRecordingAndUpload(userUid: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (!isRecording) return
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.join()

        if (outputFile == null) {
            onFailure("Recording file was not created.")
            return
        }

        val wavFile = File.createTempFile("voice_recording", ".wav", context.cacheDir)
        addWavHeader(outputFile!!, wavFile, 1, 16000, 16)

        val storageRef = FirebaseStorage.getInstance().reference
        val voiceRef = storageRef.child("voice_recordings/${userUid}-voice.wav")

        voiceRef.putFile(Uri.fromFile(wavFile))
            .addOnSuccessListener {
                wavFile.delete()
                outputFile?.delete()
                onSuccess()
            }
            .addOnFailureListener { exception ->
                wavFile.delete()
                outputFile?.delete()
                onFailure(exception.message ?: "Unknown upload error")
            }
    }

    fun isRecording(): Boolean {
        return isRecording
    }

    fun release() {
        if (isRecording) {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.interrupt() // Stop the thread
            recordingThread = null
        }
    }
    private fun writeAudioDataToFile(file: File) {
        val currentAudioRecord = audioRecord ?: return
        // Use the stored class property for the buffer size
        val data = ByteArray(bufferSizeInBytes)

        try {
            val fileOutputStream = FileOutputStream(file)
            while (isRecording) {
                val read = currentAudioRecord.read(data, 0, data.size)
                if (read > 0) { // Check for a valid number of bytes read
                    fileOutputStream.write(data, 0, read)
                }
            }
            fileOutputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun addWavHeader(inPcmFile: File, outWavFile: File, channels: Int, sampleRate: Int, bitDepth: Int) {
        val pcmData = inPcmFile.readBytes()
        val pcmDataSize = pcmData.size
        val wavFileSize = pcmDataSize + 36 // 44-byte header minus 8 bytes for RIFF chunk
        val byteRate = sampleRate * channels * bitDepth / 8
        val blockAlign = channels * bitDepth / 8

        FileOutputStream(outWavFile).use { out ->
            // RIFF chunk
            out.write("RIFF".toByteArray())
            out.write(wavFileSize.toLittleEndianBytes(4))
            out.write("WAVE".toByteArray())

            // "fmt " sub-chunk
            out.write("fmt ".toByteArray())
            out.write(16.toLittleEndianBytes(4)) // Sub-chunk size (16 for PCM)
            out.write(1.toLittleEndianBytes(2))  // Audio format (1 for PCM)
            out.write(channels.toLittleEndianBytes(2))
            out.write(sampleRate.toLittleEndianBytes(4))
            out.write(byteRate.toLittleEndianBytes(4))
            out.write(blockAlign.toLittleEndianBytes(2))
            out.write(bitDepth.toLittleEndianBytes(2))

            // "data" sub-chunk
            out.write("data".toByteArray())
            out.write(pcmDataSize.toLittleEndianBytes(4))

            // Audio data
            out.write(pcmData)
        }
    }

    // Helper extension to write integers in little-endian format
    private fun Int.toLittleEndianBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        for (i in 0 until size) {
            bytes[i] = (this shr (i * 8) and 0xFF).toByte()
        }
        return bytes
    }
}
