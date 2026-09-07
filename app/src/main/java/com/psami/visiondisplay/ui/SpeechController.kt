package com.psami.visiondisplay.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.speech.tts.UtteranceProgressListener

class SpeechController(
    context: Context,
    private val onSpeakingChanged:
        (Boolean) -> Unit
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

        tts.setOnUtteranceProgressListener(
            object :
                UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) {
                    onSpeakingChanged(
                        true
                    )
                }

                override fun onDone(
                    utteranceId: String?
                ) {
                    onSpeakingChanged(
                        false
                    )
                }

                @Deprecated(
                    "Deprecated by Android"
                )
                override fun onError(
                    utteranceId: String?
                ) {
                    onSpeakingChanged(
                        false
                    )
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    onSpeakingChanged(
                        false
                    )
                }
            }
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
        onSpeakingChanged(
            false
        )
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
        onSpeakingChanged(
            false
        )
    }

    private companion object {
        const val UTTERANCE_ID =
            "vision_assist_feedback"
    }
}