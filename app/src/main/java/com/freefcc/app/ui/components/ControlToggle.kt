package com.freefcc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            if (checked) 1.5.dp else 1.dp,
            if (checked) Green else CardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDark,
                    checkedTrackColor = Green,
                    checkedBorderColor = Green,
                    uncheckedThumbColor = TextGray,
                    uncheckedTrackColor = BgLight,
                    uncheckedBorderColor = CardBorder
                ),
                modifier = Modifier.scale(0.78f)
            )
            Text(
                text = text,
                color = if (checked) Green else TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}
