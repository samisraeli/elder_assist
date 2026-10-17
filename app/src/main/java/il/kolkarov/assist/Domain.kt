package il.kolkarov.assist

import java.time.*
import java.time.format.DateTimeFormatter
import java.text.Normalizer

enum class Topic(val label: String) { GENERAL("כללי"), SPORT("ספורט"), CULTURE("בידור ותרבות") }

data class Profile(
    val name: String = "", val address: String = "", val city: String = "", val country: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val zone: String = "Asia/Jerusalem",
    val language: String = "he-IL", val voice: String = "", val rate: Float = .85f,
    val dark: Boolean = false, val fontScale: Float = 1f, val noticeHour: Int = 9,
    val noticeMinute: Int = 0, val calendarIds: Set<String> = emptySet(), val morningNotice: Boolean = false,
    val enableCalls: Boolean = true, val enablePhoneContacts: Boolean = true,
    val enableBirthdays: Boolean = false, val enableMedications: Boolean = false,
    val enableNews: Boolean = false, val enableWeather: Boolean = false,
    val enableAgenda: Boolean = false, val enableMorning: Boolean = false
)

data class CalendarChoice(val id: String, val name: String)
data class AgendaItem(val key: String, val title: String, val time: ZonedDateTime, val kind: String, val allDay: Boolean = false)
data class NewsItem(val title: String, val summary: String, val url: String, val source: String, val published: String)
data class Place(val name: String, val country: String, val latitude: Double, val longitude: Double, val zone: String)
data class Weather(val text: String, val fetched: Instant)
data class NewsResult(val items: List<NewsItem>, val warnings: List<String>, val fetched: Instant)

fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("[\\u0591-\\u05C7]"), "").replace(Regex("[\"׳״?!.,]"), "")
    .trim().lowercase().replace(Regex("\\s+"), " ")

fun nextBirthday(month: Int, day: Int, today: LocalDate): LocalDate {
    fun date(year: Int): LocalDate = if (month == 2 && day == 29 && !Year.isLeap(year.toLong()))
        LocalDate.of(year, 2, 28) else LocalDate.of(year, month, day)
    return date(today.year).let { if (it < today) date(today.year + 1) else it }
}

sealed interface Command {
    data object Morning : Command
    data object WeatherNow : Command
    data object Medications : Command
    data class News(val topic: Topic) : Command
    data class Agenda(val birthdaysOnly: Boolean = false, val todayOnly: Boolean = false) : Command
    data class Call(val name: String) : Command
    data class Remind(val title: String, val at: LocalDateTime) : Command
    data class CancelReminder(val title: String) : Command
    data object Confirm : Command
    data object Cancel : Command
    data object Stop : Command
    data object Repeat : Command
    data class Clarify(val message: String) : Command
}

