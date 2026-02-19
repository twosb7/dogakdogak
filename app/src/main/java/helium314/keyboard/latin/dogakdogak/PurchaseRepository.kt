package helium314.keyboard.latin.dogakdogak

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.Purchase
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.purchaseDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "purchases")

/** 구매 이벤트 (UI에서 Snackbar로 표시) */
sealed class PurchaseEvent {
    data class Success(val purchasedProductIds: List<String>) : PurchaseEvent()
    data object AlreadyOwned : PurchaseEvent()
    data object Cancelled : PurchaseEvent()
    data class Error(val message: String) : PurchaseEvent()
}

/**
 * 구매 상태 관리.
 * DataStore로 로컬 캐시 + BillingClient로 서버 검증.
 */
class PurchaseRepository(private val context: Context) {

    companion object {
        private const val TAG = "PurchaseRepo"
        private val PURCHASED_SWITCHES_KEY = stringSetPreferencesKey("purchased_switches")
        private val PREMIUM_EFFECTS_KEY = booleanPreferencesKey("premium_effects")
        private val BUBBLE_EFFECTS_KEY = booleanPreferencesKey("bubble_effects")

        /** 모든 프리미엄 기능 무료 해금 이메일 */
        private val WHITELIST_EMAILS = setOf(
            "REMOVED",
            "REMOVED",
            "twosb7@gmail.com",
            "mogiy7633@gmail.com"
        )

    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 5)
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents

    private val billingManager = BillingManager(context) { responseCode, purchases ->
        scope.launch {
            when (responseCode) {
                BillingManager.BillingResponseCode.OK -> {
                    if (purchases != null && purchases.isNotEmpty()) {
                        handlePurchases(purchases)
                        val productIds = purchases.flatMap { it.products }
                        _purchaseEvents.emit(PurchaseEvent.Success(productIds))
                    }
                }
                BillingManager.BillingResponseCode.USER_CANCELED -> {
                    _purchaseEvents.emit(PurchaseEvent.Cancelled)
                }
                BillingManager.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    restorePurchases()
                    _purchaseEvents.emit(PurchaseEvent.AlreadyOwned)
                }
                BillingManager.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                    _purchaseEvents.emit(PurchaseEvent.Error("Google Play 서비스를 사용할 수 없습니다"))
                }
                BillingManager.BillingResponseCode.ERROR -> {
                    _purchaseEvents.emit(PurchaseEvent.Error("구매 처리 중 오류가 발생했습니다"))
                }
            }
        }
    }

    private val isWhitelistedFlow: Flow<Boolean> = SupabaseModule.client.auth.sessionStatus
        .map { status ->
            if (status is SessionStatus.Authenticated) {
                val email = SupabaseModule.client.auth.currentUserOrNull()?.email
                email != null && email in WHITELIST_EMAILS
            } else false
        }

    /** 구매된 스위치 이름 Set (화이트리스트 이메일이면 전체 해금) */
    val purchasedSwitchesFlow: Flow<Set<String>> = combine(
        context.purchaseDataStore.data.map { it[PURCHASED_SWITCHES_KEY] ?: emptySet() },
        isWhitelistedFlow
    ) { purchased, whitelisted ->
        if (whitelisted) {
            purchased + SwitchType.getPremiumSwitches().map { it.name }.toSet()
        } else purchased
    }

    /** 프리미엄 이펙트 구매 여부 (화이트리스트 이메일이면 자동 활성화) */
    val hasPremiumEffectsFlow: Flow<Boolean> = combine(
        context.purchaseDataStore.data.map { it[PREMIUM_EFFECTS_KEY] ?: false },
        isWhitelistedFlow
    ) { purchased, whitelisted -> purchased || whitelisted }

    /** 버블 콤보 이펙트 구매 여부 (화이트리스트 이메일이면 자동 활성화) */
    val hasBubbleEffectsFlow: Flow<Boolean> = combine(
        context.purchaseDataStore.data.map { it[BUBBLE_EFFECTS_KEY] ?: false },
        isWhitelistedFlow
    ) { purchased, whitelisted -> purchased || whitelisted }

    init {
        scope.launch { restorePurchases() }
        // DataStore → DeviceProtectedUtils SharedPreferences 자동 동기화 (IME 서비스 접근용)
        val imePrefs = DeviceProtectedUtils.getSharedPreferences(context)
        scope.launch {
            hasPremiumEffectsFlow.collect { hasPremium ->
                imePrefs.edit()
                    .putBoolean("premium_effects", hasPremium)
                    .apply()
                Log.d(TAG, "Synced premium_effects=$hasPremium to IME SharedPreferences")
            }
        }
        scope.launch {
            hasBubbleEffectsFlow.collect { hasBubble ->
                imePrefs.edit()
                    .putBoolean("bubble_effects", hasBubble)
                    .apply()
                Log.d(TAG, "Synced bubble_effects=$hasBubble to IME SharedPreferences")
            }
        }
        scope.launch {
            purchasedSwitchesFlow.collect { switches ->
                imePrefs.edit()
                    .putStringSet("purchased_switches", switches)
                    .apply()
                Log.d(TAG, "Synced purchased_switches=${switches.size} to IME SharedPreferences")
            }
        }
    }

    suspend fun launchPurchase(activity: Activity, productId: String) {
        val error = billingManager.launchPurchase(activity, productId)
        if (error != null) {
            _purchaseEvents.emit(PurchaseEvent.Error(error))
        }
    }

    suspend fun restorePurchases(): Int {
        return try {
            val purchases = billingManager.queryPurchases()
            if (purchases.isNotEmpty()) {
                handlePurchases(purchases)
            }
            purchases.size
        } catch (e: Exception) {
            Log.w(TAG, "Restore failed", e)
            0
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            billingManager.acknowledgePurchase(purchase)

            val products = purchase.products

            if (products.contains(SwitchType.BUNDLE_PRODUCT_ID)) {
                unlockAllPremiumSwitches()
            }

            for (switchType in SwitchType.getPremiumSwitches()) {
                if (switchType.productId != null && products.contains(switchType.productId)) {
                    unlockSwitch(switchType.name)
                }
            }

            if (products.contains(SwitchType.PREMIUM_EFFECTS_PRODUCT_ID)) {
                context.purchaseDataStore.edit {
                    it[PREMIUM_EFFECTS_KEY] = true
                }
            }

            if (products.contains(SwitchType.BUBBLE_EFFECTS_PRODUCT_ID)) {
                context.purchaseDataStore.edit {
                    it[BUBBLE_EFFECTS_KEY] = true
                }
            }
        }
    }

    private suspend fun unlockSwitch(switchName: String) {
        context.purchaseDataStore.edit { prefs ->
            val current = prefs[PURCHASED_SWITCHES_KEY] ?: emptySet()
            prefs[PURCHASED_SWITCHES_KEY] = current + switchName
        }
    }

    private suspend fun unlockAllPremiumSwitches() {
        context.purchaseDataStore.edit { prefs ->
            val allPremium = SwitchType.getPremiumSwitches().map { it.name }.toSet()
            val current = prefs[PURCHASED_SWITCHES_KEY] ?: emptySet()
            prefs[PURCHASED_SWITCHES_KEY] = current + allPremium
        }
    }

    /** 상품 가격 조회. productId → 포맷된 가격 문자열 */
    suspend fun fetchProductPrices(productIds: List<String>): Map<String, String> {
        return billingManager.queryPrices(productIds)
    }

    fun destroy() {
        billingManager.destroy()
    }
}
