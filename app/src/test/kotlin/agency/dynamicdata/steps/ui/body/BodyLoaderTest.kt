package agency.dynamicdata.steps.ui.body

import agency.dynamicdata.steps.core.Measurement
import agency.dynamicdata.steps.health.BodyMetric
import agency.dynamicdata.steps.health.BodyRepository
import agency.dynamicdata.steps.health.HealthConnectAvailability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BodyLoaderTest {

    private val today = LocalDate.of(2026, 11, 11)

    private class FakeBodyRepository(
        private val availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
        private val allowed: Set<BodyMetric> = BodyMetric.entries.toSet(),
        private val readings: Map<BodyMetric, List<Measurement>> = emptyMap(),
        private val historyBlocked: Boolean = false,
        private val failWith: Exception? = null,
    ) : BodyRepository {
        val requested = mutableListOf<Pair<LocalDate, LocalDate>>()

        override fun availability() = availability
        override suspend fun canRead(metric: BodyMetric) = metric in allowed
        override suspend fun historyBlocked() = historyBlocked
        override suspend fun measurements(metric: BodyMetric, from: LocalDate, to: LocalDate): List<Measurement> {
            requested += from to to
            failWith?.let { throw it }
            return readings[metric].orEmpty().filter { it.at.toLocalDate() in from..to }
        }
    }

    private fun on(date: LocalDate, value: Double) = Measurement(date.atTime(8, 0), value)

    private suspend fun load(repository: BodyRepository, yearsBack: Int = 0) =
        BodyLoader(repository).load(today, yearsBack) as BodyState.Loaded

    @Test
    fun `shows every reading in the year, not one per day`() = runTest {
        val day = LocalDate.of(2026, 6, 1)
        val repository = FakeBodyRepository(
            readings = mapOf(
                BodyMetric.WEIGHT to listOf(on(day, 80.0), Measurement(day.atTime(20, 0), 81.0)),
            ),
        )

        val weight = load(repository).weight as MetricState.Ready

        assertEquals(2, weight.readings.size)
        assertEquals(2, weight.trend.size)
    }

    @Test
    fun `reads past both ends of the year so the trend is whole at the edges`() = runTest {
        val repository = FakeBodyRepository()

        load(repository, yearsBack = 1)

        // 12 Nov 2024 – 11 Nov 2025, padded 15 days each side.
        assertEquals(LocalDate.of(2024, 10, 28) to LocalDate.of(2025, 11, 26), repository.requested.first())
    }

    @Test
    fun `never asks for days after today`() = runTest {
        val repository = FakeBodyRepository()

        load(repository)

        assertTrue(repository.requested.all { (_, to) -> !to.isAfter(today) })
    }

    @Test
    fun `neighbouring readings shape the trend but are not drawn`() = runTest {
        val start = LocalDate.of(2025, 11, 12)
        val repository = FakeBodyRepository(
            readings = mapOf(BodyMetric.WEIGHT to listOf(on(start.minusDays(5), 90.0), on(start, 80.0))),
        )

        val weight = load(repository).weight as MetricState.Ready

        assertEquals(listOf(80.0), weight.readings.map { it.value })
        assertEquals(85.0, weight.trend.single().value, 1e-9)
    }

    @Test
    fun `an empty year has no summary rather than zeros`() = runTest {
        val bodyFat = load(FakeBodyRepository()).bodyFat as MetricState.Ready

        assertTrue(bodyFat.readings.isEmpty())
        assertNull(bodyFat.summary)
    }

    @Test
    fun `each measurement asks for its own access`() = runTest {
        val state = load(FakeBodyRepository(allowed = setOf(BodyMetric.WEIGHT)))

        assertTrue(state.weight is MetricState.Ready)
        assertEquals(MetricState.PermissionRequired, state.bodyFat)
    }

    @Test
    fun `a refused read is an error with its reason`() = runTest {
        val state = load(FakeBodyRepository(failWith = SecurityException("not in foreground")))

        val error = state.weight as MetricState.Error
        assertTrue(error.reason.contains("not in foreground"))
    }

    @Test
    fun `passes on that older readings are being withheld`() = runTest {
        assertTrue(load(FakeBodyRepository(historyBlocked = true)).historyBlocked)
    }

    @Test
    fun `without health connect there is nothing to show`() = runTest {
        val state = BodyLoader(FakeBodyRepository(availability = HealthConnectAvailability.NOT_SUPPORTED)).load(today, 0)

        assertEquals(BodyState.Unavailable, state)
    }
}
