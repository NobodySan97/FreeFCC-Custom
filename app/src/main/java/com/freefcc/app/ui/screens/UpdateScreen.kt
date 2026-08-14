package com.freefcc.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freefcc.app.AppState
import com.freefcc.app.FccViewModel
import com.freefcc.app.ui.components.GlowButton
import com.freefcc.app.ui.components.GlowButtonSize
import com.freefcc.app.ui.components.GlowCard
import com.freefcc.app.ui.components.ProgressDisplay
import com.freefcc.app.ui.components.StatusBadge
import com.freefcc.app.ui.theme.Amber
import com.freefcc.app.ui.theme.BrandCyan
import com.freefcc.app.ui.theme.DarkBorder
import com.freefcc.app.ui.theme.StatusGreen
import com.freefcc.app.ui.theme.StatusRed
import com.freefcc.app.ui.theme.TextMuted
import com.freefcc.app.ui.theme.TextPrimary
import com.freefcc.app.ui.theme.TextSecondary

private val BottomNavHeight = 80.dp
private val PageHorizontalPadding = 16.dp
private val PageTopPadding = 8.dp
private val PageBottomPadding = 16.dp
private val SectionSpacing = 10.dp

@Composable
fun UpdateScreen(
    viewModel: FccViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    UpdateScreenContent(
        state = state,
        onCheckForUpdates = { viewModel.checkForUpdates(force = true) },
        onSetUpdateChannel = { channel -> viewModel.setUpdateChannel(channel) },
        onDownloadUpdate = { viewModel.downloadUpdate() },
        onInstallUpdate = { viewModel.installUpdate() },
        onReDownloadUpdate = { viewModel.reDownloadUpdate() },
        modifier = modifier
    )
}

@Composable
fun UpdateScreenContent(
    state: AppState,
    onCheckForUpdates: () -> Unit,
    onSetUpdateChannel: (String) -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onReDownloadUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding)
            .padding(bottom = BottomNavHeight + PageBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(PageTopPadding))

        // 1. Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.SystemUpdate, null, tint = BrandCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "AGGIORNAMENTI APP",
                color = BrandCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(
                text = if (state.updateAvailable) "🔴 NUOVA VERSIONE" else "🟢 AGGIORNATO",
                color = if (state.updateAvailable) StatusGreen else TextMuted
            )
        }

        Spacer(Modifier.height(SectionSpacing))

        // 2. Checking state / Error state / Content
        if (state.isCheckingUpdate) {
            GlowCard(borderColor = BrandCyan.copy(0.4f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp, color = BrandCyan, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Verifica aggiornamenti su GitHub in corso...", color = BrandCyan, fontSize = 12.sp)
                }
            }
            return
        }

        val info = state.updateInfo
        if (info == null && state.updateChecked) {
            GlowCard(borderColor = StatusRed.copy(0.35f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.CloudOff, null, tint = TextMuted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Impossibile verificare gli aggiornamenti", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Assicurati di essere connesso al Wi-Fi e riprova.", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    GlowButton(
                        text = "RIPROVA ORA",
                        color = BrandCyan,
                        onClick = onCheckForUpdates,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            return
        }

        if (info == null) return

        // 3. Status Overview Card
        GlowCard(borderColor = if (state.updateAvailable) StatusGreen.copy(0.5f) else DarkBorder) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.updateAvailable) "Aggiornamento Disponibile" else "App Aggiornata",
                        color = if (state.updateAvailable) StatusGreen else TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Versione Attuale: v${FccViewModel.APP_VERSION}", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(
                    if (state.updateAvailable) Icons.Filled.NewReleases else Icons.Filled.CheckCircle,
                    null,
                    tint = if (state.updateAvailable) StatusGreen else TextMuted,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DarkBorder.copy(0.5f), thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            // Channel Selector
            Text("Canale di Aggiornamento", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isStable = state.updateChannel == "stable"
                Surface(
                    onClick = { onSetUpdateChannel("stable") },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isStable) StatusGreen.copy(0.2f) else DarkBorder.copy(0.3f),
                    border = BorderStroke(1.dp, if (isStable) StatusGreen else DarkBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text("🟢 Stabile", color = if (isStable) StatusGreen else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                val isExperimental = state.updateChannel == "experimental"
                Surface(
                    onClick = { onSetUpdateChannel("experimental") },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isExperimental) Amber.copy(0.2f) else DarkBorder.copy(0.3f),
                    border = BorderStroke(1.dp, if (isExperimental) Amber else DarkBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text("🧪 Beta / Pre-release", color = if (isExperimental) Amber else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state.updateAvailable) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = DarkBorder.copy(0.5f), thickness = 1.dp)
                Spacer(Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ultima Versione:", color = TextSecondary, fontSize = 12.sp)
                    Text("v${info.version}", color = StatusGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Data Rilascio:", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        info.publishedAt.split("T").firstOrNull() ?: "",
                        color = TextPrimary, fontSize = 12.sp
                    )
                }
                if (info.apkSize > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dimensione APK:", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            "%.1f MB".format(java.util.Locale.US, info.apkSize / 1048576.0),
                            color = TextPrimary, fontSize = 12.sp
                        )
                    }
                }
            }

            // Changelog / Release Notes
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DarkBorder.copy(0.5f), thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Text("Note di Rilascio (Changelog)", color = BrandCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            Surface(
                color = Color(0xFF0F141C),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    info.changelog.ifEmpty { "Nessuna nota di rilascio fornita." },
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            if (state.updateAvailable) {
                when {
                    state.isDownloadingUpdate -> {
                        ProgressDisplay(
                            progress = state.updateDownloadProgress,
                            label = if (state.updateDownloadProgress <= 0f) "Connessione a GitHub..." else "Download in corso... (${(state.updateDownloadProgress * 100).toInt()}%)",
                            startColor = StatusGreen,
                            endColor = BrandCyan
                        )
                    }
                    state.isUpdateDownloaded -> {
                        GlowButton(
                            text = "INSTALLA AGGIORNAMENTO",
                            color = StatusGreen,
                            filled = true,
                            size = GlowButtonSize.LARGE,
                            onClick = onInstallUpdate,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        GlowButton(
                            text = "SCARICA DI NUOVO",
                            color = BrandCyan,
                            filled = false,
                            onClick = onReDownloadUpdate,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        GlowButton(
                            text = "SCARICA AGGIORNAMENTO v${info.version}",
                            color = StatusGreen,
                            filled = true,
                            size = GlowButtonSize.LARGE,
                            onClick = onDownloadUpdate,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            GlowButton(
                text = "VERIFICA NUOVAMENTE",
                color = BrandCyan,
                filled = false,
                onClick = onCheckForUpdates,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
