package agency.dynamicdata.steps.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyScheduleTest {

    private val nineAm = LocalTime.of(9, 0)
    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")

    private fun at(hour: Int, minute: Int = 0, zone: ZoneId = buenosAires) =
        ZonedDateTime.of(2026, 9, 22, hour, minute, 0, 0, zone)

    @Test
    fun `before the hour, fires the same day`() {
        val next = DailySchedule.next(at(7, 30), nineAm)

        assertEquals(22, next.dayOfMonth)
        assertEquals(9, next.hour)
    }

    @Test
    fun `after the hour, waits for tomorrow`() {
        val next = DailySchedule.next(at(14, 0), nineAm)

        assertEquals(23, next.dayOfMonth)
        assertEquals(9, next.hour)
    }

    @Test
    fun `exactly at the hour, moves to tomorrow rather than repeating`() {
        // Asked right after firing. Returning the same instant would reschedule the
        // reminder onto itself and fire it repeatedly.
        val next = DailySchedule.next(at(9, 0), nineAm)

        assertEquals(23, next.dayOfMonth)
        assertTrue(next.isAfter(at(9, 0)))
    }

    @Test
    fun `a second before the hour still catches today`() {
        val next = DailySchedule.next(at(8, 59).plusSeconds(59), nineAm)

        assertEquals(22, next.dayOfMonth)
        assertEquals(9, next.hour)
    }

    @Test
    fun `rolls over the end of a month`() {
        val lastDay = ZonedDateTime.of(2026, 9, 30, 21, 0, 0, 0, buenosAires)

        val next = DailySchedule.next(lastDay, nineAm)

        assertEquals(10, next.monthValue)
        assertEquals(1, next.dayOfMonth)
    }

    @Test
    fun `survives the clocks going forward`() {
        // Chile jumps 2026-09-06 00:00 -> 01:00. An hour that does not exist must
        // still resolve to a real instant rather than throwing.
        val santiago = ZoneId.of("America/Santiago")
        val midnightSkipped = LocalTime.of(0, 30)
        val evening = ZonedDateTime.of(2026, 9, 5, 22, 0, 0, 0, santiago)

        val next = DailySchedule.next(evening, midnightSkipped)

        assertTrue(next.isAfter(evening))
        assertEquals(6, next.dayOfMonth)
    }

    @Test
    fun `takes the first of a repeated hour when the clocks go back`() {
        // Chile falls back 2026-04-04 24:00 -> 23:00, so that evening hour happens
        // twice. The earlier one keeps the reminder from arriving an hour late.
        val santiago = ZoneId.of("America/Santiago")
        val repeated = LocalTime.of(23, 30)
        val afternoon = ZonedDateTime.of(2026, 4, 4, 15, 0, 0, 0, santiago)

        val next = DailySchedule.next(afternoon, repeated)

        assertTrue(next.isAfter(afternoon))
        assertEquals(23, next.hour)
    }

    @Test
    fun `stays in the zone it was given`() {
        val next = DailySchedule.next(at(7, 0, ZoneId.of("Europe/Madrid")), nineAm)

        assertEquals(ZoneId.of("Europe/Madrid"), next.zone)
    }
}
