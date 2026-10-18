package il.kolkarov.assist

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.*

interface WeatherProvider { suspend fun places(query: String): List<Place>; suspend fun current(profile: Profile): Weather }
interface NewsProvider { suspend fun fetch(sources: List<NewsSource>, topic: Topic): NewsResult }
interface CalendarProvider { fun choices(): List<CalendarChoice>; fun events(profile: Profile, from: LocalDate, days: Long = 7): List<AgendaItem> }

object Web {
    fun get(url: String, limit: Int = 2_000_000): String {
        var address = url
        repeat(5) {
            val uri = URI(address)
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "נדרש קישור HTTPS תקין" }
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 10000; connection.readTimeout = 12000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "KolKarov/0.1 (personal RSS reader)")
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    address = uri.resolve(connection.getHeaderField("Location") ?: error("הפניה לא תקינה")).toString()
                } else {
                    require(status in 200..299) { "השירות לא זמין כרגע ($status)" }
                    val out = java.io.ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192); var count = input.read(buffer)
                        while (count != -1) { require(out.size() + count <= limit) { "התגובה גדולה מדי" }; out.write(buffer,0,count); count = input.read(buffer) }
                    }
                    return out.toString(Charsets.UTF_8.name())
                }
            } finally { connection.disconnect() }
        }
        error("יותר מדי הפניות")
    }
}

class OpenMeteo : WeatherProvider {
    override suspend fun places(query: String): List<Place> = withContext(Dispatchers.IO) {
        require(query.trim().length >= 2) { "הקלידו לפחות שתי אותיות בשם העיר" }
        val obj = JSONObject(Web.get("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query,"UTF-8")}&count=8&language=he&format=json"))
        val list = obj.optJSONArray("results") ?: return@withContext emptyList()
        (0 until list.length()).map { index -> list.getJSONObject(index).let {
            Place(it.getString("name"), it.optString("country"), it.getDouble("latitude"), it.getDouble("longitude"), it.optString("timezone","Asia/Jerusalem"))
        } }
    }
    override suspend fun current(profile: Profile): Weather = withContext(Dispatchers.IO) {
        require(profile.latitude != null && profile.longitude != null) { "יש לבחור עיר בהגדרות המשפחה" }
        val o = JSONObject(Web.get("https://api.open-meteo.com/v1/forecast?latitude=${profile.latitude}&longitude=${profile.longitude}&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"))
        val c = o.getJSONObject("current"); val d = o.getJSONObject("daily")
        val code = c.getInt("weather_code")
        val description = when(code) { 0 -> "בהיר"; 1,2 -> "מעונן חלקית"; 3 -> "מעונן"; 45,48 -> "ערפל"; in 51..67 -> "גשום"; in 71..77 -> "שלג"; in 80..82 -> "ממטרים"; in 85..86 -> "ממטרי שלג"; in 95..99 -> "סופות רעמים"; else -> "" }
        Weather("ב${profile.city} כעת ${c.getDouble("temperature_2m").toInt()} מעלות. $description. היום בין ${d.getJSONArray("temperature_2m_min").getDouble(0).toInt()} ל־${d.getJSONArray("temperature_2m_max").getDouble(0).toInt()} מעלות. הסיכוי למשקעים ${d.getJSONArray("precipitation_probability_max").optInt(0)} אחוזים.", Instant.now())
    }
}

