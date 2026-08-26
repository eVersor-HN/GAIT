package dev.eversorhn.momentum.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale

/** One switch for the spoken commentator (Settings → Voice). */
object VoicePrefs {
    private const val PREFS = "momentum_voice_prefs"
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
class Commentator(context: Context, private val voice: VoiceFx.Voice = VoiceFx.Voice.DIVISION) {

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
        val engineVoice = runCatching { t.voices }.getOrNull()
            ?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            ?.sortedWith(compareBy(
                { if (voice == VoiceFx.Voice.HORDE) { if (it.name.contains("male", ignoreCase = true) && !it.name.contains("female", ignoreCase = true)) 0 else 1 } else if (it.name.contains("female", ignoreCase = true)) 0 else 1 },
                { if (it.locale == Locale.UK) 0 else 1 },
                { -it.quality },
            ))
            ?.firstOrNull()
        if (engineVoice != null) runCatching { t.voice = engineVoice }
        // Division: a young synthetic voice. Horde: as deep and slow as the engine allows —
        // the DSP does the rest (see docs/voice-design.md; the horde is its counterpart).
        if (voice == VoiceFx.Voice.HORDE) {
            t.setPitch(0.5f)
            t.setSpeechRate(0.78f)
        } else {
            t.setPitch(1.08f)
            t.setSpeechRate(1.0f)
        }
    }

    /** Speaks [text]; a line arriving while another is playing replaces it — commentary is live, not a queue. */
    fun say(text: String) {
        if (tts == null) return
        if (!ready) { pending = text; return }
        speakNow(text)
    }

    private var track: AudioTrack? = null
    @Volatile private var fxBusy = false

    /**
     * The voice-design chain (docs/voice-design.md): synthesize to WAV, run VoiceFx (high-pass,
     * presence, air, micro-doubling, soft limiter), play via AudioTrack. Any failure or overlap
     * falls back to the plain engine so a line is never lost.
     */
    private fun speakNow(text: String) {
        val t = tts ?: return
        audio.requestAudioFocus(focusRequest)
        if (fxBusy) { t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "momentum-${System.nanoTime()}"); return }
        fxBusy = true
        val file = File(appContext.cacheDir, "momentum_tts.wav")
        val id = "momentumfx-${System.nanoTime()}"
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId != id) { audio.abandonAudioFocusRequest(focusRequest); return }
                Thread {
                    try {
                        val wav = VoiceFx.readWav(file.readBytes())
                        if (wav != null) {
                            val (rate, pcm) = wav
                            val processed = VoiceFx.process(pcm, rate, voice)
                            track?.release()
                            val at = AudioTrack.Builder()
                                .setAudioAttributes(attrs)
                                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                                .setBufferSizeInBytes(processed.size * 2)
                                .setTransferMode(AudioTrack.MODE_STATIC)
                                .build()
                            at.write(processed, 0, processed.size)
                            at.play()
                            track = at
                            // Give focus back when the clip has played out.
                            val ms = processed.size * 1000L / rate + 150
                            Thread.sleep(ms)
                        }
                    } catch (e: Exception) {
                        // fall through — focus is released below either way
                    } finally {
                        fxBusy = false
                        audio.abandonAudioFocusRequest(focusRequest)
                    }
                }.start()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                fxBusy = false
                audio.abandonAudioFocusRequest(focusRequest)
                runCatching { t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "momentum-${System.nanoTime()}") }
            }
        })
        val ok = t.synthesizeToFile(text, null, file, id)
        if (ok != TextToSpeech.SUCCESS) {
            fxBusy = false
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "momentum-${System.nanoTime()}")
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        runCatching { track?.stop() }
        fxBusy = false
        audio.abandonAudioFocusRequest(focusRequest)
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
    }
}
