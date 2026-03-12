// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PurchaseRepositoryTest {

    @Test
    fun ownedPremiumSwitches_bundleUnlocksAllPremiumSwitches() {
        val owned = PurchaseRepository.ownedPremiumSwitches(
            setOf(SwitchType.BUNDLE_PRODUCT_ID)
        )

        assertEquals(
            SwitchType.getPremiumSwitches().map { it.name }.toSet(),
            owned
        )
    }

    @Test
    fun ownedPremiumSwitches_exactProductsOnlyKeepStillOwnedSwitches() {
        val owned = PurchaseRepository.ownedPremiumSwitches(
            setOf(SwitchType.PEBBLE_2.productId!!, SwitchType.PEBBLE_4.productId!!)
        )

        assertEquals(setOf(SwitchType.PEBBLE_2.name, SwitchType.PEBBLE_4.name), owned)
        assertTrue(SwitchType.PEBBLE_3.name !in owned)
    }

    @Test
    fun restorableProductIds_ignoresServerRowsMissingFromOwnedPlayPurchases() {
        val restorable = PurchaseRepository.restorableProductIds(
            serverProductIds = setOf(
                SwitchType.PEBBLE_2.productId!!,
                SwitchType.PEBBLE_4.productId!!,
            ),
            ownedPlayProductIds = setOf(SwitchType.PEBBLE_2.productId!!)
        )

        assertEquals(setOf(SwitchType.PEBBLE_2.productId), restorable)
    }

    @Test
    fun restorableProductIds_emptyOwnedPlaySetRestoresNothing() {
        val restorable = PurchaseRepository.restorableProductIds(
            serverProductIds = setOf(SwitchType.PEBBLE_2.productId!!),
            ownedPlayProductIds = emptySet()
        )

        assertTrue(restorable.isEmpty())
    }

    @Test
    fun destroy_cancelsInternalScope() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = PurchaseRepository(context)
        val scopeField = PurchaseRepository::class.java.getDeclaredField("scope").apply {
            isAccessible = true
        }

        repository.destroy()

        val scope = scopeField.get(repository) as CoroutineScope
        val job = scope.coroutineContext[Job]
        assertTrue(job?.isCancelled == true)
    }
}
