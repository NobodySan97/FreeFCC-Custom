package com.freefcc.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefcc.app.AppState
import com.freefcc.app.ui.theme.Amber
import com.freefcc.app.ui.theme.BrandCyan
import com.freefcc.app.ui.theme.DarkBackgroundMid
import com.freefcc.app.ui.theme.DarkBorder
import com.freefcc.app.ui.theme.DarkSurfaceElevated
import com.freefcc.app.ui.theme.FreeFccTheme
import com.freefcc.app.ui.theme.StatusGreen
import com.freefcc.app.ui.theme.TextMuted
import com.freefcc.app.ui.theme.TextPrimary
import com.freefcc.app.ui.theme.TextSecondary

/**
 * Milestone M2: Active Drone Serial Card component.
 * Displays the active drone serial number (`manualSerial.ifEmpty { aircraftSerial }`),
 * provides a status badge ("OVERRIDE MANUALE" vs "AUTOMATICO" vs "NON RILEVATO"),
 * manual SN input/override controls, probe refresh button, and clipboard copy.
 */
@Composable
fun DroneSerialCard(
    aircraftSerial: String,
    manualSerial: String,
    modifier: Modifier = Modifier,
    isProbingSerial: Boolean = false,
    isHardwareBusy: Boolean = false,
    onProbeSerial: () -> Unit = {},
    onSetManualSerial: (String) -> Unit = {},
    onCopySerial: ((String) -> Unit)? = null
) {
    val activeSerial = manualSerial.ifEmpty { aircraftSerial }
    val isManual = manualSerial.isNotEmpty()
    val hasSerial = activeSerial.isNotEmpty()
    val clipboardManager = LocalClipboardManager.current

    var isEditingManualSerial by remember { mutableStateOf(false) }
    var inputText by remember(manualSerial) { mutableStateOf(manualSerial) }

    val cardBorderColor = when {
        isManual -> Amber.copy(alpha = 0.5f)
        hasSerial -> StatusGreen.copy(alpha = 0.4f)
        else -> DarkBorder
    }

    GlowCard(
        borderColor = cardBorderColor,
        modifier = modifier
    ) {
        // --- Header Row: Icon + Label + Status Badge ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = if (isManual) Amber else if (hasSerial) StatusGreen else BrandCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "SERIE DRONE (S/N)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            when {
                isManual -> StatusBadge(text = "OVERRIDE MANUALE", color = Amber)
                hasSerial -> StatusBadge(text = "AUTOMATICO", color = StatusGreen)
                else -> StatusBadge(text = "NON RILEVATO", color = TextMuted)
            }
        }

        Spacer(Modifier.height(10.dp))

        // --- Active Serial Display Box ---
        Surface(
            color = DarkBackgroundMid,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isManual) "S/N Manuale Impostato" else "S/N Rilevato",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = activeSerial.ifEmpty { "Nessun S/N disponibile" },
                        color = if (hasSerial) TextPrimary else TextMuted,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSerial) {
                        IconButton(
                            onClick = {
                                if (onCopySerial != null) {
                                    onCopySerial(activeSerial)
                                } else {
                                    clipboardManager.setText(AnnotatedString(activeSerial))
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copia S/N",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { isEditingManualSerial = !isEditingManualSerial },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Modifica S/N Manuale",
                            tint = if (isManual) Amber else BrandCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- Expandable Manual Input Panel ---
        AnimatedVisibility(
            visible = isEditingManualSerial,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(DarkSurfaceElevated.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Imposta Serial Number Manuale (Override)",
                    color = Amber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it.uppercase() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("es. 1581F4...", color = TextMuted, fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkBackgroundMid,
                            unfocusedContainerColor = DarkBackgroundMid
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onSetManualSerial(inputText.trim())
                            isEditingManualSerial = false
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            onSetManualSerial(inputText.trim())
                            isEditingManualSerial = false
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Amber, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Salva S/N",
                            tint = DarkBackgroundMid
                        )
                    }
                }

                if (isManual) {
                    Spacer(Modifier.height(6.dp))
                    GlowButton(
                        text = "CANCELLA OVERRIDE (TORNA AD AUTO)",
                        color = Amber,
                        size = GlowButtonSize.COMPACT,
                        filled = false,
                        onClick = {
                            inputText = ""
                            onSetManualSerial("")
                            isEditingManualSerial = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // --- Action Row: Probe Refresh Button ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlowButton(
                text = "RILEVA S/N",
                color = BrandCyan,
                size = GlowButtonSize.COMPACT,
                filled = false,
                isLoading = isProbingSerial,
                enabled = !isHardwareBusy && !isProbingSerial,
                icon = Icons.Outlined.Refresh,
                onClick = onProbeSerial,
                modifier = Modifier.width(130.dp)
            )
        }
    }
}

/**
 * Convenient overload accepting [AppState] directly.
 */
@Composable
fun DroneSerialCard(
    state: AppState,
    modifier: Modifier = Modifier,
    onProbeSerial: () -> Unit = {},
    onSetManualSerial: (String) -> Unit = {},
    onCopySerial: ((String) -> Unit)? = null
) {
    DroneSerialCard(
        aircraftSerial = state.aircraftSerial,
        manualSerial = state.manualSerial,
        modifier = modifier,
        isProbingSerial = state.isProbingSerial,
        isHardwareBusy = state.isHardwareBusy,
        onProbeSerial = onProbeSerial,
        onSetManualSerial = onSetManualSerial,
        onCopySerial = onCopySerial
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun DroneSerialCardAutoPreview() {
    FreeFccTheme {
        DroneSerialCard(
            aircraftSerial = "1581F4X123456789",
            manualSerial = ""
        )
    }
}

@Composable
fun DroneSerialCardManualPreview() {
    FreeFccTheme {
        DroneSerialCard(
            aircraftSerial = "1581F4X123456789",
            manualSerial = "1581F4OVERRIDE99"
        )
    }
}

@Composable
fun DroneSerialCardEmptyPreview() {
    FreeFccTheme {
        DroneSerialCard(
            aircraftSerial = "",
            manualSerial = ""
        )
    }
}