object CommandParser {
    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now()): Command {
        val s = normalized(raw)
        return when {
            s in listOf("כן", "אישור", "מאשר", "מאשרת", "כן בבקשה", "תתקשר") -> Command.Confirm
            s in listOf("לא", "בטל", "ביטול", "עזוב") -> Command.Cancel
            s.contains("עצור") || s.contains("תפסיק") -> Command.Stop
            s.contains("חזור") || s.contains("תחזור") -> Command.Repeat
            s.contains("תרופ") -> Command.Medications
            s.contains("בוקר") && !s.contains("תזכיר") -> Command.Morning
            s.contains("מזג") -> Command.WeatherNow
            s.startsWith("בטל תזכורת") || s.startsWith("מחק תזכורת") ->
                Command.CancelReminder(s.substringAfter("תזכורת").trim())
            s.startsWith("תזכיר") -> reminder(s, now)
            Regex("^(התקשר|תתקשר|התקשרי|תתקשרי|להתקשר|חייג|תחייג|חייגי|תחייגי|לחייג|שיחה)( עם| אל| ל)? ?").containsMatchIn(s) -> {
                val name = s.replaceFirst(Regex("^(התקשר|תתקשר|התקשרי|תתקשרי|להתקשר|חייג|תחייג|חייגי|תחייגי|לחייג|שיחה)( עם| אל)?( ל)? ?"), "").trim()
                if (name.isEmpty()) Command.Clarify("למי להתקשר? אמרו למשל: התקשר לשלום") else Command.Call(name)
            }
            s.contains("חדשות") || s.contains("ספורט") || s.contains("בידור") || s.contains("תרבות") ->
                Command.News(when { s.contains("ספורט") -> Topic.SPORT; s.contains("בידור") || s.contains("תרבות") -> Topic.CULTURE; else -> Topic.GENERAL })
            s.contains("יום הולדת") || s.contains("ימי הולדת") -> Command.Agenda(true)
            s.contains("אירוע") || s.contains("יומן") || s.contains("השבוע") || s.contains("היום") || s.contains("תזכורות") || s.contains("מה קרוב") -> Command.Agenda(todayOnly = s.contains("היום") && !s.contains("השבוע"))
            else -> Command.Clarify("אפשר לומר: חדשות ספורט, מה מזג האוויר, התקשר לשלום, או תזכיר לי מחר בשעה 8 בבוקר להתקשר לדני")
        }
    }

    private fun reminder(s: String, now: LocalDateTime): Command {
        val date = when {
            s.contains("מחרתיים") -> now.toLocalDate().plusDays(2)
            s.contains("מחר") -> now.toLocalDate().plusDays(1)
            s.contains("היום") -> now.toLocalDate()
            else -> return Command.Clarify("באיזה יום? אמרו שוב עם היום, מחר או מחרתיים, ושעה")
        }
        val spokenHours = mapOf("אחת" to 1, "שתיים" to 2, "שתים" to 2, "שלוש" to 3, "ארבע" to 4, "חמש" to 5, "שש" to 6, "שבע" to 7, "שמונה" to 8, "תשע" to 9, "עשר" to 10, "אחת עשרה" to 11, "שתים עשרה" to 12, "שתיים עשרה" to 12)
        var parseable = s
        spokenHours.entries.sortedByDescending { it.key.length }.forEach { (word, number) ->
            parseable = parseable.replace(Regex("(?:בשעה |בשעה|ב)${Regex.escape(word)}(?= |$)"), "בשעה $number")
        }
        val m = Regex("(?:בשעה|בשעה ה|ב־|ב-?|שעה)\\s*(\\d{1,2})(?::(\\d{2}))?").find(parseable)
            ?: return Command.Clarify("באיזו שעה? אמרו שוב למשל: תזכיר לי מחר בשעה 8 בבוקר להתקשר לדני")
        var hour = m.groupValues[1].toInt()
        val fraction = if(m.groupValues[2].isEmpty()) Regex("^\\s*(וחצי|ורבע)(?= |$)").find(parseable.substring(m.range.last+1)) else null
        val minute = fraction?.let { if(it.groupValues[1]=="וחצי") 30 else 15 } ?: m.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour !in 0..23 || minute !in 0..59) return Command.Clarify("השעה אינה תקינה")
        if (hour in 1..11 && listOf("בערב", "בלילה", "אחר הצהריים").any { s.contains(it) }) hour += 12
        if (hour == 12 && s.contains("בלילה")) hour = 0
        if (hour in 1..12 && !listOf("בבוקר", "בערב", "בלילה", "בצהריים", "אחר הצהריים").any { s.contains(it) })
            return Command.Clarify("בבוקר או בערב? אמרו שוב את התזכורת עם שעה וחלק היום")
        val title = parseable.replace(Regex("^תזכיר(?:י)?(?: לי)?\\s*"), "")
            .replace("מחרתיים", "").replace("מחר", "").replace("היום", "")
            .replace(m.value + (fraction?.value ?: ""), "").replace(Regex("בבוקר|בערב|בלילה|בצהריים|אחר הצהריים"), "").trim()
        val at = date.atTime(hour, minute)
        if (title.isBlank()) return Command.Clarify("מה להזכיר? אמרו שוב את התזכורת המלאה")
        if (!at.isAfter(now)) return Command.Clarify("המועד כבר עבר. אמרו מועד עתידי")
        return Command.Remind(title, at)
    }
}

val displayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d בMMMM", java.util.Locale.forLanguageTag("he"))
val displayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
