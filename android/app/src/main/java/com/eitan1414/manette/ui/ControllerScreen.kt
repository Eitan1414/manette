package com.eitan1414.manette.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eitan1414.manette.ManetteViewModel
import com.eitan1414.manette.model.ControlKind
import com.eitan1414.manette.model.ControlPlacement
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ManetteScreen(viewModel: ManetteViewModel) {
    var showConnectionDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101218))
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                ip = viewModel.targetIp,
                configured = viewModel.networkConfigured,
                confirmed = viewModel.networkConfirmed,
                channel = viewModel.selectedChannel,
                editMode = viewModel.editMode,
                onConnection = { showConnectionDialog = true },
                onEdit = { viewModel.changeEditMode(!viewModel.editMode) }
            )

            ControllerArea(viewModel, Modifier.weight(1f))
        }

        if (viewModel.editMode) {
            CustomizationPanel(
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
            )
        }
    }

    if (showConnectionDialog) {
        ConnectionDialog(
            initialIp = viewModel.targetIp,
            initialChannel = viewModel.selectedChannel,
            onDismiss = { showConnectionDialog = false },
            onConnect = { ip, channel ->
                viewModel.connect(ip, channel)
                showConnectionDialog = false
            }
        )
    }
}

@Composable
private fun TopBar(
    ip: String,
    configured: Boolean,
    confirmed: Boolean,
    channel: Int,
    editMode: Boolean,
    onConnection: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MANETTE", fontWeight = FontWeight.Black, fontSize = 19.sp)
            Spacer(Modifier.width(16.dp))

            val statusText = when {
                confirmed -> "● Wii U reçoit · canal $channel"
                configured -> "● En attente Wii U · canal $channel"
                else -> "○ Wii U non configurée"
            }
            val statusColor = when {
                confirmed -> Color(0xFF8BE39B)
                configured -> Color(0xFFFFC267)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(statusText, color = statusColor, fontSize = 13.sp)
            if (configured && ip.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(ip, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onConnection) { Text("CONNEXION") }
            Button(
                onClick = onEdit,
                colors = if (editMode) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                else ButtonDefaults.buttonColors()
            ) {
                Text(if (editMode) "TERMINER" else "PERSONNALISER")
            }
        }
    }
}

@Composable
private fun ControllerArea(viewModel: ManetteViewModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        viewModel.layout.forEach { placement ->
            val selected = viewModel.selectedControlId == placement.id
            when (placement.kind) {
                ControlKind.BUTTON -> ControllerButtonControl(
                    placement = placement,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    editMode = viewModel.editMode,
                    selected = selected,
                    onSelect = { viewModel.selectControl(placement.id) },
                    onMove = { dx, dy -> viewModel.moveControl(placement.id, dx, dy) },
                    onMoveFinished = viewModel::persistLayout,
                    onPressed = { pressed -> placement.button?.let { viewModel.setButton(it, pressed) } }
                )

                ControlKind.STICK_LEFT, ControlKind.STICK_RIGHT -> AnalogStickControl(
                    placement = placement,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    editMode = viewModel.editMode,
                    selected = selected,
                    onSelect = { viewModel.selectControl(placement.id) },
                    onMove = { dx, dy -> viewModel.moveControl(placement.id, dx, dy) },
                    onMoveFinished = viewModel::persistLayout,
                    onStick = { x, y -> viewModel.setStick(placement.kind == ControlKind.STICK_LEFT, x, y) }
                )
            }
        }
    }
}

@Composable
private fun ControllerButtonControl(
    placement: ControlPlacement,
    widthPx: Float,
    heightPx: Float,
    editMode: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onMoveFinished: () -> Unit,
    onPressed: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val sizePx = with(density) { placement.sizeDp.dp.toPx() }
    val positionModifier = Modifier.offset {
        IntOffset(
            (placement.x * widthPx - sizePx / 2f).roundToInt(),
            (placement.y * heightPx - sizePx / 2f).roundToInt()
        )
    }

    val gestureModifier = if (editMode) {
        Modifier.pointerInput(placement.id, widthPx, heightPx) {
            detectDragGestures(
                onDragStart = { onSelect() },
                onDragEnd = onMoveFinished,
                onDragCancel = onMoveFinished
            ) { change, dragAmount ->
                change.consume()
                onMove(dragAmount.x / widthPx, dragAmount.y / heightPx)
            }
        }
    } else {
        Modifier.pointerInput(placement.button) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                onPressed(true)
                val pointerId: PointerId = down.id
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break
                    change.consume()
                } while (true)
                onPressed(false)
            }
        }
    }

    Box(
        modifier = positionModifier
            .size(placement.sizeDp.dp)
            .then(gestureModifier)
            .background(
                if (selected) Color(0xFF4F72FF) else Color(0xFF252A34),
                CircleShape
            )
            .border(
                width = if (editMode) 2.dp else 1.dp,
                color = when {
                    selected -> Color.White
                    editMode -> Color(0xFF7A8498)
                    else -> Color(0xFF404859)
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = placement.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = (placement.sizeDp / 3.5f).coerceIn(11f, 24f).sp
        )
    }
}

