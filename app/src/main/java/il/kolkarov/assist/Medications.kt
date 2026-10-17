package il.kolkarov.assist

import java.time.*

enum class DayPart(val label: String) { MORNING("בוקר"), NOON("צהריים"), EVENING("ערב"), NIGHT("לילה") }
data class MedicationTime(val part: DayPart, val time: LocalTime)

object MedicationSchedule {
    fun encode(times: List<MedicationTime>): String {
        require(times.isNotEmpty() && times.map { it.part }.distinct().size == times.size) { "יש לבחור לפחות חלק יום אחד, פעם אחת לכל חלק" }
        return times.sortedBy { it.time }.joinToString(";") { "${it.part.name}=${it.time}" }
    }
    fun decode(slots: String): List<MedicationTime> = slots.split(';').filter { it.isNotBlank() }.map {
        val pieces = it.split('='); require(pieces.size == 2) { "שעת תרופה אינה תקינה" }
        MedicationTime(DayPart.valueOf(pieces[0]),LocalTime.parse(pieces[1]))
    }.also { require(it.isNotEmpty() && it.map { t -> t.part }.distinct().size == it.size) { "רשימת זמני התרופה אינה תקינה" } }

    fun agenda(items: List<Medication>, start: LocalDate, zone: ZoneId, days: Long = 7): List<AgendaItem> = items.filter { it.enabled }.flatMap { med ->
        decode(med.slots).flatMap { slot -> (0L until days).map { offset ->
            val date = start.plusDays(offset)
            AgendaItem("medication:${med.id}:$date:${slot.part.name}",
                "${med.name} · ${slot.part.label}${if(med.instructions.isBlank()) "" else " · ${med.instructions}"}",
                date.atTime(slot.time).atZone(zone),"תרופה")
        } }
    }.sortedBy { it.time }
}
