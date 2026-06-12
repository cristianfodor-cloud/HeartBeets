package com.heartbeets.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartbeets.app.billing.BillingManager
import com.heartbeets.app.billing.BillingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    billingVm: BillingViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity,
    ),
) {
    val trialActive by billingVm.trialActive.collectAsState()
    val trialDays by billingVm.trialDaysRemaining.collectAsState()
    val subscribed by billingVm.subscriptionActive.collectAsState()
    val products by billingVm.productDetails.collectAsState()
    val activity = LocalContext.current as Activity

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Subscription") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            if (subscribed) {
                // Active subscriber
                Text(
                    "\u2705 Subscribed",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "You have full access to live sharing features. Thank you for your support!",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            } else if (trialActive) {
                // Trial period
                Text(
                    "Free Trial",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$trialDays days remaining",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "You have full access to live sharing during your trial. " +
                        "Subscribe anytime to keep sharing after the trial ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Trial expired, not subscribed
                Text(
                    "Trial Ended",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Subscribe to continue sharing your heartbeat live with friends.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(32.dp))

            if (!subscribed) {
                Text(
                    "Choose a plan",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(16.dp))

                val monthlyProduct = products.find { it.productId == BillingManager.PRODUCT_MONTHLY }
                val yearlyProduct = products.find { it.productId == BillingManager.PRODUCT_YEARLY }

                // Monthly plan
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Monthly", style = MaterialTheme.typography.titleMedium)
                            val price = monthlyProduct?.subscriptionOfferDetails
                                ?.firstOrNull()?.pricingPhases?.pricingPhaseList
                                ?.firstOrNull()?.formattedPrice ?: "$2.99"
                            Text(
                                "$price/month",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = { monthlyProduct?.let { billingVm.launchPurchase(activity, it) } },
                            enabled = monthlyProduct != null,
                        ) {
                            Text("Subscribe")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Yearly plan
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Yearly", style = MaterialTheme.typography.titleMedium)
                            val price = yearlyProduct?.subscriptionOfferDetails
                                ?.firstOrNull()?.pricingPhases?.pricingPhaseList
                                ?.firstOrNull()?.formattedPrice ?: "$24.99"
                            Text(
                                "$price/year — Save 30%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { yearlyProduct?.let { billingVm.launchPurchase(activity, it) } },
                            enabled = yearlyProduct != null,
                        ) {
                            Text("Subscribe")
                        }
                    }
                }

                if (products.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Loading plans from Google Play…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // --- Google Sign-In / Account backup section ---
            val isLinked by billingVm.isLinked.collectAsState()

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (isLinked) {
                Text(
                    "\u2705 Signed in with Google — your data is backed up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                val signInLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    billingVm.handleGoogleSignInResult(result.data) { success ->
                        if (success) {
                            // Restore codes/friends from backup if local is empty
                            billingVm.restoreData { }
                        }
                    }
                }

                Text(
                    "Sign in with Google to back up your codes and keep your trial across devices.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { signInLauncher.launch(billingVm.getGoogleSignInIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in with Google")
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Subscriptions are managed through Google Play. " +
                    "You can cancel anytime from your Play Store settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
