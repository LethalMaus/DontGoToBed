package dev.jamescullimore.dontgotobed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.random.Random

@Composable
fun SupportScreen(onBack: () -> Unit, onPrivacy: () -> Unit = {}) {
    val recoveryStore = rememberPurchaseRecoveryStore()
    LaunchedEffect(recoveryStore) { SupportPurchases.attachStore(recoveryStore) }
    var confirmCancelled by remember { mutableStateOf(false) }
    var adult by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var first by remember { mutableIntStateOf(Random.nextInt(13, 30)) }
    var second by remember { mutableIntStateOf(Random.nextInt(3, 10)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                adult = false; answer = ""; wrong = false; confirmCancelled = false
                first = Random.nextInt(13, 30); second = Random.nextInt(3, 10)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(adult) { if (adult) {
        SupportPurchases.load()
    } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("Back to game") }
            TextButton(onClick = onPrivacy) { Text("Privacy policy") }
            if (!adult) {
                Text("For grown-ups", style = MaterialTheme.typography.headlineSmall)
                Text("Please ask a grown-up to open the support page.")
                OutlinedTextField(value = answer, onValueChange = { answer = it; wrong = false }, label = { Text("What is $first multiplied by $second?") }, singleLine = true, isError = wrong)
                Button(onClick = { adult = answer.trim().toIntOrNull() == first * second; wrong = !adult }) { Text("Continue") }
                if (wrong) Text("Please ask a grown-up to try again.")
            } else {
                Text("Support our family game", style = MaterialTheme.typography.headlineSmall)
                Text("We made Don't Go To Bed together. If it made you smile, you can leave an optional tip to support its development.")
                Text("Every character and all gameplay stay free. Tips are one-time purchases, can be repeated, and do not unlock items or advantages. There is no subscription.")
                Text("Purchases use Apple or Google and RevenueCat to process and verify the transaction. No game account is required.")
                if (SupportPurchases.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Waiting for the store…") }
                SupportPurchases.packages.forEach { item ->
                    Button(enabled = !SupportPurchases.busy && !SupportPurchases.awaitingApproval, onClick = { SupportPurchases.purchase(item) }) {
                        Text("Leave a tip · ${item.storeProduct.price.formatted}")
                    }
                }
                SupportPurchases.message?.let { Text(it) }
                if (SupportPurchases.awaitingApproval) TextButton(enabled = !SupportPurchases.busy, onClick = SupportPurchases::checkPending) { Text("Check payment") }
                if (SupportPurchases.checkedWithoutCompletion) TextButton(enabled = !SupportPurchases.busy, onClick = { confirmCancelled = true }) { Text("The store says cancelled or declined") }
                if (SupportPurchases.packages.isEmpty()) TextButton(enabled = !SupportPurchases.busy, onClick = SupportPurchases::load) { Text("Try again") }
                Text("Tips are consumed when completed, so there is no purchase to restore. Your store keeps your purchase history.")
            }
        }
    }
    if (adult && confirmCancelled) AlertDialog(
        onDismissRequest = { confirmCancelled = false },
        title = { Text("Clear the payment reminder?") },
        text = { Text("Only continue if your store account confirms this payment was cancelled or declined. This does not cancel a purchase or an approval request at Apple or Google.") },
        confirmButton = { TextButton(onClick = { SupportPurchases.clearCancelledPending(); confirmCancelled = false }) { Text("Clear reminder") } },
        dismissButton = { TextButton(onClick = { confirmCancelled = false }) { Text("Keep waiting") } }
    )
}
