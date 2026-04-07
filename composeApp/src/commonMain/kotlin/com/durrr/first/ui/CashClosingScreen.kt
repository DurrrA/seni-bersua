package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.durrr.first.data.repo.CashSessionRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.CashSession
import com.durrr.first.domain.model.CashSessionSummary
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppErrorBanner
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

@Composable
fun CashClosingScreen(
    cashSessionRepository: CashSessionRepository,
    settingsRepository: SettingsRepository,
    nowIso: () -> String,
) {
    var activeSummary by remember { mutableStateOf<CashSessionSummary?>(null) }
    var sessionHistory by remember { mutableStateOf(emptyList<CashSession>()) }
    var openingCashText by remember { mutableStateOf("0") }
    var moveAmountText by remember { mutableStateOf("0") }
    var moveNoteText by remember { mutableStateOf("") }
    var countedCashText by remember { mutableStateOf("0") }
    var userIdText by remember { mutableStateOf("cashier") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    fun refresh() {
        runCatching {
            val outletId = currentOutletId()
            val activeSession = cashSessionRepository.getActiveSession(outletId)
            activeSummary = activeSession?.let { cashSessionRepository.getSessionSummary(it.sessionId) }
            sessionHistory = cashSessionRepository.getSessionHistory(outletId, limit = 20)
        }.onFailure { throwable ->
            activeSummary = null
            sessionHistory = emptyList()
            message = "Failed to load cash closing data: ${throwable.message ?: "unknown error"}"
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    LazyColumn(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader("Cash Closing", "Open shift, cash in/out, and close with variance")
        }
        item {
            if (!message.isNullOrBlank()) {
                AppErrorBanner(message = message.orEmpty())
            }
        }

        val active = activeSummary
        if (active == null) {
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Open Shift")
                        OutlinedTextField(
                            value = userIdText,
                            onValueChange = { userIdText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Opened By") },
                        )
                        OutlinedTextField(
                            value = openingCashText,
                            onValueChange = { openingCashText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Opening Cash") },
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val openingCash = openingCashText.toLongOrNull()
                                            ?: error("Invalid opening cash")
                                        cashSessionRepository.openSession(
                                            outletId = currentOutletId(),
                                            openingCash = openingCash,
                                            userId = userIdText.ifBlank { "cashier" },
                                            openedAt = nowIso(),
                                        )
                                        "Shift opened"
                                    }.onSuccess {
                                        message = it
                                        refresh()
                                    }.onFailure {
                                        message = it.message ?: "Failed to open shift"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Shift")
                        }
                    }
                }
            }
        } else {
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Active Session")
                        Text("Session: ${active.session.sessionId}")
                        Text("Opened by: ${active.session.openedBy}")
                        Text("Opened at: ${active.session.openedAt}")
                        Text("Opening cash: Rp ${active.session.openingCash}")
                        Text("Cash sales: Rp ${active.cashSales}")
                        Text("Cash in: Rp ${active.cashIn}")
                        Text("Cash out: Rp ${active.cashOut}")
                        Text(
                            "Expected cash now: Rp ${active.expectedCashNow}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Cash In / Out")
                        OutlinedTextField(
                            value = moveAmountText,
                            onValueChange = { moveAmountText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Amount") },
                        )
                        OutlinedTextField(
                            value = moveNoteText,
                            onValueChange = { moveNoteText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Note") },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val amount = moveAmountText.toLongOrNull() ?: error("Invalid amount")
                                            cashSessionRepository.addCashIn(
                                                sessionId = active.session.sessionId,
                                                amount = amount,
                                                note = moveNoteText,
                                                userId = userIdText.ifBlank { "cashier" },
                                                createdAt = nowIso(),
                                            )
                                            "Cash in recorded"
                                        }.onSuccess {
                                            message = it
                                            moveAmountText = "0"
                                            moveNoteText = ""
                                            refresh()
                                        }.onFailure {
                                            message = it.message ?: "Failed to add cash in"
                                        }
                                    }
                                },
                            ) {
                                Text("Cash In")
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val amount = moveAmountText.toLongOrNull() ?: error("Invalid amount")
                                            cashSessionRepository.addCashOut(
                                                sessionId = active.session.sessionId,
                                                amount = amount,
                                                note = moveNoteText,
                                                userId = userIdText.ifBlank { "cashier" },
                                                createdAt = nowIso(),
                                            )
                                            "Cash out recorded"
                                        }.onSuccess {
                                            message = it
                                            moveAmountText = "0"
                                            moveNoteText = ""
                                            refresh()
                                        }.onFailure {
                                            message = it.message ?: "Failed to add cash out"
                                        }
                                    }
                                },
                            ) {
                                Text("Cash Out")
                            }
                        }
                    }
                }
            }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        AppSectionHeader("Close Shift")
                        OutlinedTextField(
                            value = countedCashText,
                            onValueChange = { countedCashText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Counted Cash") },
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val countedCash = countedCashText.toLongOrNull()
                                            ?: error("Invalid counted cash")
                                        val closed = cashSessionRepository.closeSession(
                                            sessionId = active.session.sessionId,
                                            countedCash = countedCash,
                                            userId = userIdText.ifBlank { "cashier" },
                                            closedAt = nowIso(),
                                        )
                                        "Shift closed. Variance: Rp ${closed.variance ?: 0L}"
                                    }.onSuccess {
                                        message = it
                                        refresh()
                                    }.onFailure {
                                        message = it.message ?: "Failed to close shift"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Close Shift")
                        }
                    }
                }
            }
        }

        item {
            AppSectionHeader("Session History")
        }
        if (sessionHistory.isEmpty()) {
            item {
                AppEmptyState(
                    title = "No session history",
                    message = "Open and close a shift to build history.",
                )
            }
        } else {
            items(sessionHistory) { session ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                        Text(
                            "Session ${session.sessionId}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Status: ${session.status.name}")
                        Text("Opened: ${session.openedAt} by ${session.openedBy}")
                        Text("Opening: Rp ${session.openingCash}")
                        if (session.closedAt != null) {
                            Text("Closed: ${session.closedAt} by ${session.closedBy ?: "-"}")
                            Text("Counted: Rp ${session.closingCashCounted ?: 0L}")
                            Text("Expected: Rp ${session.expectedCash ?: 0L}")
                            Text("Variance: Rp ${session.variance ?: 0L}")
                        }
                    }
                }
            }
        }
    }
}
