package com.ngoloanmanager.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngoloanmanager.ui.theme.*

/** Soft elevated card with subtle depth — restrained 3D, not toy-like */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = CardShape
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    Surface(
        modifier = modifier
            .scale(scale)
            .shadow(elevation, shape, clip = false, ambientColor = EmeraldPrimary.copy(alpha = 0.08f), spotColor = EmeraldPrimary.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.95f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    ),
                    shape
                )
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface.copy(alpha = 0.985f))
                    )
                )
                .padding(16.dp)
                .animateContentSize(),
            content = content
        )
    }
}

/** Premium metric summary card for dashboard */
@Composable
fun MetricCard(
    title: String,
    value: String,
    symbol: String = "৳",
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    GlassCard(modifier = modifier, elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.06f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    value,
                    style = MoneyStyleSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Hero gradient header card */
@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, clip = false, ambientColor = EmeraldPrimary.copy(0.20f), spotColor = EmeraldDeep.copy(0.28f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF063D32), EmeraldDeep, EmeraldPrimary, Color(0xFF22A780))
                )
            )
    ) {
        // Soft glass reflections and depth, kept decorative and non-interactive.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = (-38).dp)
                .size(164.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.015f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.025f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 0.dp)
                .fillMaxWidth(0.52f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent)))
        )
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.06f))), shape)
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@Composable
fun EmptyStateView(message: String, icon: String = "◎") {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusChip(text: String, active: Boolean = true) {
    Surface(
        shape = PillShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PremiumPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "btn")
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .shadow(if (enabled && !loading) 5.dp else 0.dp, ButtonShape, clip = false, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp, focusedElevation = 4.dp, hoveredElevation = 4.dp),
        interactionSource = interaction
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun PremiumOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = ButtonShape,
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun ListRowCard(
    title: String,
    subtitle: String,
    meta: String? = null,
    status: String? = null,
    statusActive: Boolean = true,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null
) {
    GlassCard(onClick = onClick, elevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta != null) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (status != null) {
                Spacer(Modifier.width(8.dp))
                StatusChip(status, statusActive)
            }
        }
    }
}

@Composable
fun QuickActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PillShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
