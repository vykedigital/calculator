package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CalculatorScreen
import com.example.ui.ConverterScreen
import com.example.ui.UnitsScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                AppViewportContainer(viewModel = viewModel, isDarkTheme = isDarkTheme)
            }
        }
    }
}

@Composable
fun AppViewportContainer(
    viewModel: MainViewModel,
    isDarkTheme: Boolean
) {
    val currentTab by viewModel.currentTab.collectAsState()

    // Outer background to simulate a dark desktop environment if viewport is wide
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF1F5F9)),
        contentAlignment = Alignment.Center
    ) {
        val isExpanded = maxWidth > 600.dp
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

        // Responsive Phone Viewport Mockup
        Surface(
            modifier = if (isExpanded) {
                Modifier
                    .width(412.dp)
                    .fillMaxHeight()
                    .padding(vertical = 32.dp)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(36.dp)
                    )
            } else {
                Modifier.fillMaxSize()
            },
            shape = if (isExpanded) RoundedCornerShape(36.dp) else RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 1.dp
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    CustomTopAppBar(
                        title = "Calc & Convert",
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { viewModel.toggleTheme() },
                        modifier = Modifier.padding(top = if (isExpanded) 8.dp else statusBarPadding.calculateTopPadding())
                    )
                },
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BannerAdView()
                        CustomBottomNavBar(
                            currentTab = currentTab,
                            onTabSelect = { viewModel.selectTab(it) }
                        )
                    }
                }
            ) { innerPadding ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Smooth crossfade tab transitions
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "TabTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        when (tab) {
                            AppTab.Calculator -> CalculatorScreen(viewModel = viewModel)
                            AppTab.Convert -> ConverterScreen(viewModel = viewModel)
                            AppTab.Units -> UnitsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomTopAppBar(
    title: String,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.testTag("app_title")
        )

        IconButton(
            onClick = onThemeToggle,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .size(40.dp)
                .testTag("btn_theme_toggle")
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = if (isDarkTheme) "Switch to Light Theme" else "Switch to Dark Theme",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CustomBottomNavBar(
    currentTab: AppTab,
    onTabSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.navigationBars,
        modifier = modifier.testTag("bottom_nav_bar")
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.Calculator,
            onClick = { onTabSelect(AppTab.Calculator) },
            icon = {
                Icon(
                    imageVector = if (currentTab == AppTab.Calculator) Icons.Default.Calculate else Icons.Outlined.Calculate,
                    contentDescription = "Calculator Tab"
                )
            },
            label = {
                Text(
                    text = "Calculator",
                    fontWeight = if (currentTab == AppTab.Calculator) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier.testTag("tab_calculator")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.Convert,
            onClick = { onTabSelect(AppTab.Convert) },
            icon = {
                Icon(
                    imageVector = if (currentTab == AppTab.Convert) Icons.Default.CurrencyExchange else Icons.Outlined.CurrencyExchange,
                    contentDescription = "Converter Tab"
                )
            },
            label = {
                Text(
                    text = "Convert",
                    fontWeight = if (currentTab == AppTab.Convert) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier.testTag("tab_convert")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.Units,
            onClick = { onTabSelect(AppTab.Units) },
            icon = {
                Icon(
                    imageVector = if (currentTab == AppTab.Units) Icons.Default.Straighten else Icons.Outlined.Straighten,
                    contentDescription = "Units Tab"
                )
            },
            label = {
                Text(
                    text = "Units",
                    fontWeight = if (currentTab == AppTab.Units) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier.testTag("tab_units")
        )
    }
}

@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                com.google.android.gms.ads.AdView(context).apply {
                    setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                    adUnitId = "ca-app-pub-3940256099942544/6300978111"
                    loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
                }
            }
        )
    }
}

