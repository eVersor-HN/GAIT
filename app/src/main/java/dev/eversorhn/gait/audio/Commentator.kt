package dev.eversorhn.gait.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** One switch for the spoken commentator (Settings → Voice). */
object VoicePrefs {
    private const val PREFS = "gait_voice_prefs"
    private const val KEY_ENABLED = "enabled"
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}

/**
 * The division's voice as a live commentator (docs/voice-design.md): Android TextToSpeech,
 * a female English voice where the device has one, pitch lifted a touch, natural rate. Ducks
 * the user's music for the length of the line and gives focus back. Lines come from
 * CommentaryScript; cadence from TrackViewModel. If the device has no TTS engine, every call
 * is a silent no-op — the on-screen comms are the primary channel anyway.
 */
class Commentator(context: Context) {

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var ready = false
    private var pending: String? = null
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { }
        .build()

    private val tts: TextToSpeech? = try {
        TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configure()
                ready = true
                pending?.let { speakNow(it) }
                pending = null
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun configure() {
        val t = tts ?: return
        t.setAudioAttributes(attrs)
        t.language = Locale.UK.takeIf { t.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.US
        // Prefer a female, local, English voice: names on Google's engine look like "en-gb-x-gba#female_2-local".
        val voice = runCatching { t.voices }.getOrNull()
            ?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            ?.sortedWith(compareBy(
                { if (it.name.contains("female", ignoreCase = true)) 0 else 1 },
                { if (it.locale == Locale.UK) 0 else 1 },
                { -it.quality },
            ))
            ?.firstOrNull()
        if (voice != null) runCatching { t.voice = voice }
        t.setPitch(1.08f)
        t.setSpeechRate(1.0f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { audio.abandonAudioFocusRequest(focusRequest) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { audio.abandonAudioFocusRequest(focusRequest) }
        })
    }

    /** Speaks [text]; a line arriving while another is playing replaces it — commentary is live, not a queue. */
    fun say(text: String) {
        if (tts == null) return
        if (!ready) { pending = text; return }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val t = tts ?: return
        audio.requestAudioFocus(focusRequest)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gait-${System.nanoTime()}")
    }

    fun stop() {
        runCatching { tts?.stop() }
        audio.abandonAudioFocusRequest(focusRequest)
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
    }
}
