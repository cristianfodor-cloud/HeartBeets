package com.heartbeets.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.AcknowledgePurchaseParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages subscriptions and trial period for HeartBeets sharing features.
 *
 * - 3-month free trial (tracked server-side in Firestore, fallback to local)
 * - Monthly ($2.99) or Yearly ($24.99) subscription after trial
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_MONTHLY = "heartbeets_sharing_monthly"
        const val PRODUCT_YEARLY = "heartbeets_sharing_yearly"
        private const val PREFS_NAME = "heartbeets_billing"
        private const val KEY_TRIAL_START = "trial_start_ms"
        const val TRIAL_DURATION_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _subscriptionActive = MutableStateFlow(false)
    val subscriptionActive: StateFlow<Boolean> = _subscriptionActive.asStateFlow()

    private val _trialActive = MutableStateFlow(true) // optimistic until checked
    val trialActive: StateFlow<Boolean> = _trialActive.asStateFlow()

    private val _trialDaysRemaining = MutableStateFlow(90)
    val trialDaysRemaining: StateFlow<Int> = _trialDaysRemaining.asStateFlow()

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    private var billingClient: BillingClient? = null

    /** Whether the user has access to sharing features (trial OR subscription). */
    val hasAccess: Boolean
        get() = _trialActive.value || _subscriptionActive.value

    init {
        // Use local cache for immediate status; will be corrected by setTrialStart()
        refreshTrialFromLocal()
    }

    /**
     * Set the trial start from server (Firestore). Called after auth resolves.
     */
    fun setTrialStart(trialStartMs: Long) {
        prefs.edit().putLong(KEY_TRIAL_START, trialStartMs).apply()
        refreshTrialFromLocal()
    }

    private fun refreshTrialFromLocal() {
        val trialStart = prefs.getLong(KEY_TRIAL_START, 0L)
        if (trialStart == 0L) {
            // No data yet, assume trial active until server responds
            _trialActive.value = true
            _trialDaysRemaining.value = 90
            return
        }
        val elapsed = System.currentTimeMillis() - trialStart
        val remaining = TRIAL_DURATION_MS - elapsed
        _trialActive.value = remaining > 0
        _trialDaysRemaining.value = if (remaining > 0) (remaining / (24 * 60 * 60 * 1000)).toInt() else 0
    }

    /**
     * Connect to Google Play Billing and query subscription status.
     */
    fun connect() {
        val client = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        billingClient = client

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    querySubscription()
                    queryProducts()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Will reconnect on next access attempt
            }
        })
    }

    private fun querySubscription() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        purchase.products.any { it == PRODUCT_MONTHLY || it == PRODUCT_YEARLY }
                }
                _subscriptionActive.value = active

                // Acknowledge unacknowledged purchases
                purchases.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
                }.forEach { purchase ->
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    client.acknowledgePurchase(ackParams) { }
                }
            }
        }
    }

    private fun queryProducts() {
        val client = billingClient ?: return
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = details
            }
        }
    }

    /**
     * Launch the subscription purchase flow.
     */
    fun launchPurchase(activity: Activity, productDetails: ProductDetails) {
        val client = billingClient ?: return
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        client.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    _subscriptionActive.value = true
                    // Acknowledge
                    if (!purchase.isAcknowledged) {
                        val client = billingClient ?: return
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        client.acknowledgePurchase(ackParams) { }
                    }
                }
            }
        }
    }

    /**
     * Refresh subscription and trial status. Call on resume.
     */
    fun refresh() {
        refreshTrialFromLocal()
        querySubscription()
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
    }
}
