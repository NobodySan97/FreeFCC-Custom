package com.freefcc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freefcc.app.ui.theme.BrandCyan
import com.freefcc.app.ui.theme.Red
import com.freefcc.app.ui.theme.TextGray
import com.freefcc.app.ui.theme.TextWhite

@Composable
fun SideNavigationRail(
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    updateAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple("FCC", Icons.Filled.Wifi, 0),
        Triple("Modem", Icons.Filled.SettingsInputAntenna, 1),
        Triple("Diag", Icons.Outlined.Info, 2),
        Triple("Update", Icons.Outlined.SystemUpdate, 3)
    )

    NavigationRail(
        containerColor = Color.White.copy(alpha = 0.03f),
        contentColor = TextWhite,
        modifier = modifier.width(90.dp).background(Color.Transparent)
    ) {
        Spacer(Modifier.weight(1f))
        tabs.forEach { (label, icon, index) ->
            val selected = currentPage == index
            NavigationRailItem(
                selected = selected,
                onClick = { onPageSelected(index) },
                icon = {
                    if (index == 3 && updateAvailable) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = Red,
                                    contentColor = TextWhite
                                )
                            }
                        ) {
                            Icon(icon, contentDescription = label)
                        }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = BrandCyan,
                    selectedTextColor = BrandCyan,
                    indicatorColor = BrandCyan.copy(alpha = 0.18f),
                    unselectedIconColor = TextGray,
                    unselectedTextColor = TextGray
                )
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}
