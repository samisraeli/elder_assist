package il.kolkarov.assist

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.util.concurrent.TimeUnit

@Entity data class Person(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String, val aliases: String = "", val phone: String = "",
    val birthMonth: Int? = null, val birthDay: Int? = null, val birthYear: Int? = null,
    val favorite: Boolean = true
)
@Entity data class Reminder(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val title: String, val at: Long, val delivered: Boolean = false
)
@Entity data class NewsSource(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String, val url: String, val topic: String = Topic.GENERAL.name
)
@Entity data class Medication(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String, val instructions: String = "", val slots: String, val enabled: Boolean = true
)
@Entity data class ScheduledNotice(@PrimaryKey val key: String, val title: String, val at: Long, val requestCode: Int = 0)

@Dao interface AppDao {
    @Query("SELECT * FROM Person ORDER BY favorite DESC, name") fun people(): Flow<List<Person>>
    @Query("SELECT * FROM Person ORDER BY name") suspend fun peopleNow(): List<Person>
    @Upsert suspend fun savePeople(items: List<Person>)
    @Delete suspend fun deletePerson(person: Person)
    @Query("SELECT * FROM Reminder ORDER BY at") fun reminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM Reminder ORDER BY at") suspend fun remindersNow(): List<Reminder>
    @Upsert suspend fun saveReminder(reminder: Reminder)
    @Delete suspend fun deleteReminder(reminder: Reminder)
    @Query("SELECT * FROM NewsSource ORDER BY name") fun sources(): Flow<List<NewsSource>>
    @Query("SELECT * FROM NewsSource ORDER BY name") suspend fun sourcesNow(): List<NewsSource>
    @Upsert suspend fun saveSource(source: NewsSource)
    @Delete suspend fun deleteSource(source: NewsSource)
    @Query("SELECT * FROM Medication ORDER BY name") fun medications(): Flow<List<Medication>>
    @Query("SELECT * FROM Medication ORDER BY name") suspend fun medicationsNow(): List<Medication>
    @Upsert suspend fun saveMedication(medication: Medication)
    @Delete suspend fun deleteMedication(medication: Medication)
    @Query("SELECT * FROM ScheduledNotice") suspend fun notices(): List<ScheduledNotice>
    @Query("SELECT * FROM ScheduledNotice WHERE `key` = :key") suspend fun notice(key: String): ScheduledNotice?
    @Upsert suspend fun saveNotice(notice: ScheduledNotice)
    @Query("DELETE FROM ScheduledNotice") suspend fun clearNotices()
    @Query("DELETE FROM ScheduledNotice WHERE `key` = :key") suspend fun deleteNotice(key: String)
    @Query("UPDATE Reminder SET delivered = 1 WHERE id = :id") suspend fun delivered(id: String)
}
val MIGRATION_1_2 = object : Migration(1,2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `Medication` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `instructions` TEXT NOT NULL, `slots` TEXT NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}
@Database(entities = [Person::class, Reminder::class, NewsSource::class, ScheduledNotice::class, Medication::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }

private val Context.settings by preferencesDataStore("profile")
class ProfileStore(private val context: Context) {
    val flow: Flow<Profile> = context.settings.data.map { p ->
        Profile(
            name = p[stringPreferencesKey("name")] ?: "", address = p[stringPreferencesKey("address")] ?: "",
            city = p[stringPreferencesKey("city")] ?: "", country = p[stringPreferencesKey("country")] ?: "",
            latitude = p[doublePreferencesKey("lat")], longitude = p[doublePreferencesKey("lon")],
            zone = p[stringPreferencesKey("zone")] ?: "Asia/Jerusalem", language = p[stringPreferencesKey("language")] ?: "he-IL",
            voice = p[stringPreferencesKey("voice")] ?: "", rate = p[floatPreferencesKey("rate")] ?: .85f,
            dark = p[booleanPreferencesKey("dark")] ?: false, fontScale = p[floatPreferencesKey("font")] ?: 1f,
            noticeHour = p[intPreferencesKey("hour")] ?: 9, noticeMinute = p[intPreferencesKey("minute")] ?: 0,
            calendarIds = p[stringSetPreferencesKey("calendars")] ?: emptySet(),
            morningNotice = p[booleanPreferencesKey("morning")] ?: false
        )
    }
    suspend fun save(p: Profile) { context.settings.edit { v ->
        v[stringPreferencesKey("name")] = p.name; v[stringPreferencesKey("address")] = p.address
        v[stringPreferencesKey("city")] = p.city; v[stringPreferencesKey("country")] = p.country
        if (p.latitude != null) v[doublePreferencesKey("lat")] = p.latitude else v.remove(doublePreferencesKey("lat"))
        if (p.longitude != null) v[doublePreferencesKey("lon")] = p.longitude else v.remove(doublePreferencesKey("lon"))
        v[stringPreferencesKey("zone")] = p.zone; v[stringPreferencesKey("language")] = p.language
        v[stringPreferencesKey("voice")] = p.voice; v[floatPreferencesKey("rate")] = p.rate.coerceIn(.5f, 1.5f)
        v[booleanPreferencesKey("dark")] = p.dark; v[floatPreferencesKey("font")] = p.fontScale.coerceIn(1f, 1.5f)
        v[intPreferencesKey("hour")] = p.noticeHour.coerceIn(0,23); v[intPreferencesKey("minute")] = p.noticeMinute.coerceIn(0,59)
        v[stringSetPreferencesKey("calendars")] = p.calendarIds; v[booleanPreferencesKey("morning")] = p.morningNotice
    } }
}

class CompanionApp : Application() {
    val database by lazy { Room.databaseBuilder(this, AppDatabase::class.java, "companion.db").addMigrations(MIGRATION_1_2).build() }
    val profiles by lazy { ProfileStore(this) }
    override fun onCreate() {
        super.onCreate()
        // Foreground startup already schedules notices. Keep initial maintenance away
        // from the first render, especially on slower devices after installation.
        val task = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("refresh-notices", ExistingPeriodicWorkPolicy.KEEP, task)
    }
}

fun birthdayAgenda(people: List<Person>, start: LocalDate, zone: ZoneId): List<AgendaItem> = people.mapNotNull { p ->
    if (p.birthMonth == null || p.birthDay == null) null else {
        val date = nextBirthday(p.birthMonth, p.birthDay, start)
        if (date < start.plusDays(7)) AgendaItem("birthday:${p.id}:$date", "יום הולדת ל${p.name}", date.atStartOfDay(zone), "יום הולדת", true) else null
    }
}
