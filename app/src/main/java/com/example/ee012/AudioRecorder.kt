package com.example.ee012

import java.io.File

interface AudioRecorder {
    fun start(outputFile: File)
    fun stop()
}