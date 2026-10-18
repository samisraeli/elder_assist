package il.kolkarov.assist

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DomainTest {
    private val now = LocalDateTime.of(2026,10,17,12,0)
    @Test fun leapBirthdayUsesFebruary28() {
        assertEquals(LocalDate.of(2027,2,28),nextBirthday(2,29,LocalDate.of(2027,1,1)))
        assertEquals(LocalDate.of(2028,2,29),nextBirthday(2,29,LocalDate.of(2027,3,1)))
    }
    @Test fun birthdayAcrossYearBoundary() {
        assertEquals(LocalDate.of(2027,1,1),nextBirthday(1,1,LocalDate.of(2026,12,29)))
    }
    @Test fun birthdayTodayIsIncluded() { assertEquals(LocalDate.of(2026,10,17),nextBirthday(10,17,now.toLocalDate())) }
    @Test fun callRequiresAContactAndConfirmationIsSeparate() {
        assertEquals(Command.Call("שלום"),CommandParser.parse("התקשר לשלום",now))
        assertEquals(Command.Call("ליאור"),CommandParser.parse("התקשר לליאור",now))
        assertTrue(CommandParser.parse("התקשר",now) is Command.Clarify)
        assertEquals(Command.Confirm,CommandParser.parse("כן",now))
    }
    @Test fun validReminderGetsFutureTime() {
        assertEquals(Command.Remind("להתקשר לדני",LocalDateTime.of(2026,10,18,8,0)),CommandParser.parse("תזכיר לי מחר בשעה 8 בבוקר להתקשר לדני",now))
    }
    @Test fun ambiguousAndPastTimesAreRejected() {
        assertTrue(CommandParser.parse("תזכיר לי מחר בשעה 8 להתקשר לדני",now) is Command.Clarify)
        assertTrue(CommandParser.parse("תזכיר לי היום בשעה 8 בבוקר להתקשר לדני",now) is Command.Clarify)
        assertTrue(CommandParser.parse("תזכיר לי מחר בשעה 25 בבוקר להתקשר לדני",now) is Command.Clarify)
    }
    @Test fun eveningIsConverted() {
        val command=CommandParser.parse("תזכיר לי מחר בשעה 8 בערב לשתות מים",now) as Command.Remind
        assertEquals(20,command.at.hour)
    }
    @Test fun hebrewSpokenHoursAreAccepted() {
        val command=CommandParser.parse("תזכיר לי מחר בשמונה בבוקר להתקשר לדני",now) as Command.Remind
        assertEquals(8,command.at.hour)
        assertEquals("להתקשר לדני",command.title)
    }
    @Test fun halfPastHourIsNotSilentlyRoundedDown() {
        val command=CommandParser.parse("תזכיר לי מחר בשמונה וחצי בבוקר להתקשר לדני",now) as Command.Remind
        assertEquals(LocalTime.of(8,30),command.at.toLocalTime())
        assertEquals("להתקשר לדני",command.title)
    }
    @Test fun newsAndAgendaAreRouted() {
        assertEquals(Command.News(Topic.SPORT),CommandParser.parse("חדשות ספורט",now))
        assertEquals(Command.Agenda(true),CommandParser.parse("ימי הולדת השבוע",now))
    }
    @Test fun importPreservesLeadingZeroAndDetectsDuplicate() {
        val text="שם\tכינוי\tטלפון\tתאריך לידה\nשלום\tאבא\t0501234567\t29/2\nשלום\tאבא\t0501234567\t29/2"
        val preview=PeopleImport.pasted(text,emptyList())
        assertEquals(1,preview.accepted.size);assertEquals("0501234567",preview.accepted.single().phone)
        assertTrue(preview.rows[1].duplicate)
    }
    @Test fun invalidDatesAndLostLeadingZeroNeedCorrection() {
        val preview=PeopleImport.pasted("שם,טלפון,תאריך לידה\nשלום,501234567,31/2",emptyList())
        assertEquals(0,preview.accepted.size);assertNotNull(preview.rows.single().error)
    }
    @Test fun csvSupportsQuotedCommas() {
        val preview=PeopleImport.pasted("שם,כינוי,טלפון\n\"שלום כהן\",\"אבא,סבא\",0501234567",emptyList())
        assertEquals("אבא,סבא",preview.accepted.single().aliases)
    }
    @Test fun excelSerialAnd1904Dates() {
        assertEquals(Triple(1,1,2024),PeopleImport.parseBirth("45292"))
        assertEquals(Triple(2,1,1904),PeopleImport.parseBirth("1",true))
    }
    @Test fun crossLanguagePhoneticSkeletonMatchesHebrewAndEnglish() {
        assertEquals(phoneticSkeleton("ג'סיקה"), phoneticSkeleton("jessica"))
        assertEquals(phoneticSkeleton("גסיקה"), phoneticSkeleton("jessica"))
        assertTrue(phoneticSkeleton("eladush").startsWith(phoneticSkeleton("אלעד")))
        assertTrue(phoneticSkeleton("אלעדוש").startsWith(phoneticSkeleton("אלעד")))
    }
}
