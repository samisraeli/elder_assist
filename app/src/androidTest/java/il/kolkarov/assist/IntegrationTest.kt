package il.kolkarov.assist

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    @Test fun medicationBackupSupportsNewAndPreviousFormats() {
        val medication=Medication(name="בדיקת רשימה",instructions="הנחיות שנקבעו",slots="MORNING=08:00;NIGHT=22:00")
        val json=Backup.encode(Profile(name="יוסף"),emptyList(),emptyList(),emptyList(),listOf(medication))
        assertEquals(listOf(medication),Backup.decode(json).medications)
        val old=org.json.JSONObject(json).put("version",1).apply {remove("medications")}
        assertTrue(Backup.decode(old.toString()).medications.isEmpty())
        assertEquals("יוסף",Backup.decode(old.toString()).profile.name)
    }
    @Test fun realXlsxWithSharedStringsDateAndPhoneFormat() {
        val files=mapOf(
            "xl/sharedStrings.xml" to """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>שם</t></si><si><t>טלפון</t></si><si><t>תאריך לידה</t></si><si><t>שלום</t></si></sst>""",
            "xl/styles.xml" to """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="0000000000"/></numFmts><cellXfs count="2"><xf numFmtId="0"/><xf numFmtId="164"/></cellXfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row><row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2" s="1"><v>501234567</v></c><c r="C2"><v>45292</v></c></row></sheetData></worksheet>"""
        )
        val bytes=ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z -> files.forEach { (name,text) -> z.putNextEntry(ZipEntry(name));z.write(text.toByteArray());z.closeEntry() } }
        val preview=PeopleImport.xlsx(bytes.toByteArray().inputStream(),emptyList())
        assertEquals(1,preview.accepted.size)
        assertEquals("0501234567",preview.accepted.single().phone)
        assertEquals(1,preview.accepted.single().birthDay)
        assertEquals(2024,preview.accepted.single().birthYear)
    }
    @Test fun rssAndAtomReturnSourceTextAndMissingDatesHonestly() {
        val parser=RssNews();val source=NewsSource(name="בדיקה",url="https://example.org/rss")
        val rss=parser.parse("""<rss version="2.0"><channel><item><title>כותרת</title><description>&lt;p&gt;תקציר&lt;/p&gt;</description><link>https://example.org/one</link></item></channel></rss>""",source)
        assertEquals("תקציר",rss.single().summary);assertEquals("",rss.single().published)
        val atom=parser.parse("""<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>אחר</title><link href="https://example.org/two"/><updated>2026-10-17T10:00:00Z</updated></entry></feed>""",source)
        assertEquals("https://example.org/two",atom.single().url)
    }
    @Test fun backupRoundTripKeepsPersonalDataButNotDeviceCalendarIds() {
        val p=Profile(name="יוסף",city="חיפה",calendarIds=setOf("123"),latitude=32.8,longitude=34.9)
        val people=listOf(Person(name="שלום",phone="0501234567",birthMonth=2,birthDay=29))
        val result=Backup.decode(Backup.encode(p,people,emptyList(),emptyList()))
        assertEquals("יוסף",result.profile.name);assertTrue(result.profile.calendarIds.isEmpty())
        assertEquals(people,result.people)
    }
    @Test fun unsafeXmlAndMalformedBackupAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RssNews().parse("<!DOCTYPE rss><rss/>",NewsSource(name="x",url="https://example.org"))
        }
        assertThrows(Exception::class.java) { Backup.decode("{\"version\":99}") }
    }
}
