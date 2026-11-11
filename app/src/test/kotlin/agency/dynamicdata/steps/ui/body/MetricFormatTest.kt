package agency.dynamicdata.steps.ui.body

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MetricFormatTest {

    @Test
    fun `weight follows the locale's unit`() {
        assertEquals("82.4 kg", MetricFormat.weight(Locale.UK).value(82.4, Locale.UK))
        assertEquals("181.7 lb", MetricFormat.weight(Locale.US).value(82.4, Locale.US))
    }

    @Test
    fun `body fat changes are in points, not percent`() {
        assertEquals("18.2%", MetricFormat.BODY_FAT.value(18.2, Locale.US))
        assertEquals("−1.5 pts", MetricFormat.BODY_FAT.change(-1.5, Locale.US))
    }

    @Test
    fun `axis labels drop the decimal when the step is whole`() {
        assertEquals("80", MetricFormat.BODY_FAT.tick(80.0, 2.0, Locale.US))
        assertEquals("80.5", MetricFormat.BODY_FAT.tick(80.5, 0.5, Locale.US))
    }
}
