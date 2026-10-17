package il.kolkarov.assist

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

enum class Screen(val title: String) { HOME("הבית שלי"), NEWS("חדשות"), AGENDA("מה קרוב"), CALLS("התקשרות"), MEDICATIONS("התרופות שלי"), SETTINGS("הגדרות למשפחה") }
data class Confirmation(val text: String, val action: suspend () -> Unit)

class CompanionModel(application: Application) : AndroidViewModel(application) {
    val app = application as CompanionApp
    val dao = app.database.dao()
    val profile = app.profiles.flow.stateIn(viewModelScope,SharingStarted.Eagerly,Profile())
    val people = dao.people().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val reminders = dao.reminders().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val sources = dao.sources().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val medications = dao.medications().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val calendar: CalendarProvider = DeviceCalendar(app)
    val deviceContacts = DeviceContacts(app)
    val weather: WeatherProvider = OpenMeteo()
    val news: NewsProvider = RssNews()
    var screen by mutableStateOf(Screen.HOME)
    var status by mutableStateOf("מוכן להקשיב")
    var listening by mutableStateOf(false)
    var transcript by mutableStateOf("")
    var answer by mutableStateOf("")
    var spokenAnswer by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var confirmation by mutableStateOf<Confirmation?>(null)
    var callChoices by mutableStateOf<List<Person>>(emptyList())
    var phonePeople by mutableStateOf<List<Person>>(emptyList())
    var agenda by mutableStateOf<List<AgendaItem>>(emptyList())
    var newsResult by mutableStateOf<NewsResult?>(null)
    var topic by mutableStateOf(Topic.GENERAL)
    var importPreview by mutableStateOf<ImportPreview?>(null)
    var restorePreview by mutableStateOf<BackupData?>(null)
    var places by mutableStateOf<List<Place>>(emptyList())
    var calendarChoices by mutableStateOf<List<CalendarChoice>>(emptyList())
    private var task: Job? = null
    var output: SpeechOutput? = null
    var dial: (Person) -> Unit = {}
    private var lastWeather: Weather? = null

    init {
        viewModelScope.launch {
            combine(profile,people,reminders,medications) { _,_,_,_ -> Unit }.collectLatest {
                delay(150)
                runCatching { agenda = agendaNow(); NoticeScheduler(app).refresh() }
            }
        }
    }

    fun allCallablePeople(): List<Person> {
        val localWithPhone = people.value.filter { it.phone.isNotBlank() }
        if (!profile.value.enablePhoneContacts) return localWithPhone
        val seen = localWithPhone.map { "${normalized(it.name)}|${it.phone}" }.toMutableSet()
        val extra = phonePeople.filter { it.phone.isNotBlank() && seen.add("${normalized(it.name)}|${it.phone}") }
        return (localWithPhone + extra).sortedBy { it.name }
    }

