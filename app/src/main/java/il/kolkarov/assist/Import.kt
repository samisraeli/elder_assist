package il.kolkarov.assist

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDate
import java.time.MonthDay
import java.util.zip.ZipInputStream

data class ImportRow(val line: Int, val person: Person?, val error: String? = null, val duplicate: Boolean = false)
data class ImportPreview(val rows: List<ImportRow>) {
    val accepted get() = rows.filter { it.person != null && !it.duplicate && it.error == null }.map { it.person!! }
}

object PeopleImport {
    fun pasted(text: String, existing: List<Person>): ImportPreview {
        require(text.length <= 2_000_000) { "הרשימה גדולה מדי" }
        val delimiter = if ('\t' in text.lineSequence().firstOrNull().orEmpty()) '\t' else ','
        return preview(delimited(text, delimiter), existing)
    }

    fun xlsx(input: InputStream, existing: List<Person>): ImportPreview {
        val entries = mutableMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                require(++count < 1000) { "יותר מדי רכיבים בקובץ" }
                val retain = entry.name in listOf("xl/sharedStrings.xml", "xl/styles.xml", "xl/workbook.xml", "xl/_rels/workbook.xml.rels") || entry.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml"))
                run {
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var size = zip.read(buffer)
                    while (size != -1) {
                        total += size
                        require(total <= 12_000_000) { "קובץ Excel גדול מדי" }
                        if(retain) out.write(buffer, 0, size); size = zip.read(buffer)
                    }
                    if(retain) entries[entry.name] = out.toByteArray()
                }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
        val workbook = entries["xl/workbook.xml"]?.toString(Charsets.UTF_8).orEmpty()
        val date1904 = Regex("date1904=[\"'](?:1|true)[\"']").containsMatchIn(workbook)
        val strings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val p = parser(bytes)
            var value: StringBuilder? = null
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "si") value = StringBuilder()
                if (p.eventType == XmlPullParser.START_TAG && p.name == "t") value?.append(p.nextText())
                if (p.eventType == XmlPullParser.END_TAG && p.name == "si") { strings += value.toString(); value = null }
                p.next()
            }
        }
        val zeroFormats = mutableMapOf<Int, Int>()
        val styles = mutableMapOf<Int, Int>()
        entries["xl/styles.xml"]?.let { bytes ->
            val p = parser(bytes); var inXfs = false; var index = 0
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                    "numFmt" -> {
                        val code = p.getAttributeValue(null, "formatCode").orEmpty()
                        if (code.matches(Regex("0{2,15}"))) zeroFormats[p.getAttributeValue(null, "numFmtId").toInt()] = code.length
                    }
                    "cellXfs" -> inXfs = true
                    "xf" -> if (inXfs) { styles[index++] = zeroFormats[p.getAttributeValue(null, "numFmtId")?.toIntOrNull()] ?: 0 }
                }
                if (p.eventType == XmlPullParser.END_TAG && p.name == "cellXfs") inXfs = false
                p.next()
            }
        }
        val sheet = entries.keys.filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }.sorted().firstOrNull()
            ?: error("לא נמצא גיליון בקובץ")
        val rows = mutableListOf<List<String>>()
        val p = parser(entries.getValue(sheet)); var row = mutableListOf<String>()
        var col = 0; var type = ""; var style = 0; var value = ""; var formula = false
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "row" -> row = mutableListOf()
                "c" -> {
                    val letters = p.getAttributeValue(null, "r").orEmpty().takeWhile { it.isLetter() }
                    col = letters.fold(0) { n, c -> n * 26 + (c - 'A' + 1) } - 1
                    if (col < 0) col = row.size
                    require(col < 100) { "יותר מדי עמודות" }
                    type = p.getAttributeValue(null, "t").orEmpty(); style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                    value = ""; formula = false
                }
                "f" -> formula = true
                "v", "t" -> value += p.nextText()
            }
            if (p.eventType == XmlPullParser.END_TAG) when (p.name) {
                "c" -> {
                    while (row.size <= col) row += ""
                    row[col] = when {
                        formula -> "#FORMULA"
                        type == "s" -> strings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                        (styles[style] ?: 0) > 0 -> value.toDoubleOrNull()?.toLong()?.toString()?.padStart(styles.getValue(style), '0') ?: value
                        else -> value
                    }
                }
                "row" -> { rows += row; require(rows.size <= 5001) { "מותר לייבא עד 5000 שורות" } }
            }
            p.next()
        }
        return preview(rows, existing, date1904)
    }

    private fun parser(bytes: ByteArray): XmlPullParser {
        require(!bytes.toString(Charsets.UTF_8).contains("<!DOCTYPE",ignoreCase=true)) { "פורמט XML אינו נתמך" }
        return Xml.newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }
    }

    internal fun delimited(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { row += cell.toString(); cell.clear() }
                c == '\n' && !quoted -> { row += cell.toString().trimEnd('\r'); cell.clear(); rows += row; row = mutableListOf() }
                else -> cell.append(c)
            }; i++
        }
        require(!quoted) { "מרכאות לא סגורות בטבלה" }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    fun preview(rows: List<List<String>>, existing: List<Person>, date1904: Boolean = false): ImportPreview {
        require(rows.size <= 5001) { "מותר לייבא עד 5000 שורות" }
        val header = rows.firstOrNull()?.map { it.trim().removePrefix("\uFEFF").lowercase() } ?: return ImportPreview(emptyList())
        fun index(vararg names: String) = header.indexOfFirst { it in names }
        val name = index("שם", "name"); val alias = index("כינוי", "כינויים", "alias", "aliases")
        val phone = index("טלפון", "מספר טלפון", "phone"); val birth = index("תאריך לידה", "יום הולדת", "birthday", "birthdate")
        require(name >= 0) { "נדרשת שורת כותרת עם עמודת שם" }
        val seen = existing.map { normalized(it.name) + "|" + it.phone }.toMutableSet()
        return ImportPreview(rows.drop(1).mapIndexedNotNull { i, cells ->
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null
            fun cell(n: Int) = cells.getOrNull(n)?.trim().orEmpty()
            try {
                require(cells.none { it == "#FORMULA" }) { "יש להחליף נוסחאות בערכים" }
                val n = cell(name); require(n.isNotBlank() && n.length <= 100) { "שם חסר או ארוך מדי" }
                var ph = cell(phone).replace(Regex("[\\s()\\-]"), "")
                if (Regex("\\d{9,10}\\.0+").matches(ph)) ph = ph.substringBefore('.')
                require(ph.isEmpty() || Regex("\\+?\\d{3,15}").matches(ph)) { "מספר טלפון לא תקין; יש לשמור טלפונים כטקסט ב־Excel" }
                require(!(ph.length == 9 && ph.startsWith("5"))) { "ייתכן שחסר אפס בתחילת הטלפון; שמרו את העמודה כטקסט" }
                val b = cell(birth); var year: Int? = null; var month: Int? = null; var day: Int? = null
                if (b.isNotBlank()) {
                    val date = parseBirth(b, date1904)
                    year = date.third; month = date.second; day = date.first
                }
                val person = Person(name = n, aliases = cell(alias), phone = ph, birthMonth = month, birthDay = day, birthYear = year)
                val duplicate = !seen.add(normalized(n) + "|" + ph)
                ImportRow(i + 2, person, duplicate = duplicate)
            } catch (e: IllegalArgumentException) { ImportRow(i + 2, null, e.message ?: "שורה לא תקינה") }
              catch (_: java.time.DateTimeException) { ImportRow(i + 2, null, "תאריך לא תקין") }
        })
    }

    fun parseBirth(value: String, date1904: Boolean = false): Triple<Int, Int, Int?> {
        val v = value.trim()
        val numeric = v.toDoubleOrNull()
        if (numeric != null && numeric >= 1 && numeric <= 150000) {
            require(date1904 || numeric.toLong() != 60L) { "תאריך Excel אינו תקין" }
            val base = if (date1904) LocalDate.of(1904,1,1) else if(numeric < 60) LocalDate.of(1899,12,31) else LocalDate.of(1899,12,30)
            val date = base.plusDays(numeric.toLong())
            require(date.year in 1900..LocalDate.now().year) { "שנת לידה לא תקינה" }
            return Triple(date.dayOfMonth, date.monthValue, date.year)
        }
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(v)) {
            val d = LocalDate.parse(v); require(d.year in 1900..LocalDate.now().year) { "שנת לידה לא תקינה" }; return Triple(d.dayOfMonth,d.monthValue,d.year)
        }
        val parts = v.split('/', '.', '-').map { it.toIntOrNull() ?: throw IllegalArgumentException("תאריך לא תקין") }
        require(parts.size in 2..3) { "יש להזין תאריך בצורה יום/חודש או יום/חודש/שנה" }
        MonthDay.of(parts[1], parts[0])
        val year = parts.getOrNull(2)
        if (year != null) { require(year in 1900..LocalDate.now().year) { "שנת לידה לא תקינה" }; LocalDate.of(year,parts[1],parts[0]) }
        return Triple(parts[0],parts[1],year)
    }
}
