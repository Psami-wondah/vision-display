package com.psami.visiondisplay.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechController(
    context: Context
) : TextToSpeech.OnInitListener,
    AutoCloseable {

    private var textToSpeech:
            TextToSpeech? = null

    private var isReady =
        false

    private var pendingText:
            String? = null

    init {
        textToSpeech =
            TextToSpeech(
                context.applicationContext,
                this
            )
    }

    override fun onInit(
        status: Int
    ) {
        if (
            status !=
            TextToSpeech.SUCCESS
        ) {
            return
        }

        val tts =
            textToSpeech
                ?: return

        val preferredLocale =
            Locale.getDefault()

        val languageResult =
            tts.setLanguage(
                preferredLocale
            )

        if (
            languageResult ==
            TextToSpeech.LANG_MISSING_DATA ||
            languageResult ==
            TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.setLanguage(
                Locale.UK
            )
        }

        tts.setSpeechRate(
            1.0f
        )

        isReady =
            true

        pendingText
            ?.let {
                speak(it)
            }

        pendingText =
            null
    }

    fun speak(
        text: String
    ) {
        if (
            text.isBlank()
        ) {
            return
        }

        if (
            !isReady
        ) {
            /*
             * Keep only the latest feedback.
             * We don't want stale messages
             * queued during TTS startup.
             */
            pendingText =
                text

            return
        }

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            UTTERANCE_ID
        )
    }

    fun stop() {
        textToSpeech?.stop()
    }

    override fun close() {
        isReady =
            false

        pendingText =
            null

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        textToSpeech =
            null
    }

    private companion object {
        const val UTTERANCE_ID =
            "vision_assist_feedback"
    }
}