class RssNews : NewsProvider {
    override suspend fun fetch(sources: List<NewsSource>, topic: Topic): NewsResult = withContext(Dispatchers.IO) {
        val selected = sources.filter { it.topic == topic.name }
        require(selected.isNotEmpty()) { "טרם הוגדרו מקורות ל${topic.label}. אפשר להוסיף בהגדרות המשפחה" }
        val warnings = mutableListOf<String>(); val items = mutableListOf<NewsItem>()
        selected.take(12).forEach { source ->
            runCatching { parse(Web.get(source.url), source) }.fold(
                onSuccess = { items += it }, onFailure = { warnings += "${source.name}: ${it.message ?: "לא ניתן לקרוא את המקור"}" }
            )
        }
        if (items.isEmpty()) error(warnings.joinToString("\n").ifBlank { "לא נמצאו ידיעות במקורות" })
        NewsResult(items.distinctBy { it.url.ifBlank { normalized(it.title) } }.take(30), warnings, Instant.now())
    }
    private fun plain(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+")," ").trim()
    fun parse(xml: String, source: NewsSource): List<NewsItem> {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "פורמט XML אינו נתמך" }
        val p = Xml.newPullParser(); p.setInput(xml.reader())
        val result = mutableListOf<NewsItem>(); var active = false
        var title = ""; var summary = ""; var url = ""; var date = ""; var field: String? = null; var depth = 0; var fieldDepth = 0
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (p.name == "item" || p.name == "entry") { active = true; title=""; summary=""; url=""; date="" }
                    else if (active) {
                        if (p.name == "link" && p.getAttributeValue(null,"href") != null && p.getAttributeValue(null,"rel") in listOf(null,"alternate")) url = p.getAttributeValue(null,"href")
                        if (p.name in listOf("title","description","summary","link","pubDate","published","updated")) { field = p.name; fieldDepth = depth }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (active) when(field) {
                    "title" -> title += p.text
                    "description", "summary" -> summary += p.text
                    "link" -> url += p.text.trim()
                    "pubDate", "published", "updated" -> date += p.text
                }
                XmlPullParser.END_TAG -> {
                    if (active && (p.name == "item" || p.name == "entry")) {
                        val safeUrl = if(url.isBlank()) "" else runCatching { URI(source.url).resolve(url.trim()).toString() }.getOrDefault("")
                        if (title.isNotBlank()) result += NewsItem(plain(title).take(500),plain(summary).take(4000),safeUrl,source.name,date.trim())
                        active = false
                    }
                    if (depth == fieldDepth) field = null
                    depth--
                }
            }
            p.next()
        }
        require(result.isNotEmpty()) { "הקישור אינו מכיל RSS או Atom נתמך" }
        return result
    }
}

class DeviceCalendar(private val context: Context) : CalendarProvider {
    private fun allowed() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    override fun choices(): List<CalendarChoice> {
        if (!allowed()) return emptyList()
        return context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),null,null,null)?.use { c ->
            buildList { while(c.moveToNext()) add(CalendarChoice(c.getString(0),c.getString(1).orEmpty())) }
        } ?: emptyList()
    }
    override fun events(profile: Profile, from: LocalDate, days: Long): List<AgendaItem> {
        if (!allowed() || profile.calendarIds.isEmpty()) return emptyList()
        val zone = ZoneId.of(profile.zone)
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli(); val end = from.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply { ContentUris.appendId(this,start); ContentUris.appendId(this,end) }.build()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_ID)
        return context.contentResolver.query(uri,projection,null,null,CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
            buildList {
                while(c.moveToNext()) {
                    if (c.getString(4) !in profile.calendarIds) continue
                    val allDay = c.getInt(3) != 0; val instant = Instant.ofEpochMilli(c.getLong(2))
                    val at = if (allDay) instant.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone) else instant.atZone(zone)
                    if (at.toLocalDate() >= from && at.toLocalDate() < from.plusDays(days))
                        add(AgendaItem("calendar:${c.getLong(0)}:${c.getLong(2)}",c.getString(1) ?: "אירוע",at,"יומן",allDay))
                }
            }
        } ?: emptyList()
    }
}

class DeviceContacts(private val context: Context) {
    fun allowed() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    fun load(): List<Person> {
        if (!allowed()) return emptyList()
        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
            android.provider.ContactsContract.CommonDataKinds.Phone.STARRED
        )
        val byKey = linkedMapOf<String, Person>()
        context.contentResolver.query(uri, projection, null, null, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1)?.trim().orEmpty()
                val rawPhone = c.getString(2)?.replace(Regex("[\\s()\\-]"), "").orEmpty()
                val starred = c.getInt(3) != 0
                if (name.isBlank() || rawPhone.isBlank()) continue
                if (name.startsWith("*") || name.startsWith("#")) continue
                val key = "${normalized(name)}|$rawPhone"
                val existing = byKey[key]
                if (existing == null || (starred && !existing.favorite)) {
                    byKey[key] = Person(id = "device:$id:$rawPhone", name = name, phone = rawPhone, favorite = starred)
                }
            }
        }
        return byKey.values.sortedWith(compareByDescending<Person> { it.favorite }.thenBy { it.name.lowercase() })
    }
}

