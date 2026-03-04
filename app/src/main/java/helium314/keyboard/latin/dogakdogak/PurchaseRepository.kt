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
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.purchaseDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "purchases")

/** 구매 이벤트 (UI에서 Snackbar로 표시) */
sealed class PurchaseEvent {
    data class Success(val purchasedProductIds: List<String>) : PurchaseEvent()
    data object AlreadyOwned : PurchaseEvent()
    data object Cancelled : PurchaseEvent()
    data class Error(val message: String) : PurchaseEvent()
}

/** user_purchases 테이블 행 (교차 기기 구매 동기화용) */
@Serializable
data class UserPurchaseRow(
    @SerialName("user_id") val userId: String,
    @SerialName("product_id") val productId: String,
    val verified: Boolean = true
)

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
        // Migration: 이전 키 이름
        private val LEGACY_CHILL_EFFECTS_KEY = booleanPreferencesKey("chill_effects")
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
        // Migration: chill_effects → arcade_effects (DataStore 키 이름 변경)
        scope.launch {
            context.purchaseDataStore.edit { prefs ->
                val legacyValue = prefs[LEGACY_CHILL_EFFECTS_KEY]
                if (legacyValue != null) {
                    prefs[ARCADE_EFFECTS_KEY] = legacyValue
                    prefs.remove(LEGACY_CHILL_EFFECTS_KEY)
                    Log.d(TAG, "Migrated chill_effects=$legacyValue → arcade_effects")
                }
            }
        }
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
            // Google Play 결과 기준으로 미구매 이펙트를 false로 교정
            // (이전 버전 버그로 SharedPreferences에 true가 잔류할 수 있음)
            reconcileEffectFlags(purchases)
            purchases.size
        } catch (e: Exception) {
            Log.w(TAG, "Restore failed", e)
            0
        }
    }

    /**
     * Google Play 구매 목록 기준으로 이펙트 플래그 교정.
     * 구매 목록에 없는 이펙트는 DataStore + IME SharedPreferences 모두 false로 리셋.
     */
    private suspend fun reconcileEffectFlags(purchases: List<Purchase>) {
        val allProducts = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }
            .toSet()

        val hasPremium = allProducts.contains(SwitchType.PREMIUM_EFFECTS_PRODUCT_ID)
                || allProducts.contains(SwitchType.EFFECTS_BUNDLE_PRODUCT_ID)
        val hasCutiePink = allProducts.contains(SwitchType.CUTIE_PINK_EFFECTS_PRODUCT_ID)
                || allProducts.contains(SwitchType.EFFECTS_BUNDLE_PRODUCT_ID)
        val hasArcade = allProducts.contains(SwitchType.ARCADE_EFFECTS_PRODUCT_ID)
                || allProducts.contains(SwitchType.EFFECTS_BUNDLE_PRODUCT_ID)

        val imePrefs = DeviceProtectedUtils.getSharedPreferences(context)

        context.purchaseDataStore.edit { prefs ->
            if (!hasPremium) prefs[PREMIUM_EFFECTS_KEY] = false
            if (!hasCutiePink) prefs[CUTIE_PINK_EFFECTS_KEY] = false
            if (!hasArcade) prefs[ARCADE_EFFECTS_KEY] = false
        }

        val editor = imePrefs.edit()
        if (!hasPremium) {
            editor.putBoolean("premium_effects", false)
            editor.putBoolean("premium_effects_on", false)
        }
        if (!hasCutiePink) {
            editor.putBoolean("bubble_effects", false)
            editor.putBoolean("bubble_effects_on", false)
        }
        if (!hasArcade) {
            editor.putBoolean("arcade_effects", false)
            editor.putBoolean("arcade_effects_on", false)
        }
        editor.apply()

        Log.d(TAG, "Reconciled effect flags: premium=$hasPremium, cutiePink=$hasCutiePink, arcade=$hasArcade")
    }

    private suspend fun handlePurchases(purchases: List<Purchase>, isNewPurchase: Boolean = false) {
        val imePrefs = DeviceProtectedUtils.getSharedPreferences(context)
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            billingManager.acknowledgePurchase(purchase)

            // 서버 검증 (비동기, 실패해도 로컬 구매 플로우에 영향 없음)
            if (isNewPurchase) {
                scope.launch(Dispatchers.IO) { verifyPurchaseOnServer(purchase) }
            }

            val productIds = purchase.products.toSet()
            applyProductIds(productIds)

            // isNewPurchase 전용: last_purchased_effect 설정 (이펙트 미리보기용)
            if (isNewPurchase) {
                if (productIds.contains(SwitchType.EFFECTS_BUNDLE_PRODUCT_ID) ||
                    productIds.contains(SwitchType.PREMIUM_EFFECTS_PRODUCT_ID)) {
                    imePrefs.edit().putString("last_purchased_effect", "premium").apply()
                }
                if (productIds.contains(SwitchType.CUTIE_PINK_EFFECTS_PRODUCT_ID)) {
                    imePrefs.edit().putString("last_purchased_effect", "bubble").apply()
                }
                if (productIds.contains(SwitchType.ARCADE_EFFECTS_PRODUCT_ID)) {
                    imePrefs.edit().putString("last_purchased_effect", "arcade").apply()
                }
            }
        }
    }

    /**
     * 상품 ID 세트를 로컬에 적용 (스위치 해금 + 이펙트 플래그 설정).
     * handlePurchases()와 restoreFromServer() 양쪽에서 재사용.
     */
    private suspend fun applyProductIds(productIds: Set<String>) {
        if (productIds.contains(SwitchType.BUNDLE_PRODUCT_ID)) {
            unlockAllPremiumSwitches()
        }
        for (switchType in SwitchType.getPremiumSwitches()) {
            if (switchType.productId != null && productIds.contains(switchType.productId)) {
                unlockSwitch(switchType.name)
            }
        }
        if (productIds.contains(SwitchType.EFFECTS_BUNDLE_PRODUCT_ID)) {
            context.purchaseDataStore.edit {
                it[PREMIUM_EFFECTS_KEY] = true
                it[CUTIE_PINK_EFFECTS_KEY] = true
                it[ARCADE_EFFECTS_KEY] = true
            }
        }
        if (productIds.contains(SwitchType.PREMIUM_EFFECTS_PRODUCT_ID)) {
            context.purchaseDataStore.edit { it[PREMIUM_EFFECTS_KEY] = true }
        }
        if (productIds.contains(SwitchType.CUTIE_PINK_EFFECTS_PRODUCT_ID)) {
            context.purchaseDataStore.edit { it[CUTIE_PINK_EFFECTS_KEY] = true }
        }
        if (productIds.contains(SwitchType.ARCADE_EFFECTS_PRODUCT_ID)) {
            context.purchaseDataStore.edit { it[ARCADE_EFFECTS_KEY] = true }
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

    /**
     * 구매 토큰을 Supabase Edge Function으로 전송하여 서버 검증.
     * 비동기 fire-and-forget: 실패해도 로컬 구매에 영향 없음.
     */
    private suspend fun verifyPurchaseOnServer(purchase: Purchase) {
        try {
            val body = Json.encodeToString(mapOf(
                "purchaseToken" to purchase.purchaseToken,
                "orderId" to (purchase.orderId ?: ""),
                "productIds" to purchase.products.joinToString(","),
                "packageName" to context.packageName,
                "timestamp" to System.currentTimeMillis().toString(),
                "nonce" to java.util.UUID.randomUUID().toString()
            ))
            SupabaseModule.client.functions.invoke(
                function = "verify-purchase",
                body = body,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                }
            )
            Log.d(TAG, "Server purchase verification sent")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Server verification failed (non-blocking): ${e.message}")
            else Log.w(TAG, "Server verification failed (non-blocking)")
        }
    }

    /**
     * 로그인 시 호출: Google Play 구매를 서버에 동기화 + 서버 구매를 로컬에 복원.
     * fire-and-forget — 서버 실패해도 로컬 구매에 영향 없음.
     */
    fun onLoginSync() {
        scope.launch(Dispatchers.IO) {
            syncPurchasesToServer()
            restoreFromServer()
        }
    }

    /**
     * 현재 기기의 Google Play 구매를 서버 검증 함수로 재동기화.
     * 로그인 상태에서만 동작하며, 직접 user_purchases 테이블을 쓰지 않는다.
     */
    private suspend fun syncPurchasesToServer() {
        try {
            SupabaseModule.client.auth.currentUserOrNull()?.id ?: return
            val purchases = billingManager.queryPurchases()
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (purchases.isEmpty()) return

            purchases.forEach { purchase ->
                verifyPurchaseOnServer(purchase)
            }

            val syncedProductCount = purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap { it.products }
                .distinct()
                .size
            Log.d(TAG, "Synced $syncedProductCount purchases to server via verify-purchase")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "syncPurchasesToServer failed: ${e.message}")
            else Log.w(TAG, "syncPurchasesToServer failed")
        }
    }

    /**
     * user_purchases 테이블에서 사용자 구매 기록을 조회하여 로컬에 적용.
     * 서버 데이터는 additive only — 로컬에 추가만, 삭제 안 함.
     */
    private suspend fun restoreFromServer() {
        try {
            val userId = SupabaseModule.client.auth.currentUserOrNull()?.id ?: return
            val rows = SupabaseModule.client.postgrest.from("user_purchases")
                .select { filter { eq("user_id", userId) } }
                .decodeList<UserPurchaseRow>()
            val productIds = rows.map { it.productId }.toSet()
            if (productIds.isNotEmpty()) {
                applyProductIds(productIds)
                Log.d(TAG, "Restored ${productIds.size} purchases from server")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "restoreFromServer failed: ${e.message}")
            else Log.w(TAG, "restoreFromServer failed")
        }
    }

    fun destroy() {
        billingManager.destroy()
    }
}
