package com.heartbeets.app.billing

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.heartbeets.sharing.HeartbeatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped ViewModel providing subscription + auth state to all screens.
 */
class BillingViewModel(application: Application) : AndroidViewModel(application) {

    val manager = BillingManager(application)
    val authManager = AuthManager(application)
    private val sharingRepo = HeartbeatRepository(application)

    val subscriptionActive: StateFlow<Boolean> = manager.subscriptionActive
    val trialActive: StateFlow<Boolean> = manager.trialActive
    val trialDaysRemaining: StateFlow<Int> = manager.trialDaysRemaining
    val productDetails: StateFlow<List<ProductDetails>> = manager.productDetails
    val isLinked: StateFlow<Boolean> = authManager.isLinked
    val user = authManager.user

    private val _authReady = MutableStateFlow(false)
    val authReady: StateFlow<Boolean> = _authReady.asStateFlow()

    /** Whether user can use sharing features (trial or subscribed). */
    val hasAccess: Boolean get() = manager.hasAccess

    init {
        manager.connect()
        viewModelScope.launch {
            // Sign in anonymously (silent) and fetch server-side trial start
            authManager.ensureSignedIn()
            val trialStart = authManager.getOrCreateTrialStart()
            manager.setTrialStart(trialStart)
            _authReady.value = true
        }
    }

    fun refresh() {
        manager.refresh()
        viewModelScope.launch {
            val trialStart = authManager.getOrCreateTrialStart()
            manager.setTrialStart(trialStart)
        }
    }

    fun launchPurchase(activity: Activity, productDetails: ProductDetails) {
        manager.launchPurchase(activity, productDetails)
    }

    fun getGoogleSignInIntent(): Intent = authManager.getGoogleSignInIntent()

    fun handleGoogleSignInResult(data: Intent?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = authManager.handleGoogleSignInResult(data)
            if (success) {
                // Re-fetch trial from potentially merged account
                val trialStart = authManager.getOrCreateTrialStart()
                manager.setTrialStart(trialStart)
                // Backup current local data to Firestore
                triggerBackup()
            }
            onResult(success)
        }
    }

    /**
     * Backup local codes + friends to Firestore. Call after mutations.
     */
    fun triggerBackup() {
        viewModelScope.launch {
            val codes = sharingRepo.getRawCodes()
            val friends = sharingRepo.getRawFriends()
            authManager.backupUserData(
                codes = if (codes.isBlank()) emptyList() else listOf(codes),
                friends = if (friends.isBlank()) emptyList() else listOf(friends),
            )
        }
    }

    /**
     * Restore codes + friends from Firestore into local storage.
     */
    fun restoreData(onResult: (restored: Boolean) -> Unit) {
        viewModelScope.launch {
            val (codes, friends) = authManager.restoreUserData()
            var restored = false
            if (!codes.isNullOrEmpty() && sharingRepo.getRawCodes().isBlank()) {
                sharingRepo.restoreRawCodes(codes.first())
                restored = true
            }
            if (!friends.isNullOrEmpty() && sharingRepo.getRawFriends().isBlank()) {
                sharingRepo.restoreRawFriends(friends.first())
                restored = true
            }
            onResult(restored)
        }
    }

    override fun onCleared() {
        super.onCleared()
        manager.disconnect()
    }
}
