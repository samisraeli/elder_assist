package il.kolkarov.assist

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpgradeTest {
    @Test fun upgradingDatabasePreservesExistingContactAndAcceptsMedication() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val filename="upgrade-test-${java.util.UUID.randomUUID()}.db"
        val path=context.getDatabasePath(filename)
        path.parentFile!!.mkdirs()
        val schema=instrumentation.context.assets.open("il.kolkarov.assist.AppDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity=entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO Person (id,name,aliases,phone,birthMonth,birthDay,birthYear,favorite) VALUES (?,?,?,?,?,?,?,?)",arrayOf("preserved-contact","שם לבדיקה","","0501234567",null,null,null,1))
            old.version=1
        }
        val upgraded=Room.databaseBuilder(context,AppDatabase::class.java,filename).addMigrations(MIGRATION_1_2).build()
        try {
            assertEquals("0501234567",upgraded.dao().peopleNow().single().phone)
            val med=Medication(name="בדיקת שדרוג",slots="MORNING=08:00")
            upgraded.dao().saveMedication(med)
            assertEquals(med,upgraded.dao().medicationsNow().single())
        } finally {upgraded.close();context.deleteDatabase(filename)}
    }
}
