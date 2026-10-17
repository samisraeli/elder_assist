package il.kolkarov.assist

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MedicationTest {
    @Test fun separateDayPartsKeepTheirOwnTimes() {
        val times=listOf(MedicationTime(DayPart.MORNING,LocalTime.of(8,30)),MedicationTime(DayPart.NIGHT,LocalTime.of(22,0)))
        assertEquals(times,MedicationSchedule.decode(MedicationSchedule.encode(times)))
    }
    @Test fun repeatsAcrossYearBoundaryAndDisabledMedicinesAreExcluded() {
        val slots=MedicationSchedule.encode(listOf(MedicationTime(DayPart.MORNING,LocalTime.of(8,0))))
        val items=MedicationSchedule.agenda(listOf(Medication(name="לפי מרשם",slots=slots),Medication(name="מושהה",slots=slots,enabled=false)),LocalDate.of(2026,12,31),ZoneId.of("Asia/Jerusalem"))
        assertEquals(7,items.size)
        assertEquals(LocalDate.of(2027,1,6),items.last().time.toLocalDate())
        assertEquals(7,items.map { it.key }.distinct().size)
        assertTrue(items.none { it.title.contains("מושהה") })
    }
    @Test fun duplicateDayPartAndEmptyOrInvalidSchedulesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {MedicationSchedule.encode(emptyList())}
        val time=MedicationTime(DayPart.NOON,LocalTime.NOON)
        assertThrows(IllegalArgumentException::class.java) {MedicationSchedule.encode(listOf(time,time))}
        assertThrows(java.time.DateTimeException::class.java) {MedicationSchedule.decode("MORNING=29:00")}
    }
    @Test fun medicationCommandsTakePrecedenceOverMorningBriefing() {
        assertEquals(Command.Medications,CommandParser.parse("מה התרופות שלי בבוקר"))
    }
}
