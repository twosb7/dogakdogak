package helium314.keyboard.latin.dogakdogak

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing 래퍼.
 * 인앱 구매 플로우, 구매 확인(acknowledge), 구매 상태 조회.
 * 연결 끊김 시 지수 백오프 재연결.
 */
class BillingManager(
    context: Context,
    private val onPurchaseResult: (BillingResponseCode, List<Purchase>?) -> Unit
) {

    enum class BillingResponseCode {
        OK, USER_CANCELED, ITEM_ALREADY_OWNED, ERROR, SERVICE_UNAVAILABLE
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val code = when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> BillingResponseCode.OK
            BillingClient.BillingResponseCode.USER_CANCELED -> BillingResponseCode.USER_CANCELED
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingResponseCode.ITEM_ALREADY_OWNED
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> BillingResponseCode.SERVICE_UNAVAILABLE
            else -> BillingResponseCode.ERROR
        }
        onPurchaseResult(code, purchases)
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    @Volatile
    private var isConnected = false

    init {
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
            }
        })
    }

    /** suspend로 연결 보장. 최대 3회 재시도 (지수 백오프) */
    private suspend fun ensureConnected(): Boolean {
        if (isConnected) return true

        for (attempt in 0 until MAX_RETRY) {
            val connected = suspendCancellableCoroutine { cont ->
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        isConnected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                        if (cont.isActive) cont.resume(isConnected)
                    }
                    override fun onBillingServiceDisconnected() {
                        isConnected = false
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
            if (connected) return true
            delay(RETRY_DELAY_MS * (attempt + 1))
        }
        return false
    }

    /** 구매 플로우 시작. 실패 시 에러 메시지 반환 */
    suspend fun launchPurchase(activity: Activity, productId: String): String? {
        if (!ensureConnected()) return "Google Play 서비스에 연결할 수 없습니다"

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient.queryProductDetails(params)

        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return "상품 정보를 불러올 수 없습니다"
        }

        val productDetails = result.productDetailsList?.firstOrNull()
            ?: return "상품을 찾을 수 없습니다 (Google Play Console에 등록 필요)"

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()

        val flowResult = billingClient.launchBillingFlow(activity, flowParams)
        return if (flowResult.responseCode == BillingClient.BillingResponseCode.OK) {
            null
        } else {
            "구매를 시작할 수 없습니다 (${flowResult.debugMessage})"
        }
    }

    /** 구매 확인 (acknowledge). 3일 내 미확인 시 자동 환불됨 */
    suspend fun acknowledgePurchase(purchase: Purchase): Boolean {
        if (purchase.isAcknowledged) return true
        if (!ensureConnected()) return false

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        val result = billingClient.acknowledgePurchase(params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    /** 기존 구매 상태 조회 (구매 복원) */
    suspend fun queryPurchases(): List<Purchase> {
        if (!ensureConnected()) return emptyList()

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = billingClient.queryPurchasesAsync(params)
        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.purchasesList
        } else {
            emptyList()
        }
    }

    /** 상품 가격 조회. productId → 포맷된 가격 문자열 */
    suspend fun queryPrices(productIds: List<String>): Map<String, String> {
        if (!ensureConnected()) return emptyMap()

        val productList = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient.queryProductDetails(params)

        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.productDetailsList?.associate { detail ->
                detail.productId to (detail.oneTimePurchaseOfferDetails?.formattedPrice ?: "")
            } ?: emptyMap()
        } else {
            emptyMap()
        }
    }

    fun destroy() {
        billingClient.endConnection()
    }

    companion object {
        private const val MAX_RETRY = 3
        private const val RETRY_DELAY_MS = 1000L

        val ALL_PRODUCT_IDS = listOf(
            "com.dogakdogak.switch.pebble2",
            "com.dogakdogak.switch.pebble3",
            "com.dogakdogak.switch.pebble4",
            "com.dogakdogak.switch.pebble5",
            "com.dogakdogak.switch.pebble6",
            "com.dogakdogak.switch.pebble7",
            "com.dogakdogak.switch.pebble8",
            "com.dogakdogak.switch.pebble9",
            "com.dogakdogak.switch.pebble10",
            "com.dogakdogak.switch.pebble11",
            SwitchType.BUNDLE_PRODUCT_ID,
            SwitchType.PREMIUM_EFFECTS_PRODUCT_ID,
            SwitchType.CUTIE_PINK_EFFECTS_PRODUCT_ID,
            SwitchType.ARCADE_EFFECTS_PRODUCT_ID
        )
    }
}
