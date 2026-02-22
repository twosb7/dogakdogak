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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
        private val CUTIE_PINK_EFFECTS_KEY = booleanPreferencesKey("bubble_effects")
        private val ARCADE_EFFECTS_KEY = booleanPreferencesKey("arcade_effects")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _purchaseEvents = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 5)
    val purchaseEvents: SharedFlow<PurchaseEvent> = _purchaseEvents

    private val billingManager = BillingManager(context) { responseCode, purchases ->
        scope.launch {
            when (responseCode) {
                BillingManager.BillingResponseCode.OK -> {
                    if (purchases != null && purchases.isNotEmpty()) {
                        handlePurchases(purchases, isNewPurchase = true)
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

    /** 구매된 스위치 이름 Set */
    val purchasedSwitchesFlow: Flow<Set<String>> =
        context.purchaseDataStore.data.map { it[PURCHASED_SWITCHES_KEY] ?: emptySet() }

    /** 프리미엄 이펙트 구매 여부 */
    val hasPremiumEffectsFlow: Flow<Boolean> =
        context.purchaseDataStore.data.map { it[PREMIUM_EFFECTS_KEY] ?: false }

    /** 큐티핑크 콤보 이펙트 구매 여부 */
    val hasCutiePinkEffectsFlow: Flow<Boolean> =
        context.purchaseDataStore.data.map { it[CUTIE_PINK_EFFECTS_KEY] ?: false }

    /** Arcade 이펙트 구매 여부 */
    val hasArcadeEffectsFlow: Flow<Boolean> =
        context.purchaseDataStore.data.map { it[ARCADE_EFFECTS_KEY] ?: false }

    init {
        scope.launch { restorePurchases() }
        // DataStore → DeviceProtectedUtils SharedPreferences 자동 동기화 (IME 서비스 접근용)
        val imePrefs = DeviceProtectedUtils.getSharedPreferences(context)
        // 세션 상태 변화로 인한 일시적 false emit이 IME prefs를 덮어쓰지 않도록:
        // true로 확인된 값은 기록하고, false로는 절대 되돌리지 않음.
        scope.launch {
            hasPremiumEffectsFlow.collect { hasPremium ->
                if (hasPremium) {
                    imePrefs.edit().putBoolean("premium_effects", true).apply()
                    Log.d(TAG, "Synced premium_effects=true to IME SharedPreferences")
                }
            }
        }
        scope.launch {
            hasCutiePinkEffectsFlow.collect { hasBubble ->
                if (hasBubble) {
                    imePrefs.edit().putBoolean("bubble_effects", true).apply()
                    Log.d(TAG, "Synced bubble_effects=true to IME SharedPreferences")
                }
            }
        }
        scope.launch {
            hasArcadeEffectsFlow.collect { hasArcade ->
                if (hasArcade) {
                    imePrefs.edit().putBoolean("arcade_effects", true).apply()
                    Log.d(TAG, "Synced arcade_effects=true to IME SharedPreferences")
                }
            }
        }
        scope.launch {
            purchasedSwitchesFlow.collect { switches ->
                if (switches.isNotEmpty()) {
                    val current = imePrefs.getStringSet("purchased_switches", emptySet()) ?: emptySet()
                    val merged = current + switches
                    imePrefs.edit().putStringSet("purchased_switches", merged).apply()
                    Log.d(TAG, "Synced purchased_switches=${merged.size} to IME SharedPreferences")
                }
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

    private suspend fun handlePurchases(purchases: List<Purchase>, isNewPurchase: Boolean = false) {
        val imePrefs = DeviceProtectedUtils.getSharedPreferences(context)
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
                if (isNewPurchase) {
                    imePrefs.edit().putString("last_purchased_effect", "premium").apply()
                }
            }

            if (products.contains(SwitchType.CUTIE_PINK_EFFECTS_PRODUCT_ID)) {
                context.purchaseDataStore.edit {
                    it[CUTIE_PINK_EFFECTS_KEY] = true
                }
                if (isNewPurchase) {
                    imePrefs.edit().putString("last_purchased_effect", "bubble").apply()
                }
            }

            if (products.contains(SwitchType.ARCADE_EFFECTS_PRODUCT_ID)) {
                context.purchaseDataStore.edit {
                    it[ARCADE_EFFECTS_KEY] = true
                }
                if (isNewPurchase) {
                    imePrefs.edit().putString("last_purchased_effect", "arcade").apply()
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
