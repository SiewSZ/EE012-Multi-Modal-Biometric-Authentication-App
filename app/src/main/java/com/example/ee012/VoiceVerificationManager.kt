package com.example.ee012

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class VoiceVerificationManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var bufferSizeInBytes: Int = 0

    fun isRecording(): Boolean {
        return isRecording
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(): File? {
        bufferSizeInBytes = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

        if (bufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE || bufferSizeInBytes <= 0) {
            return null // Indicate failure
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
        )

        val outputFile = File.createTempFile("verification_raw", ".pcm", context.cacheDir)

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            writeAudioDataToFile(outputFile)
        }
        recordingThread?.start()
        return outputFile
    }

    fun stopRecording(rawPcmFile: File): File {
        if (!isRecording) throw IllegalStateException("Recording was not started.")
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.join() // Wait for thread to finish writing

        // Create the final .wav file
        val wavFile = File.createTempFile("verification_final", ".wav", context.cacheDir)
        addWavHeader(rawPcmFile, wavFile, 1, 16000, 16)
        rawPcmFile.delete() // Clean up raw file
        return wavFile
    }

    fun release() {
        if (isRecording) {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.interrupt()
            recordingThread = null
        }
    }

    private fun writeAudioDataToFile(file: File) {
        val currentAudioRecord = audioRecord ?: return
        val data = ByteArray(bufferSizeInBytes)
        try {
            FileOutputStream(file).use { outStream ->
                while (isRecording) {
                    val read = currentAudioRecord.read(data, 0, data.size)
                    if (read > 0) {
                        outStream.write(data, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun addWavHeader(inPcmFile: File, outWavFile: File, channels: Int, sampleRate: Int, bitDepth: Int) {
        val pcmData = inPcmFile.readBytes()
        val pcmDataSize = pcmData.size
        val wavFileSize = pcmDataSize + 36
        val byteRate = sampleRate * channels * bitDepth / 8
        val blockAlign = channels * bitDepth / 8

        FileOutputStream(outWavFile).use { out ->
            out.write("RIFF".toByteArray())
            out.write(wavFileSize.toLittleEndianBytes(4))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(16.toLittleEndianBytes(4))
            out.write(1.toLittleEndianBytes(2))
            out.write(channels.toLittleEndianBytes(2))
            out.write(sampleRate.toLittleEndianBytes(4))
            out.write(byteRate.toLittleEndianBytes(4))
            out.write(blockAlign.toLittleEndianBytes(2))
            out.write(bitDepth.toLittleEndianBytes(2))
            out.write("data".toByteArray())
            out.write(pcmDataSize.toLittleEndianBytes(4))
            out.write(pcmData)
        }
    }

    private fun Int.toLittleEndianBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        for (i in 0 until size) {
            bytes[i] = (this shr (i * 8) and 0xFF).toByte()
        }
        return bytes
    }
}
