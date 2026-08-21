package com.freefcc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefcc.app.ui.theme.BgDark
import com.freefcc.app.ui.theme.BgLight
import com.freefcc.app.ui.theme.CardBorder
import com.freefcc.app.ui.theme.Green
import com.freefcc.app.ui.theme.TextGray
import com.freefcc.app.ui.theme.TextWhite

@Composable
fun AutoModeToggle(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        color = if (checked) Green.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (checked) 1.5.dp else 1.dp,
            if (checked) Green else CardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDark,
                    checkedTrackColor = Green,
                    checkedBorderColor = Green,
                    uncheckedThumbColor = TextGray,
                    uncheckedTrackColor = BgLight,
                    uncheckedBorderColor = CardBorder
                )
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = if (checked) Green else TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
                maxLines = 2
            )
        }
    }
}
