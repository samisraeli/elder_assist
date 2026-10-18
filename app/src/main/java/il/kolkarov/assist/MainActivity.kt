package il.kolkarov.assist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model: CompanionModel by viewModels()
    private lateinit var speech: DeviceSpeech
    private lateinit var input: VoiceInput
    private var pendingCall: Person? = null
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) input.listen(model.profile.value.language) else model.status = "המיקרופון לא אושר. אפשר להשתמש בכפתורים או לאשר בהגדרות המכשיר"
    }
    private val speechDialog = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        model.listening = false
        val list = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.filter { it.isNotBlank() }.orEmpty()
        if (list.isNotEmpty()) {
            model.processingVoice = true
            model.commandCandidates(list)
        } else {
            model.processingVoice = false
            model.status = "מוכן להקשיב"
        }
    }
    private val phone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingCall?.let { person -> pendingCall = null; openCall(person,granted) }
    }
    private val contacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) { model.loadPhoneContacts(); model.status = "אנשי הקשר מהטלפון נטענו" }
        else model.status = "אין הרשאה לקריאת אנשי קשר מהטלפון"
    }
    private val calendars = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) { model.loadCalendars(); model.refreshNotices() } else model.status = "אין הרשאת יומן. תזכורות וימי הולדת עדיין זמינים"
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.status = if(granted) "התראות אושרו" else "ההתראות חסומות. ניתן לאשר בהגדרות המכשיר"
        model.refreshNotices()
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) model.runTask {
            model.importPreview = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { PeopleImport.xlsx(it,model.people.value) } ?: error("לא ניתן לפתוח את הקובץ")
            }
        }
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) model.runTask {
            val text = Backup.encode(model.profile.value,model.people.value,model.reminders.value,model.sources.value,model.medications.value)
            withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri,"wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("לא ניתן לשמור גיבוי") }
            model.status = "הגיבוי נשמר. הקובץ מכיל מידע אישי; שמרו אותו במקום פרטי"
        }
    }
    private val restoreFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) model.runTask {
            model.restorePreview = withContext(Dispatchers.IO) {
                val text = contentResolver.openInputStream(uri)?.use { stream ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    var count = stream.read(buffer)
                    while(count != -1) { require(out.size()+count <= 5_000_000) { "קובץ גדול מדי" }; out.write(buffer,0,count); count=stream.read(buffer) }
                    out.toString(Charsets.UTF_8.name())
                } ?: error("לא ניתן לקרוא את הגיבוי")
                Backup.decode(text)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = DeviceSpeech(this) { model.status = it }
        input = VoiceInput(
            context = this,
            state = { model.status = it },
            onListening = { model.listening = it },
            onProcessing = { model.processingVoice = it },
            fallbackDialog = { intent ->
                runCatching { speechDialog.launch(intent) }.onFailure {
                    model.listening = false
                    model.processingVoice = false
                    model.status = "אין שירות זיהוי דיבור במכשיר"
                }
            },
            result = { model.commandCandidates(it) }
        )
        model.output = speech
        model.dial = { person ->
            if(checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) openCall(person,true)
            else { pendingCall = person; phone.launch(Manifest.permission.CALL_PHONE) }
        }
        setContent {
            val profile by model.profile.collectAsState()
            CompanionTheme(profile) {
                CompanionUi(model,profile,speech,
                    listen = {
                        if(!model.busy) {
                            if(model.listening) {
                                input.cancel()
                                model.status = "מוכן להקשיב"
                            } else {
                                speech.stop()
                                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) input.listen(profile.language)
                                else microphone.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }, stop = { input.cancel(); model.stop() },
                    requestContacts = { contacts.launch(Manifest.permission.READ_CONTACTS) },
                    requestCalendar = { calendars.launch(Manifest.permission.READ_CALENDAR) },
                    requestNotifications = { if(Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else model.status = "ההתראות זמינות; אפשר לבדוק בהגדרות המכשיר" },
                    requestExact = { if(Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName"))) },
                    appSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) },
                    importFile = { importFile.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                    exportFile = { exportFile.launch("ezra-backup.json") },
                    restoreFile = { restoreFile.launch(arrayOf("application/json","text/plain","application/octet-stream")) },
                    openUrl = { url -> runCatching {
                        val uri = Uri.parse(url); require(uri.scheme in listOf("https","http"))
                        startActivity(Intent(Intent.ACTION_VIEW,uri))
                    }.onFailure { model.status = "לא ניתן לפתוח את הקישור" } }
                )
            }
        }
    }
    private fun openCall(person: Person, direct: Boolean) {
        if(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        runCatching { startActivity(Intent(if(direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,Uri.fromParts("tel",person.phone,null))) }
            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",person.phone,null))) }.onFailure { model.status = "אין יישום חיוג זמין במכשיר" } }
    }
    override fun onResume() { super.onResume(); if(::speech.isInitialized) model.output=speech; model.refreshNotices(); model.loadAgenda(); model.loadCalendars(); model.loadPhoneContacts() }
    override fun onStop() {
        super.onStop()
        if(::input.isInitialized) input.cancel()
        model.quietStop(); model.output = null
        model.confirmation = null
    }
    override fun onDestroy() {
        input.close(); speech.close(); model.output = null; model.dial = {}; super.onDestroy()
    }
}