@Composable
private fun AnalogStickControl(
    placement: ControlPlacement,
    widthPx: Float,
    heightPx: Float,
    editMode: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onMoveFinished: () -> Unit,
    onStick: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val sizePx = with(density) { placement.sizeDp.dp.toPx() }
    var knob by remember(placement.id) { mutableStateOf(Offset.Zero) }

    val gestureModifier = if (editMode) {
        Modifier.pointerInput(placement.id, widthPx, heightPx) {
            detectDragGestures(
                onDragStart = { onSelect() },
                onDragEnd = onMoveFinished,
                onDragCancel = onMoveFinished
            ) { change, dragAmount ->
                change.consume()
                onMove(dragAmount.x / widthPx, dragAmount.y / heightPx)
            }
        }
    } else {
        Modifier.pointerInput(placement.id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id

                fun update(position: Offset) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = minOf(size.width, size.height).toFloat() / 2f
                    var dx = (position.x - center.x) / radius
                    var dy = (position.y - center.y) / radius
                    val magnitude = hypot(dx, dy)
                    if (magnitude > 1f) {
                        dx /= magnitude
                        dy /= magnitude
                    }
                    knob = Offset(dx, dy)
                    onStick(dx, -dy)
                }

                update(down.position)
                down.consume()
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break
                    update(change.position)
                    change.consume()
                } while (true)

                knob = Offset.Zero
                onStick(0f, 0f)
            }
        }
    }

    Canvas(
        modifier = Modifier
            .offset {
                IntOffset(
                    (placement.x * widthPx - sizePx / 2f).roundToInt(),
                    (placement.y * heightPx - sizePx / 2f).roundToInt()
                )
            }
            .size(placement.sizeDp.dp)
            .then(gestureModifier)
            .then(
                if (editMode) Modifier.border(
                    2.dp,
                    if (selected) Color.White else Color(0xFF7A8498),
                    CircleShape
                ) else Modifier
            )
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Color(0xFF222833), radius = radius)
        drawCircle(Color(0xFF485164), radius = radius * 0.72f)
        val knobCenter = center + Offset(knob.x * radius * 0.48f, knob.y * radius * 0.48f)
        drawCircle(
            if (selected) Color(0xFF6E8AFF) else Color(0xFF9AA4B8),
            radius = radius * 0.34f,
            center = knobCenter
        )
    }
}

@Composable
private fun CustomizationPanel(viewModel: ManetteViewModel, modifier: Modifier = Modifier) {
    val selected = viewModel.layout.firstOrNull { it.id == viewModel.selectedControlId }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected == null) {
                Text("Touchez puis faites glisser une commande pour la déplacer.")
            } else {
                Text("${selected.label}  •  Taille", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = selected.sizeDp,
                    onValueChange = { viewModel.resizeControl(selected.id, it) },
                    onValueChangeFinished = viewModel::persistLayout,
                    valueRange = 38f..190f,
                    modifier = Modifier.width(210.dp)
                )
                Text("${selected.sizeDp.roundToInt()} dp", fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = viewModel::resetLayout) { Text("RÉINITIALISER") }
        }
    }
}

@Composable
private fun ConnectionDialog(
    initialIp: String,
    initialChannel: Int,
    onDismiss: () -> Unit,
    onConnect: (String, Int) -> Unit
) {
    var ip by remember(initialIp) { mutableStateOf(initialIp) }
    var channel by remember(initialChannel) { mutableStateOf(initialChannel.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connexion à la Wii U") },
        text = {
            Column {
                Text("La tablette et la Wii U doivent être sur le même réseau Wi‑Fi.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Adresse IP de la Wii U") },
                    placeholder = { Text("192.168.1.42") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Canal Pro Controller : ${channel.roundToInt()}", fontWeight = FontWeight.Bold)
                Slider(
                    value = channel,
                    onValueChange = { channel = it },
                    valueRange = 0f..6f,
                    steps = 5
                )
                Text(
                    "Si la Wii U répond mais qu'un jeu ne détecte pas la manette, essayez un autre canal.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text("UDP : 4405 · protocole v2", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onConnect(ip, channel.roundToInt()) }) { Text("CONNECTER") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ANNULER") }
        }
    )
}
