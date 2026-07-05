package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.UnitCategory
import com.example.UnitType

@Composable
fun UnitsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentCategory by viewModel.currentUnitCategory.collectAsState()
    val fromUnit by viewModel.fromUnit.collectAsState()
    val toUnit by viewModel.toUnit.collectAsState()
    val fromValue by viewModel.fromUnitValue.collectAsState()
    val toValue by viewModel.toUnitValue.collectAsState()

    var showFromDropdown by remember { mutableStateOf(false) }
    var showToDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Category Selection Chips ---
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("unit_category_list"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(UnitCategory.values()) { category ->
                val selected = category == currentCategory
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectUnitCategory(category) },
                    label = {
                        Text(
                            text = "${category.icon} ${category.displayName}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("chip_${category.name.lowercase()}")
                )
            }
        }

        // --- Conversion Input/Output Box ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // FROM Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showFromDropdown = true }
                                .padding(8.dp)
                                .testTag("btn_select_from_unit")
                        ) {
                            Text(
                                text = "${fromUnit.name} (${fromUnit.suffix})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select from unit",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = showFromDropdown,
                            onDismissRequest = { showFromDropdown = false }
                        ) {
                            val categoryUnits = viewModel.unitCategories[currentCategory] ?: emptyList()
                            categoryUnits.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text("${unit.name} (${unit.suffix})") },
                                    onClick = {
                                        viewModel.setFromUnit(unit)
                                        showFromDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = fromValue.ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                            .testTag("txt_from_value")
                    )
                }

                // Swap Button & Visual Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    IconButton(
                        onClick = { viewModel.swapUnits() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(40.dp)
                            .testTag("btn_swap_units")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap units",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                // TO Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showToDropdown = true }
                                .padding(8.dp)
                                .testTag("btn_select_to_unit")
                        ) {
                            Text(
                                text = "${toUnit.name} (${toUnit.suffix})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select to unit",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        DropdownMenu(
                            expanded = showToDropdown,
                            onDismissRequest = { showToDropdown = false }
                        ) {
                            val categoryUnits = viewModel.unitCategories[currentCategory] ?: emptyList()
                            categoryUnits.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text("${unit.name} (${unit.suffix})") },
                                    onClick = {
                                        viewModel.setToUnit(unit)
                                        showToDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = toValue,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                            .testTag("txt_to_value")
                    )
                }
            }
        }

        // --- Keyboard Entry Area ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2.3f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val keypad = listOf(
                listOf("7", "8", "9", "⌫"),
                listOf("4", "5", "6", "C"),
                listOf("1", "2", "3", "-"),
                listOf("0", ".", "±", "AC")
            )

            for (row in keypad) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (key in row) {
                        val isSpecial = key in listOf("⌫", "C", "AC", "±")
                        val isMinus = key == "-"

                        val bg = when {
                            key == "AC" -> MaterialTheme.colorScheme.errorContainer
                            isSpecial -> MaterialTheme.colorScheme.surfaceVariant
                            isMinus -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }

                        val fg = when {
                            key == "AC" -> MaterialTheme.colorScheme.onErrorContainer
                            isMinus -> MaterialTheme.colorScheme.onPrimaryContainer
                            isSpecial -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        UnitKeypadButton(
                            text = key,
                            onClick = {
                                when (key) {
                                    "AC" -> viewModel.setFromUnitValue("1.0")
                                    "C" -> viewModel.setFromUnitValue("")
                                    "⌫" -> {
                                        val cur = fromValue
                                        if (cur.isNotEmpty()) {
                                            viewModel.setFromUnitValue(cur.dropLast(1))
                                        }
                                    }
                                    "±" -> {
                                        val cur = fromValue
                                        if (cur.startsWith("-")) {
                                            viewModel.setFromUnitValue(cur.drop(1))
                                        } else {
                                            viewModel.setFromUnitValue("-$cur")
                                        }
                                    }
                                    else -> {
                                        viewModel.setFromUnitValue(fromValue + key)
                                    }
                                }
                            },
                            backgroundColor = bg,
                            contentColor = fg,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_unit_key_$key")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnitKeypadButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "UnitButtonScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        contentColor = contentColor,
        modifier = modifier
            .scale(scale)
            .aspectRatio(1.25f)
            .minimumInteractiveComponentSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
        }
    }
}
