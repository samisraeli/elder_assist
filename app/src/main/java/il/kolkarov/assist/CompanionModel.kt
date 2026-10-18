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
    var voiceSearchQuery by mutableStateOf("")
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
        val combined = if (!profile.value.enablePhoneContacts) {
            localWithPhone
        } else {
            val seen = localWithPhone.map { "${normalized(it.name)}|${it.phone}" }.toMutableSet()
            val extra = phonePeople.filter { it.phone.isNotBlank() && seen.add("${normalized(it.name)}|${it.phone}") }
            localWithPhone + extra
        }
        return combined.sortedWith(compareByDescending<Person> { it.favorite }.thenBy { it.name.lowercase() })
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
    fun stop() { task?.cancel(); listening = false; output?.stop(); status = "מוכן להקשיב" }
    fun quietStop() { task?.cancel(); listening = false; output?.stop(); status = "מוכן להקשיב" }
    fun ask(text: String, action: suspend () -> Unit) { confirmation = Confirmation(text,action); respond(text) }
    fun confirm() {
        val pending = confirmation ?: return
        if (busy) return
        confirmation = null; output?.stop()
        runTask { pending.action() }
    }
    fun cancel() { confirmation = null; callChoices = emptyList(); voiceSearchQuery = ""; listening = false; output?.stop(); status = "מוכן להקשיב" }
    fun navigate(to: Screen) {
        confirmation = null; callChoices = emptyList(); voiceSearchQuery = ""; listening = false; output?.stop()
        answer = ""; spokenAnswer = false; status = "מוכן להקשיב"
        screen = to
        if (to == Screen.AGENDA) loadAgenda()
        if (to == Screen.CALLS && phonePeople.isEmpty()) loadPhoneContacts()
        if (to == Screen.SETTINGS) { loadCalendars(); if (phonePeople.isEmpty()) loadPhoneContacts() }
    }

    private val callVerbRegex = Regex("^(התקשר|תתקשר|התקשרי|תתקשרי|להתקשר|חייג|תחייג|חייגי|תחייגי|לחייג|שיחה|לדבר|טלפן|תטלפן)( עם| אל)?( ל)? ?")

    fun matchPeople(rawQueries: List<String>, candidates: List<Person> = allCallablePeople()): List<Person> {
        val baseQueries = rawQueries.map {
            normalized(it).replaceFirst(callVerbRegex, "")
                .replace(Regex("^(עם |אל |ל )"), "").trim()
        }.filter { it.isNotBlank() }

        // Expand queries to handle Hebrew 'ל' prefix ("לאלעד" -> "אלעד", and if speech dropped 'א'/'ע' after 'ל': "לעד" -> "אלעד"/"עלעד")
        val queries = baseQueries.flatMap { q ->
            val withoutLamed = if (q.startsWith("ל") && q.length >= 2) q.drop(1).trim() else null
            val withAlef = if (withoutLamed != null && !withoutLamed.startsWith("א")) "א$withoutLamed" else null
            val withAyin = if (withoutLamed != null && !withoutLamed.startsWith("ע")) "ע$withoutLamed" else null
            listOfNotNull(q, withoutLamed, withAlef, withAyin)
        }.filter { it.isNotBlank() }.distinct()
        if (queries.isEmpty()) return emptyList()

        val preparedQueries = queries.map { q ->
            val qWords = extractWords(q)
            val qSkel = phoneticSkeleton(q)
            val qWordSkels = qWords.map { phoneticSkeleton(it) }.filter { it.length >= 2 }
            Triple(q, qWords, qSkel to qWordSkels)
        }

        val scored = candidates.mapNotNull { p ->
            val names = (listOf(p.name) + p.aliases.split(',', ';')).map { normalized(it) }.filter { it.isNotBlank() }
            val fullText = names.joinToString(" ")
            val words = names.flatMap { extractWords(it) }.distinct()
            val wordSkeletons = words.map { phoneticSkeleton(it) }.filter { it.isNotBlank() }
            var bestScore = 0
            for ((q, qWords, skels) in preparedQueries) {
                val (qSkel, qWordSkels) = skels
                val score = when {
                    // 6: Exact full name/alias match
                    names.any { it == q } -> 6
                    // 5: Exact word match in name/alias
                    words.any { it == q } -> 5
                    // 4: Any word in name starts with query (e.g., "אלעד" in "Eladush אלעדוש") or direct substring in name
                    words.any { it.startsWith(q) } || fullText.contains(q) ||
                        (qWords.isNotEmpty() && qWords.all { qw -> words.any { w -> w.contains(qw) } || fullText.contains(qw) }) -> 4
                    // 3: Exact phonetic word match ("גסיקה" == "jessica", "אלעד" == "elad")
                    (qSkel.length >= 2 && wordSkeletons.any { it == qSkel }) ||
                        (qWordSkels.isNotEmpty() && qWordSkels.all { qs -> wordSkeletons.any { ws -> ws == qs } }) -> 3
                    // 2: Phonetic prefix/substring match (e.g., "אלעד" -> "ald" prefix of "eladush" -> "alds")
                    (qSkel.length >= 2 && wordSkeletons.any { it.startsWith(qSkel) || (qSkel.length >= 3 && it.contains(qSkel)) }) ||
                        (qWordSkels.isNotEmpty() && qWordSkels.all { qs -> wordSkeletons.any { ws -> ws.startsWith(qs) || (qs.length >= 3 && ws.contains(qs)) } }) -> 2
                    else -> 0
                }
                if (score > bestScore) bestScore = score
            }
            if (bestScore > 0) Triple(p, bestScore, p.favorite) else null
        }

        if (scored.isEmpty()) return emptyList()
        // Favorites ALWAYS come first among matching contacts, then sorted by match score and name!
        return scored
            .sortedWith(
                compareByDescending<Triple<Person, Int, Boolean>> { it.third }
                    .thenByDescending { it.second }
                    .thenBy { it.first.name.lowercase() }
            )
            .map { it.first }
    }

    fun commandCandidates(texts: List<String>) {
        val first = texts.firstOrNull()?.trim().orEmpty()
        if (first.isBlank()) return
        transcript = first
        status = "שמעתי: $first"
        val now = LocalDateTime.now(ZoneId.of(profile.value.zone))

        if (confirmation != null) {
            for (t in texts) {
                when (CommandParser.parse(t, now)) {
                    Command.Confirm -> { confirm(); return }
                    Command.Cancel, Command.Stop -> { cancel(); return }
                    else -> {}
                }
            }
            respond("ממתינה פעולה לאישור. אמרו כן או ביטול")
            return
        }

        // First check if any recognized candidate is a Call command
        val parsedList = texts.map { it to CommandParser.parse(it, now) }
        val callCmd = parsedList.firstNotNullOfOrNull { (_, cmd) -> (cmd as? Command.Call)?.name }
        if (callCmd != null) {
            findCallFromCandidates(texts)
            return
        }

        val nonClarify = parsedList.firstOrNull { (_, cmd) -> cmd !is Command.Clarify }
        if (nonClarify != null) {
            command(nonClarify.first)
            return
        }

        // If on CALLS screen or user just spoke a contact's name (e.g., "אלעד", "ג'סיקה")
        if (screen == Screen.CALLS) {
            findCallFromCandidates(texts)
            return
        }
        viewModelScope.launch {
            val matched = withContext(Dispatchers.Default) { matchPeople(texts) }
            if (matched.isNotEmpty()) {
                findCallFromCandidates(texts, matched)
            } else {
                command(first)
            }
        }
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
                viewModelScope.launch {
                    val matched = withContext(Dispatchers.Default) { matchPeople(listOf(text)) }
                    if (matched.isNotEmpty()) findCallFromCandidates(listOf(text), matched) else respond(command.message)
                }
            }
        }
    }

    fun findCallFromCandidates(texts: List<String>, precomputedMatches: List<Person>? = null) {
        val rawCleaned = normalized(texts.firstOrNull().orEmpty())
            .replaceFirst(callVerbRegex, "")
            .replace(Regex("^(עם |אל |ל )"), "").trim()
            .ifBlank { texts.firstOrNull().orEmpty().trim() }
        val displayQuery = if (rawCleaned.startsWith("ל") && rawCleaned.length >= 3 && texts.firstOrNull()?.contains(Regex("התקשר|תתקשר|חייג|תחייג|שיחה|לדבר|טלפן")) == true) {
            rawCleaned.drop(1).trim()
        } else {
            rawCleaned
        }
        voiceSearchQuery = displayQuery
        screen = Screen.CALLS
        viewModelScope.launch {
            val matches = precomputedMatches ?: withContext(Dispatchers.Default) { matchPeople(texts) }
            when(matches.size) {
                0 -> {
                    callChoices = emptyList()
                    respond("לא נמצא איש קשר בשם $displayQuery", false)
                }
                1 -> {
                    callChoices = matches
                    requestCall(matches.single())
                }
                else -> {
                    callChoices = matches
                    respond("נמצאו ${matches.size} אנשי קשר עבור $displayQuery. לחצו על השם הרצוי")
                }
            }
        }
    }

    fun findCall(name: String) = findCallFromCandidates(listOf(name))
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
