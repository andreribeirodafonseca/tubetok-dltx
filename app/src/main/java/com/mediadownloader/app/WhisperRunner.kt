package com.mediadownloader.app

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.runBlocking

/** Bridge to the actual on-device whisper.cpp implementation. */
object WhisperRunner {
    data class Segment(val startMs: Long, val endMs: Long, val text: String)
    data class Result(val text: String, val segments: List<Segment>)

    @JvmStatic
    fun transcribe(context: Context, modelPath: String, audioPath: String, language: String): Result = runBlocking {
        val model = Whisper.loadModel(context, modelPath)
        try {
            val config = WhisperConfig(language = if (language == "all") "auto" else language)
            val result = Whisper.transcribe(model, audioPath, config)
            Result(result.text, result.segments.map { Segment(it.startMs, it.endMs, it.text) })
        } finally {
            Whisper.releaseModel(model)
        }
    }
}
