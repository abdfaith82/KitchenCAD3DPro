package com.engineering.kitchencad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KitchenCADScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenCADScreen() {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var activeTool by remember { mutableStateOf("Wall") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KitchenCAD 3D Pro - CAD Engine") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { activeTool = "Wall" }) { Text("Wall") }
                    Button(onClick = { activeTool = "Cabinet" }) { Text("Cabinet") }
                    Button(onClick = { activeTool = "Appliance" }) { Text("Appliance") }
                    Button(onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }) { Text("Reset") }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset += pan
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridStep = 50f * scale
                val startX = offset.x % gridStep
                val startY = offset.y % gridStep

                // Draw 2D Grid
                var x = startX
                while (x < size.width) {
                    drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += gridStep
                }
                var y = startY
                while (y < size.height) {
                    drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += gridStep
                }

                // Draw Sample CAD Kitchen Layout
                val cx = size.width / 2f + offset.x
                val cy = size.height / 2f + offset.y

                // Outer Walls
                drawRect(
                    color = Color.DarkGray,
                    topLeft = Offset(cx - 300f * scale, cy - 200f * scale),
                    size = Size(600f * scale, 400f * scale),
                    style = Stroke(width = 4f * scale)
                )

                // Base Cabinets
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(cx - 280f * scale, cy + 120f * scale),
                    size = Size(250f * scale, 60f * scale)
                )

                // Kitchen Island / Counter
                drawRect(
                    color = Color.Magenta,
                    topLeft = Offset(cx - 80f * scale, cy - 60f * scale),
                    size = Size(160f * scale, 100f * scale)
                )
            }

            Text(
                text = "Tool: $activeTool | Zoom: ${String.format("%.2f", scale)}x",
                modifier = Modifier
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