    fun loadPhoneContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = runCatching { deviceContacts.load() }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { phonePeople = loaded }
        }
    }

    fun runTask(action: suspend () -> Unit) {
        if (busy) return
        task = viewModelScope.launch {
            busy = true
            try { action() } catch (e: CancellationException) { throw e }
              catch (e: Exception) { respond(friendlyError(e),false); status = "לא ניתן להשלים כרגע" }
            finally { busy = false }
        }
    }
    fun respond(text: String, speak: Boolean = true) {
        answer = text
        spokenAnswer = speak
        if (speak) output?.say(text,profile.value)
    }
    private fun friendlyError(e: Exception): String = when(e) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "אין חיבור לשירות. בדקו את האינטרנט ונסו שוב"
        is java.net.SocketTimeoutException -> "השירות לא ענה בזמן. נסו שוב בעוד רגע"
        is SecurityException -> "חסרה הרשאה במכשיר. אפשר לאשר אותה בהגדרות המשפחה"
        is org.json.JSONException -> "השירות או הקובץ החזירו מידע שאינו תקין"
        else -> e.message?.takeIf { it.any { c -> c in 'א'..'ת' } } ?: "הפעולה לא הצליחה. בדקו את החיבור ואת הקובץ ונסו שוב"
    }
    fun stop() { task?.cancel(); listening = false; output?.stop(); status = "ההקראה נעצרה" }
    fun ask(text: String, action: suspend () -> Unit) { confirmation = Confirmation(text,action); respond(text) }
    fun confirm() {
        val pending = confirmation ?: return
        if (busy) return
        confirmation = null; output?.stop()
        runTask { pending.action() }
    }
    fun cancel() { confirmation = null; callChoices = emptyList(); listening = false; output?.stop(); status = "הפעולה בוטלה" }
    fun navigate(to: Screen) {
        confirmation = null; callChoices = emptyList(); listening = false; output?.stop()
        answer = ""; spokenAnswer = false; status = "מוכן להקשיב"
        screen = to
        if (to == Screen.AGENDA) loadAgenda()
        if (to == Screen.CALLS) loadPhoneContacts()
        if (to == Screen.SETTINGS) { loadCalendars(); loadPhoneContacts() }
    }

    fun command(text: String) {
        transcript = text
        val trimmed = normalized(text)
        if (confirmation != null) {
            val command = CommandParser.parse(text,LocalDateTime.now(ZoneId.of(profile.value.zone)))
            when(command) { Command.Confirm -> confirm(); Command.Cancel, Command.Stop -> cancel(); else -> respond("ממתינה פעולה לאישור. אמרו כן או ביטול") }
            return
        }
        val command = CommandParser.parse(text,LocalDateTime.now(ZoneId.of(profile.value.zone)))
        if (screen == Screen.CALLS && command is Command.Clarify && trimmed.isNotBlank()) {
            findCall(text)
            return
        }
        when(command) {
            Command.Confirm -> respond("אין פעולה שממתינה לאישור")
            Command.Cancel -> cancel()
            Command.Stop -> stop()
            Command.Repeat -> output?.say(answer,profile.value)
            Command.Morning -> morning()
            Command.WeatherNow -> getWeather()
            Command.Medications -> { screen=Screen.MEDICATIONS; loadAgenda(); respond(medicationText()) }
            is Command.News -> loadNews(command.topic,true)
            is Command.Agenda -> loadAgenda(true,command.birthdaysOnly,command.todayOnly)
            is Command.Call -> findCall(command.name)
            is Command.Remind -> requestReminder(command.title,command.at)
            is Command.CancelReminder -> {
                val matches = reminders.value.filter { !it.delivered && normalized(it.title).contains(normalized(command.title)) }
                if(command.title.isBlank() || matches.size != 1) { screen = Screen.AGENDA; loadAgenda(); respond("בחרו תזכורת מהרשימה כדי לבטל אותה") }
                else requestDeleteReminder(matches.single())
            }
            is Command.Clarify -> {
                val candidates = allCallablePeople()
                val directMatch = if (trimmed.length >= 2) candidates.filter { p ->
                    (listOf(p.name) + p.aliases.split(',', ';')).any { it.isNotBlank() && normalized(it) == trimmed }
                } else emptyList()
                if (directMatch.isNotEmpty()) findCall(text) else respond(command.message)
            }
        }
    }
    fun findCall(name: String) {
        screen = Screen.CALLS
        val target = normalized(name).replace(Regex("^(אל |ל)"), "").trim()
        val candidates = allCallablePeople()
        val exact = candidates.filter { p -> (listOf(p.name) + p.aliases.split(',', ';')).any { it.isNotBlank() && normalized(it) == target } }
        val matches = exact.ifEmpty { candidates.filter { p -> (listOf(p.name) + p.aliases.split(',', ';')).any { it.isNotBlank() && (normalized(it).contains(target) || target.contains(normalized(it))) } } }
        when(matches.size) {
            0 -> respond("לא נמצא איש קשר בשם $name", false)
            1 -> requestCall(matches.single())
            else -> { callChoices = matches; respond("נמצאו ${matches.size} אנשי קשר עבור $name. לחצו על השם הרצוי") }
        }
    }
    fun requestCall(person: Person) {
        callChoices = emptyList()
        if(person.phone.isBlank()) { respond("לא הוגדר מספר טלפון עבור ${person.name}"); return }
        ask("להתקשר ל${person.name}, למספר ${person.phone}?") { dial(person) }
    }
    fun requestReminder(title: String, at: LocalDateTime) {
        val zone = ZoneId.of(profile.value.zone)
        if(title.isBlank() || !at.isAfter(LocalDateTime.now(zone))) { respond("יש להזין תיאור ומועד עתידי"); return }
        ask("להזכיר לך ${title.trim()} ב־${at.format(displayTime)}?") {
            dao.saveReminder(Reminder(title=title.trim(),at=at.atZone(zone).toInstant().toEpochMilli()))
            NoticeScheduler(app).refresh(); respond("התזכורת נשמרה"); loadAgenda()
        }
    }
    fun requestDeleteReminder(reminder: Reminder) {
        ask("לבטל את התזכורת ${reminder.title}?") { dao.deleteReminder(reminder); NoticeScheduler(app).refresh(); respond("התזכורת בוטלה"); loadAgenda() }
    }
    private suspend fun agendaNow(): List<AgendaItem> = withContext(Dispatchers.IO) {
        val p = profile.value; val zone = ZoneId.of(p.zone); val today = LocalDate.now(zone)
        (MedicationSchedule.agenda(dao.medicationsNow(),today,zone) + birthdayAgenda(dao.peopleNow(),today,zone) + calendar.events(p,today) + dao.remindersNow().filter { !it.delivered }.map {
            AgendaItem("reminder:${it.id}",it.title,Instant.ofEpochMilli(it.at).atZone(zone),"תזכורת")
        }.filter { it.time.toLocalDate() >= today && it.time.toLocalDate() < today.plusDays(7) }).sortedBy { it.time }
    }
    private fun agendaText(items: List<AgendaItem>): String = if(items.isEmpty()) "אין אירועים בטווח שנבחר" else items.joinToString(". ") {
        "${it.title}, ${it.time.toLocalDate().format(displayDate)}${if(it.allDay) "" else " בשעה ${it.time.toLocalTime()}"}"
    }
    fun loadAgenda(speak: Boolean = false, birthdays: Boolean = false, todayOnly: Boolean = false) {
        viewModelScope.launch {
            try {
                agenda = agendaNow()
                if(speak) { screen = Screen.AGENDA; val today = LocalDate.now(ZoneId.of(profile.value.zone))
                    respond(agendaText(agenda.filter { (!birthdays || it.kind == "יום הולדת") && (!todayOnly || it.time.toLocalDate() == today) })) }
            } catch (_: Exception) { status = "לא ניתן לקרוא את היומן. בדקו הרשאות" }
        }
    }
    fun loadCalendars() { viewModelScope.launch(Dispatchers.IO) {
        val list = runCatching { calendar.choices() }.getOrDefault(emptyList())
        withContext(Dispatchers.Main) { calendarChoices = list }
    } }
    fun saveProfile(p: Profile) { runTask { app.profiles.save(p); NoticeScheduler(app).refresh(); status = "ההגדרות נשמרו" } }
    fun searchPlaces(query: String) { runTask { places = weather.places(query); if(places.isEmpty()) status = "לא נמצאו ערים. נסו איות אחר" } }
    fun getWeather() { runTask {
        try { lastWeather = weather.current(profile.value); respond(lastWeather!!.text + " הנתונים מאת Open-Meteo.") }
        catch (e: Exception) {
            val cached = lastWeather
            if(cached != null) respond("אין מידע עדכני. מידע קודם מ־${cached.fetched.atZone(ZoneId.of(profile.value.zone)).format(displayTime)}: ${cached.text}")
            else throw e
        }
    } }
    fun loadNews(selected: Topic, speak: Boolean = false) { topic = selected; screen = Screen.NEWS; runTask {
        newsResult = null
        newsResult = news.fetch(sources.value,selected)
        if(speak) respond(newsResult!!.items.take(3).joinToString(". ") { "${it.source}: ${it.title}. ${it.summary}" })
    } }
    fun morning() { runTask {
        val parts = mutableListOf("בוקר טוב ${profile.value.name}")
        parts += runCatching { weather.current(profile.value).text }.getOrElse { "מזג האוויר אינו זמין כרגע" }
        agenda = agendaNow(); val today = LocalDate.now(ZoneId.of(profile.value.zone))
        parts += agendaText(agenda.filter { it.time.toLocalDate() == today || it.kind == "יום הולדת" })
        parts += runCatching { news.fetch(sources.value,Topic.GENERAL).items.take(3).joinToString(". ") { "${it.source}: ${it.title}" } }.getOrElse { "החדשות אינן זמינות כרגע" }
        respond(parts.joinToString(". "))
    } }
    fun savePerson(person: Person) { runTask { dao.savePeople(listOf(person)); NoticeScheduler(app).refresh(); status = "איש הקשר נשמר" } }
    fun medicationText(): String = medications.value.filter { it.enabled }.let { items ->
        if(items.isEmpty()) "עדיין לא הוגדרה רשימת תרופות. ניתן להוסיף אותה בהגדרות המשפחה" else items.joinToString(". ") { med ->
            med.name + ": " + MedicationSchedule.decode(med.slots).joinToString(", ") { "${it.part.label} בשעה ${it.time}" } + if(med.instructions.isBlank()) "" else ". ${med.instructions}"
        }
    }
    fun requestMedication(medication: Medication) {
        require(medication.name.isNotBlank()); MedicationSchedule.decode(medication.slots)
        val times=MedicationSchedule.decode(medication.slots).joinToString(", ") { "${it.part.label} ${it.time}" }
        val action = if(medication.enabled) "לשמור תזכורות יומיות" else "לשמור עם תזכורות מושהות"
        ask("$action ל${medication.name}: $times? ${medication.instructions}") {
            dao.saveMedication(medication); NoticeScheduler(app).refresh(); status="התרופה והתזכורות נשמרו"; respond("הרשימה עודכנה")
        }
    }
    fun deleteMedication(medication: Medication) { ask("למחוק את ${medication.name} ולבטל את התזכורות שלה?") {
        dao.deleteMedication(medication); NoticeScheduler(app).refresh(); status="התרופה הוסרה"
    } }
    fun deletePerson(person: Person) { ask("למחוק את ${person.name} מהרשימה?") { dao.deletePerson(person); NoticeScheduler(app).refresh(); status = "איש הקשר נמחק" } }
    fun saveSource(name: String, url: String, topic: Topic) { runTask {
        val source = NewsSource(name=name.trim(),url=url.trim(),topic=topic.name)
        require(source.name.isNotBlank()) { "יש להזין שם מקור" }
        withContext(Dispatchers.IO) { RssNews().parse(Web.get(source.url),source) }
        dao.saveSource(source); status = "מקור החדשות נבדק ונשמר"
    } }
    fun deleteSource(source: NewsSource) { ask("להסיר את המקור ${source.name}?") { dao.deleteSource(source) } }
    fun previewPaste(text: String) { runTask { importPreview = withContext(Dispatchers.Default) { PeopleImport.pasted(text,people.value) } } }
    fun acceptImport() { val preview = importPreview ?: return; runTask {
        val current = dao.peopleNow().map { normalized(it.name) + "|" + it.phone }.toSet()
        val accepted = preview.accepted.filter { normalized(it.name) + "|" + it.phone !in current }
        dao.savePeople(accepted); importPreview = null; NoticeScheduler(app).refresh(); status = "יובאו ${accepted.size} רשומות"
    } }
    fun refreshNotices() { viewModelScope.launch { runCatching { NoticeScheduler(app).refresh() } } }
}
