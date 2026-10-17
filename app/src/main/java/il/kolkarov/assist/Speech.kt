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

interface SpeechOutput { fun say(text: String, profile: Profile); fun stop(); fun resume() }
class DeviceSpeech(private val context: Context, private val state: (String) -> Unit) : SpeechOutput {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    var voices by mutableStateOf<List<Voice>>(emptyList()); private set
    private var chunks = emptyList<String>(); private var index = 0; private var generation = 0; private var speaking = false
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
        if (language < 0) { state("לא מותקן קול בשפה שנבחרה. התקינו בהגדרות המכשיר"); return }
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

class VoiceInput(private val context: Context, private val state: (String) -> Unit, private val result: (String) -> Unit) {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    fun listen(language: String) {
        val session = ++generation
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { state("אין שירות זיהוי דיבור במכשיר. אפשר להשתמש בכפתורים"); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if(session==generation) state("מקשיב… דברו עכשיו") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { if(session==generation) state("מעבד את הבקשה…") }
                override fun onError(error: Int) { if(session!=generation) return; state(when(error) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "זיהוי הדיבור זקוק לחיבור אינטרנט. נסו שוב"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "נדרשת הרשאת מיקרופון"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "לא שמעתי בבירור. לחצו ונסו שוב"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "המיקרופון עסוק. נסו שוב בעוד רגע"
                    else -> "זיהוי הדיבור לא הצליח. אפשר לנסות שוב או להשתמש בכפתורים"
                }) }
                override fun onResults(results: Bundle?) {
                    if(session!=generation) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) state("לא שמעתי בבירור") else result(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,language)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)
            })
        }
    }
    fun cancel() { generation++; recognizer?.cancel() }
    fun close() { generation++; recognizer?.destroy(); recognizer = null }
}
