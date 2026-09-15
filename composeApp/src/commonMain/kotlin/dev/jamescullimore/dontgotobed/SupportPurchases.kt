package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.models.CacheFetchPolicy
import com.revenuecat.purchases.kmp.models.ProductType
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode

/** Initialized only after an adult opens Support. No purchase state enters the game world. */
object SupportPurchases {
    private var client: Purchases? = null
    var packages: List<Package> by mutableStateOf(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var recoveryStore: SettingsStore? = null
    private var pending: PendingTip? = null
    var awaitingApproval by mutableStateOf(false)
        private set
    var checkedWithoutCompletion by mutableStateOf(false)
        private set
    private val ids = listOf("tip_small", "tip_medium", "tip_large")

    fun attachStore(store: SettingsStore) {
        recoveryStore = store
        pending = PendingTip.read(store)
        awaitingApproval = pending != null
    }

    /** Only called after an adult checks the store and confirms cancellation/decline. */
    fun clearCancelledPending() {
        if (busy || !checkedWithoutCompletion) return
        clearPending()
        message = "Payment reminder cleared. Check your store purchase history before leaving another tip."
    }

    private fun clearPending() {
        recoveryStore?.let(PendingTip::clear)
        pending = null
        awaitingApproval = false
        checkedWithoutCompletion = false
    }

    fun load() {
        if (busy) return
        val key = revenueCatPublicKey()
        if (key.isBlank()) { message = "Tips aren't available yet. Thank you for playing!"; return }
        busy = true
        message = null
        packages = emptyList()
        try {
            val sdk = client ?: Purchases.configure(PurchasesConfiguration.Builder(key)
                .showInAppMessagesAutomatically(false).diagnosticsEnabled(false).build()).also { client = it }
            sdk.getOfferings(onError = {
                busy = false; message = "Couldn't load tips. Check your connection and try again."
            }, onSuccess = { offerings ->
                packages = offerings.all["support"]?.availablePackages.orEmpty()
                    .filter { it.identifier in ids && it.storeProduct.id in ids && it.storeProduct.type == ProductType.INAPP }
                    .sortedBy { ids.indexOf(it.identifier) }
                busy = false
                if (packages.isEmpty()) message = "Tips aren't available in the store right now. You can keep playing for free."
                if (awaitingApproval) checkPending()
            })
        } catch (_: Exception) {
            busy = false; message = "Tips are unavailable right now. You can keep playing for free."
        }
    }

    fun purchase(item: Package) {
        if (busy || awaitingApproval || item !in packages) return
        val sdk = client ?: return
        busy = true
        message = null
        val startedAt = currentTimeMillis()
        pending = PendingTip(item.storeProduct.id, startedAt).also { tip -> recoveryStore?.let(tip::save) }
        awaitingApproval = true
        checkedWithoutCompletion = false
        try {
            sdk.purchase(packageToPurchase = item, onError = { error, cancelled ->
                busy = false
                if (error.code != PurchasesErrorCode.PaymentPendingError) clearPending()
                message = when {
                    cancelled -> "Purchase cancelled. Nothing has changed in your game."
                    error.code == PurchasesErrorCode.PaymentPendingError -> "Waiting for payment approval. No need to try again while it is pending."
                    else -> "The purchase couldn't be completed. Please check the store and try again."
                }
            }, onSuccess = { _, _ ->
                busy = false
                clearPending()
                message = "Thank you for supporting our family game! All gameplay stays free."
            })
        } catch (_: Exception) {
            busy = false
            message = "The store was interrupted. Check payment status before trying again."
        }
    }

    fun checkPending() {
        if (busy || !awaitingApproval) return
        val sdk = client ?: return
        val payment = pending ?: return
        busy = true
        checkedWithoutCompletion = false
        try { sdk.getCustomerInfo(fetchPolicy = CacheFetchPolicy.FETCH_CURRENT,
            onError = { busy = false; message = "Couldn't check the payment. Please try again later." },
            onSuccess = { info ->
                busy = false
                if (info.nonSubscriptionTransactions.any { it.productIdentifier == payment.product && it.purchaseDateMillis >= payment.startedAt }) {
                    clearPending()
                    message = "Thank you! Your tip has been confirmed. All gameplay stays free."
                } else {
                    checkedWithoutCompletion = true
                    message = "No completed payment yet. Check approval in your store account; you can return to the game."
                }
            })
        } catch (_: Exception) {
            busy = false
            message = "Couldn't check the payment. Please try again later."
        }
    }

}
