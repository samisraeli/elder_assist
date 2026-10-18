package il.kolkarov.assist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.os.Handler
import android.os.Looper
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

interface SpeechOutput { val speaking: Boolean; fun say(text: String, profile: Profile); fun stop(); fun resume() }
class DeviceSpeech(private val context: Context, private val state: (String) -> Unit) : SpeechOutput {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    var voices by mutableStateOf<List<Voice>>(emptyList()); private set
    private var chunks = emptyList<String>(); private var index = 0; private var generation = 0
    override var speaking by mutableStateOf(false); private set
    private var lastProfile = Profile()
    init {
        tts = TextToSpeech(context) { status -> handler.post {
            ready = status == TextToSpeech.SUCCESS
            voices = if (ready) tts?.voices?.sortedBy { it.name } ?: emptyList() else emptyList()
            if (!ready) state("מנוע ההקראה אינו זמין. בדקו את הגדרות הדיבור במכשיר")
        } }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { handler.post { if (id == "$generation:$index") state("מדבר") } }
            override fun onDone(id: String?) { handler.post { if (speaking && id == "$generation:$index") { index++; speakChunk() } } }
            @Deprecated("Android callback") override fun onError(id: String?) { handler.post { if(id == "$generation:$index") { speaking = false; state("לא ניתן להקריא. בדקו שקול בשפה הנבחרת מותקן") } } }
        })
    }
    override fun say(text: String, profile: Profile) {
        if (!ready) { state("מנוע ההקראה עדיין אינו מוכן. אפשר ללחוץ על הקראה שוב"); return }
        stop(); lastProfile = profile
        val language = tts?.setLanguage(Locale.forLanguageTag(profile.language)) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (language < 0) { state("לא מותקן קול בשפה שנבחרת. התקינו בהגדרות המכשיר"); return }
        voices.firstOrNull { it.name == profile.voice && it.locale.language == Locale.forLanguageTag(profile.language).language }?.let { tts?.voice = it }
        tts?.setSpeechRate(profile.rate)
        chunks = text.split(Regex("(?<=[.!?\\n])\\s+")).flatMap { it.chunked(350) }.filter { it.isNotBlank() }
        index = 0; speaking = true; speakChunk()
    }
    private fun speakChunk() {
        if (index >= chunks.size) { speaking = false; state("מוכן להקשיב"); return }
        tts?.speak(chunks[index],TextToSpeech.QUEUE_FLUSH,null,"$generation:$index")
    }
    override fun stop() { speaking = false; generation++; tts?.stop() }
    override fun resume() { if (index < chunks.size) { generation++; speaking = true; speakChunk() } }
    fun close() { stop(); tts?.shutdown() }
}

class VoiceInput(
    private val context: Context,
    private val state: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onProcessing: (Boolean) -> Unit = {},
    private val fallbackDialog: (Intent) -> Unit,
    private val result: (List<String>) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

    private fun ensureRecognizer(): SpeechRecognizer {
        recognizer?.let { return it }
        return SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also { rec ->
            recognizer = rec
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    val s = generation
                    handler.post { if (s == generation) { onProcessing(false); onListening(true); state("מקשיב… דברו עכשיו") } }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    val s = generation
                    handler.post { if (s == generation) { onListening(false); onProcessing(true); state("בודק…") } }
                }
                override fun onError(error: Int) {
                    val s = generation
                    handler.post {
                        if (s != generation) return@post
                        onListening(false)
                        onProcessing(false)
                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            runCatching { rec.cancel() }
                        }
                        if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == 11 || error == 12 || error == 13) {
                            fallbackDialog(buildIntent(lastLanguage))
                            return@post
                        }
                        state(when (error) {
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "זיהוי הדיבור זקוק לחיבור אינטרנט"
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "לא שמעתי בבירור. לחצו ונסו שוב"
                            else -> "לא שמעתי בבירור. לחצו ונסו שוב"
                        })
                    }
                }
                override fun onResults(results: Bundle?) {
                    val s = generation
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.filter { it.isNotBlank() }.orEmpty()
                    handler.post {
                        if (s != generation) return@post
                        onListening(false)
                        if (list.isEmpty()) {
                            onProcessing(false)
                            state("לא שמעתי בבירור")
                        } else {
                            onProcessing(true)
                            result(list)
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private var lastLanguage: String = "he-IL"
    private fun buildIntent(language: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "דברו עכשיו...")
    }

    fun listen(language: String) {
        lastLanguage = language
        val session = ++generation
        val speechIntent = buildIntent(language)
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onListening(false)
            onProcessing(false)
            fallbackDialog(speechIntent)
            return
        }
        onProcessing(false)
        onListening(true)
        state("מקשיב… דברו עכשיו")
        handler.post {
            if (session != generation) return@post
            runCatching {
                val rec = ensureRecognizer()
                rec.cancel()
                rec.startListening(speechIntent)
            }.onFailure {
                onListening(false)
                onProcessing(false)
                fallbackDialog(speechIntent)
            }
        }
    }
    fun cancel() { generation++; onListening(false); onProcessing(false); handler.post { runCatching { recognizer?.cancel() } } }
    fun close() { generation++; onListening(false); onProcessing(false); handler.post { runCatching { recognizer?.destroy() }; recognizer = null } }
}

