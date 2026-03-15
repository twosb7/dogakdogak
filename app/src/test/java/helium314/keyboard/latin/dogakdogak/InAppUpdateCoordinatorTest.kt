package helium314.keyboard.latin.dogakdogak

import com.google.android.play.core.install.model.UpdatePrecondition
import org.junit.Test
import kotlin.test.assertEquals

class InAppUpdateCoordinatorTest {

    @Test
    fun formatPreconditions_mapsKnownCodesToReadableLabels() {
        val formatted = formatUpdatePreconditions(
            setOf(
                UpdatePrecondition.APP_VERSION_FRESH,
                UpdatePrecondition.NEED_STORE_TO_PROCEED,
                9999
            )
        )

        assertEquals(
            listOf("NEED_STORE_TO_PROCEED", "APP_VERSION_FRESH", "UNKNOWN_9999"),
            formatted
        )
    }
}
