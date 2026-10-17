package il.kolkarov.assist

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

data class BackupData(val profile: Profile, val people: List<Person>, val reminders: List<Reminder>, val sources: List<NewsSource>, val medications: List<Medication> = emptyList())

object Backup {
    fun encode(p: Profile, people: List<Person>, reminders: List<Reminder>, sources: List<NewsSource>, medications: List<Medication> = emptyList()): String = JSONObject().apply {
        put("version",2)
        put("profile",JSONObject().apply {
            put("name",p.name); put("address",p.address); put("city",p.city); put("country",p.country)
            put("lat",p.latitude); put("lon",p.longitude); put("zone",p.zone); put("language",p.language)
            put("voice",p.voice); put("rate",p.rate); put("dark",p.dark); put("font",p.fontScale)
            put("hour",p.noticeHour); put("minute",p.noticeMinute); put("morning",p.morningNotice)
        })
        put("people",JSONArray().apply { people.forEach { p -> put(JSONObject().apply {
            put("id",p.id); put("name",p.name); put("aliases",p.aliases); put("phone",p.phone)
            put("month",p.birthMonth); put("day",p.birthDay); put("year",p.birthYear); put("favorite",p.favorite)
        }) } })
        put("reminders",JSONArray().apply { reminders.forEach { r -> put(JSONObject().apply { put("id",r.id); put("title",r.title); put("at",r.at); put("delivered",r.delivered) }) } })
        put("sources",JSONArray().apply { sources.forEach { s -> put(JSONObject().apply { put("id",s.id); put("name",s.name); put("url",s.url); put("topic",s.topic) }) } })
        put("medications",JSONArray().apply { medications.forEach { m -> put(JSONObject().apply {
            put("id",m.id); put("name",m.name); put("instructions",m.instructions); put("slots",m.slots); put("enabled",m.enabled)
        }) } })
    }.toString(2)

    fun decode(text: String): BackupData {
        require(text.length <= 5_000_000) { "קובץ הגיבוי גדול מדי" }
        val o = JSONObject(text); val version=o.getInt("version"); require(version in 1..2) { "גרסת גיבוי לא נתמכת" }
        val p = o.getJSONObject("profile")
        val lat = if (p.isNull("lat")) null else p.getDouble("lat"); val lon = if (p.isNull("lon")) null else p.getDouble("lon")
        require((lat == null && lon == null) || (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0))
        val zone = p.getString("zone"); ZoneId.of(zone)
        val profile = Profile(p.getString("name"),p.getString("address"),p.getString("city"),p.getString("country"),lat,lon,zone,
            p.optString("language","he-IL"),p.optString("voice"),p.optDouble("rate",.85).toFloat(),p.optBoolean("dark"),p.optDouble("font",1.0).toFloat(),
            p.optInt("hour",9),p.optInt("minute",0),emptySet(),p.optBoolean("morning"))
        require(profile.noticeHour in 0..23 && profile.noticeMinute in 0..59 && profile.rate in .5f..1.5f && profile.fontScale in 1f..1.5f)
        fun array(name: String): List<JSONObject> = o.getJSONArray(name).let { a -> require(a.length() <= 5000); (0 until a.length()).map { a.getJSONObject(it) } }
        val people = array("people").map { v ->
            val month = if(v.isNull("month")) null else v.getInt("month"); val day = if(v.isNull("day")) null else v.getInt("day")
            require((month == null) == (day == null)); if(month != null && day != null) java.time.MonthDay.of(month,day)
            val name = v.getString("name"); val phone = v.getString("phone")
            require(name.isNotBlank() && name.length <= 100 && (phone.isEmpty() || Regex("\\+?\\d{3,15}").matches(phone)))
            Person(v.getString("id"),name,v.optString("aliases"),phone,month,day,if(v.isNull("year")) null else v.getInt("year"),v.optBoolean("favorite",true))
        }
        val reminders = array("reminders").map { v -> Reminder(v.getString("id"),v.getString("title"),v.getLong("at"),v.optBoolean("delivered")) }
        val sources = array("sources").map { v ->
            val url = v.getString("url"); require(java.net.URI(url).scheme == "https"); Topic.valueOf(v.getString("topic"))
            NewsSource(v.getString("id"),v.getString("name"),url,v.getString("topic"))
        }
        require(people.map { it.id }.distinct().size == people.size && reminders.map { it.id }.distinct().size == reminders.size)
        val medications = if(version==1) emptyList() else array("medications").map { v ->
            val name=v.getString("name"); require(name.isNotBlank() && name.length <= 120)
            val slots=v.getString("slots"); MedicationSchedule.decode(slots)
            Medication(v.getString("id"),name,v.optString("instructions"),slots,v.optBoolean("enabled",true))
        }
        require(medications.map { it.id }.distinct().size==medications.size)
        return BackupData(profile,people,reminders,sources,medications)
    }

    suspend fun merge(app: CompanionApp, data: BackupData) {
        app.database.withTransaction {
            val dao = app.database.dao(); dao.savePeople(data.people)
            data.reminders.forEach { dao.saveReminder(it) }; data.sources.forEach { dao.saveSource(it) }
            data.medications.forEach { dao.saveMedication(it) }
        }
        // Calendar IDs are device-specific and must be selected again on the destination phone.
        app.profiles.save(data.profile)
        NoticeScheduler(app).refresh()
    }
}
