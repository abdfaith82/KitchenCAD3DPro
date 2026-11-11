package com.engineering.kitchencad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

enum class UnitType(
    val label: String,
    val defaultW: Float,
    val defaultD: Float,
    val defaultH: Float,
    val baseZ: Float,
    val baseColor: Long
) {
    WALL("جدار معماري", 300f, 20f, 280f, 0f, 0xFF78909C),
    BASE_CABINET("خزانة أرضية", 80f, 60f, 85f, 0f, 0xFF8D6E63),
    WALL_CABINET("خزانة علوية", 80f, 35f, 75f, 145f, 0xFFA1887F),
    TALL_FRIDGE("وحدة ثلاجة/فرن", 75f, 70f, 220f, 0f, 0xFF546E7A),
    ISLAND("جزيرة مطبخ", 180f, 90f, 90f, 0f, 0xFF4E342E),
    SINK_UNIT("وحدة حوض غسيل", 100f, 60f, 85f, 0f, 0xFF00838F)
}

data class KitchenElement(
    val id: Int,
    val type: UnitType,
    val x: Float,      // سم
    val y: Float,      // سم
    val width: Float,  // العرض (سم)
    val depth: Float,  // العمق (سم)
    val height: Float, // الارتفاع (سم)
    val elevationZ: Float,
    val rotationDeg: Float = 0f
)

data class CadState(
    val elements: List<KitchenElement> = listOf(
        KitchenElement(1, UnitType.WALL, 0f, 0f, 400f, 20f, 280f, 0f),
        KitchenElement(2, UnitType.WALL, 0f, 20f, 20f, 320f, 280f, 0f),
        KitchenElement(3, UnitType.TALL_FRIDGE, 20f, 20f, 80f, 70f, 220f, 0f),
        KitchenElement(4, UnitType.BASE_CABINET, 100f, 20f, 100f, 60f, 85f, 0f),
        KitchenElement(5, UnitType.SINK_UNIT, 200f, 20f, 100f, 60f, 85f, 0f),
        KitchenElement(6, UnitType.BASE_CABINET, 300f, 20f, 100f, 60f, 85f, 0f),
        KitchenElement(7, UnitType.WALL_CABINET, 100f, 20f, 100f, 35f, 75f, 145f),
        KitchenElement(8, UnitType.WALL_CABINET, 300f, 20f, 100f, 35f, 75f, 145f),
        KitchenElement(9, UnitType.ISLAND, 130f, 160f, 180f, 90f, 90f, 0f)
    ),
    val selectedId: Int? = 9,
    val is3DMode: Boolean = false,
    val snapGridCm: Float = 10f,
    val zoom: Float = 1.6f,
    val panOffset: Offset = Offset(140f, 180f),
    val yawDeg: Float = 45f,
    val pitchDeg: Float = 30f
)

class KitchenCadViewModel : ViewModel() {
    private val _state = MutableStateFlow(CadState())
    val state: StateFlow<CadState> = _state.asStateFlow()
    private var nextId = 10

    private fun snap(v: Float, grid: Float): Float = (v / grid).roundToInt() * grid

    fun toggleViewMode() = _state.update { it.copy(is3DMode = !it.is3DMode) }

    fun addElement(type: UnitType) {
        _state.update { s ->
            val newEl = KitchenElement(
                id = nextId++,
                type = type,
                x = 120f,
                y = 120f,
                width = type.defaultW,
                depth = type.defaultD,
                height = type.defaultH,
                elevationZ = type.baseZ
            )
            s.copy(elements = s.elements + newEl, selectedId = newEl.id)
        }
    }

    fun selectAt(worldCmX: Float, worldCmY: Float) {
        _state.update { s ->
            // الأعلى ارتفاعاً هو الأقرب للتحديد (يطابق ترتيب الرسم)
            val hit = s.elements.sortedBy { it.elevationZ }.lastOrNull { el ->
                worldCmX >= el.x && worldCmX <= (el.x + el.width) &&
                    worldCmY >= el.y && worldCmY <= (el.y + el.depth)
            }
            s.copy(selectedId = hit?.id)
        }
    }

    fun moveSelected(dxCm: Float, dyCm: Float) {
        _state.update { s ->
            val id = s.selectedId ?: return@update s
            val updated = s.elements.map { el ->
                if (el.id == id) {
                    el.copy(
                        x = snap((el.x + dxCm).coerceAtLeast(0f), s.snapGridCm),
                        y = snap((el.y + dyCm).coerceAtLeast(0f), s.snapGridCm)
                    )
                } else el
            }
            s.copy(elements = updated)
        }
    }

    fun rotateSelected() {
        _state.update { s ->
            val id = s.selectedId ?: return@update s
            val updated = s.elements.map { el ->
                if (el.id == id) {
                    el.copy(
                        width = el.depth,
                        depth = el.width,
                        rotationDeg = (el.rotationDeg + 90f) % 360f
                    )
                } else el
            }
            s.copy(elements = updated)
        }
    }

    fun resizeSelected(dWidthCm: Float, dDepthCm: Float) {
        _state.update { s ->
            val id = s.selectedId ?: return@update s
            val updated = s.elements.map { el ->
                if (el.id == id) {
                    el.copy(
                        width = (el.width + dWidthCm).coerceIn(20f, 600f),
                        depth = (el.depth + dDepthCm).coerceIn(20f, 600f)
                    )
                } else el
            }
            s.copy(elements = updated)
        }
    }

    fun deleteSelected() {
        _state.update { s ->
            val id = s.selectedId ?: return@update s
            s.copy(elements = s.elements.filterNot { it.id == id }, selectedId = null)
        }
    }

    fun onPanZoom(pan: Offset, zoomChange: Float) {
        _state.update { s ->
            if (s.is3DMode) {
                s.copy(
                    yawDeg = (s.yawDeg + pan.x * 0.35f) % 360f,
                    pitchDeg = (s.pitchDeg - pan.y * 0.25f).coerceIn(15f, 75f),
                    zoom = (s.zoom * zoomChange).coerceIn(0.6f, 4.0f)
                )
            } else {
                s.copy(
                    panOffset = s.panOffset + pan,
                    zoom = (s.zoom * zoomChange).coerceIn(0.5f, 4.5f)
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                KitchenCadApp()
            }
        }
    }
}

private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

@Composable
fun KitchenCadApp(vm: KitchenCadViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val latestState by rememberUpdatedState(state)
    val selectedElement = state.elements.find { it.id == state.selectedId }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF12161F))) {
        // الشريط العلوي
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E2433))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "KITCHEN CAD 3D PRO",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                // مساحة الوحدات الأرضية فقط (دون الجدران والخزائن العلوية لتجنب التكرار)
                val floorAreaM2 = state.elements
                    .filter { it.type != UnitType.WALL && it.type != UnitType.WALL_CABINET }
                    .sumOf { (it.width * it.depth).toDouble() } / 10000.0
                Text(
                    "الوحدات: ${state.elements.size} | مساحة الوحدات الأرضية: ${fmt(floorAreaM2)} م²",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
            Button(
                onClick = { vm.toggleViewMode() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.is3DMode) Color(0xFFFF6D00) else Color(0xFF00B0FF)
                )
            ) {
                Text(
                    if (state.is3DMode) "مسقط 2D CAD" else "معاينة 3D ISO",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // شريط إضافة الوحدات
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181D29))
                .horizontalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UnitType.values().forEach { type ->
                Box(
                    modifier = Modifier
                        .border(1.dp, Color(type.baseColor), RoundedCornerShape(6.dp))
                        .background(Color(0xFF232A3B), RoundedCornerShape(6.dp))
                        .clickable { vm.addElement(type) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+ ${type.label}", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // لوحة الرسم 2D / 3D
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            vm.onPanZoom(pan, zoom)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val s = latestState
                            if (!s.is3DMode) {
                                val worldX = (tap.x - s.panOffset.x) / s.zoom
                                val worldY = (tap.y - s.panOffset.y) / s.zoom
                                vm.selectAt(worldX, worldY)
                            }
                        }
                    }
            ) {
                if (!state.is3DMode) {
                    draw2DCadPlan(state)
                } else {
                    draw3DIsometricScene(state)
                }
            }

            Text(
                text = if (state.is3DMode) {
                    "وضع 3D: اسحب لتدوير الكاميرا | قرّب للتكبير"
                } else {
                    "وضع 2D CAD (التكبير: ${fmt(state.zoom.toDouble())}x) | الشبكة: 20سم"
                },
                color = Color(0xFF90A4AE),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                    .padding(6.dp)
            )
        }

        // لوحة التحكم بالوحدة المحددة
        if (selectedElement != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2433))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "العنصر المحدد: ${selectedElement.type.label} | الأبعاد: ${selectedElement.width.toInt()}×${selectedElement.depth.toInt()}×${selectedElement.height.toInt()} سم | الموقع: (${selectedElement.x.toInt()}, ${selectedElement.y.toInt()})",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallCtrlBtn("◀ يسار") { vm.moveSelected(-10f, 0f) }
                    SmallCtrlBtn("يمين ▶") { vm.moveSelected(10f, 0f) }
                    SmallCtrlBtn("▲ أعلى") { vm.moveSelected(0f, -10f) }
                    SmallCtrlBtn("أسفل ▼") { vm.moveSelected(0f, 10f) }
                    SmallCtrlBtn("تدوير 90°") { vm.rotateSelected() }
                    SmallCtrlBtn("عرض +10سم") { vm.resizeSelected(10f, 0f) }
                    SmallCtrlBtn("عرض -10سم") { vm.resizeSelected(-10f, 0f) }
                    SmallCtrlBtn("عمق +10سم") { vm.resizeSelected(0f, 10f) }
                    SmallCtrlBtn("عمق -10سم") { vm.resizeSelected(0f, -10f) }
                    SmallCtrlBtn("حذف", Color(0xFFC62828)) { vm.deleteSelected() }
                }
            }
        }
    }
}

@Composable
fun SmallCtrlBtn(label: String, bg: Color = Color(0xFF2E384D), onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------- الرسم ثنائي الأبعاد ----------
private fun DrawScope.draw2DCadPlan(state: CadState) {
    val z = state.zoom
    val ox = state.panOffset.x
    val oy = state.panOffset.y

    // شبكة: خط فرعي كل 20 سم ورئيسي كل 100 سم، محاذية لنقطة الأصل
    val step = 20f * z
    val majorEvery = 5
    val firstI = floor(-ox / step).toInt()
    val lastI = floor((size.width - ox) / step).toInt() + 1
    for (i in firstI..lastI) {
        val x = ox + i * step
        val major = Math.floorMod(i, majorEvery) == 0
        drawLine(
            color = if (major) Color(0xFF263238) else Color(0xFF1A2228),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (major) 1.8f else 0.8f
        )
    }
    val firstJ = floor(-oy / step).toInt()
    val lastJ = floor((size.height - oy) / step).toInt() + 1
    for (j in firstJ..lastJ) {
        val y = oy + j * step
        val major = Math.floorMod(j, majorEvery) == 0
        drawLine(
            color = if (major) Color(0xFF263238) else Color(0xFF1A2228),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (major) 1.8f else 0.8f
        )
    }

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = (10f * z).coerceIn(18f, 32f)
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    state.elements.sortedBy { it.elevationZ }.forEach { el ->
        val sx = ox + el.x * z
        val sy = oy + el.y * z
        val sw = el.width * z
        val sd = el.depth * z
        val isSelected = el.id == state.selectedId

        drawRect(
            color = Color(el.type.baseColor).copy(
                alpha = if (el.type == UnitType.WALL_CABINET) 0.55f else 0.88f
            ),
            topLeft = Offset(sx, sy),
            size = Size(sw, sd)
        )
        drawRect(
            color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.7f),
            topLeft = Offset(sx, sy),
            size = Size(sw, sd),
            style = Stroke(width = if (isSelected) 3.5f else 1.5f)
        )

        if (el.type == UnitType.SINK_UNIT) {
            drawOval(
                color = Color(0xFFB2EBF2),
                topLeft = Offset(sx + sw * 0.15f, sy + sd * 0.2f),
                size = Size(sw * 0.32f, sd * 0.6f),
                style = Stroke(width = 2f)
            )
            drawOval(
                color = Color(0xFFB2EBF2),
                topLeft = Offset(sx + sw * 0.53f, sy + sd * 0.2f),
                size = Size(sw * 0.32f, sd * 0.6f),
                style = Stroke(width = 2f)
            )
        }

        drawContext.canvas.nativeCanvas.drawText(
            "${el.width.toInt()}×${el.depth.toInt()}cm",
            sx + sw / 2f,
            sy + sd / 2f + textPaint.textSize / 3f,
            textPaint
        )
    }
}

// ---------- الإسقاط ثلاثي الأبعاد ----------
private fun shade(c: Color, f: Float): Color =
    Color(
        red = (c.red * f).coerceIn(0f, 1f),
        green = (c.green * f).coerceIn(0f, 1f),
        blue = (c.blue * f).coerceIn(0f, 1f),
        alpha = 1f
    )

private class Face(val pts: List<Offset>, val depth: Float, val fill: Color)

private fun DrawScope.draw3DIsometricScene(state: CadState) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.62f
    val scale = state.zoom * 1.15f
    val yaw = (state.yawDeg * PI / 180.0).toFloat()
    val pitch = (state.pitchDeg * PI / 180.0).toFloat()
    val cosY = cos(yaw)
    val sinY = sin(yaw)
    val cosP = cos(pitch)
    val sinP = sin(pitch)

    // عمق الكاميرا: القيم الأكبر أقرب للمشاهد
    fun depthOf(xCm: Float, yCm: Float): Float {
        val sx = xCm - 200f
        val sy = yCm - 150f
        return sx * sinY + sy * cosY
    }

    fun project3D(xCm: Float, yCm: Float, zCm: Float): Offset {
        val sx = xCm - 200f
        val sy = yCm - 150f
        val rx = sx * cosY - sy * sinY
        val ry = sx * sinY + sy * cosY
        return Offset(cx + rx * scale, cy + (ry * sinP - zCm * cosP) * scale)
    }

    // أرضية 400×350 سم
    val gridColor = Color(0xFF263238)
    for (gx in 0..400 step 50) {
        drawLine(gridColor, project3D(gx.toFloat(), 0f, 0f), project3D(gx.toFloat(), 350f, 0f), 1.5f)
    }
    for (gy in 0..350 step 50) {
        drawLine(gridColor, project3D(0f, gy.toFloat(), 0f), project3D(400f, gy.toFloat(), 0f), 1.5f)
    }

    // الأبعد أولاً (خوارزمية الرسام)
    val sorted = state.elements.sortedBy { el ->
        depthOf(el.x + el.width / 2f, el.y + el.depth / 2f) + el.elevationZ * 0.2f
    }

    sorted.forEach { el ->
        val x0 = el.x
        val x1 = el.x + el.width
        val y0 = el.y
        val y1 = el.y + el.depth
        val z0 = el.elevationZ
        val z1 = el.elevationZ + el.height

        val base = Color(el.type.baseColor)
        val isSelected = el.id == state.selectedId
        val strokeCol = if (isSelected) Color(0xFF00E5FF) else Color(0xFF10141D)
        val strokeW = if (isSelected) 3f else 1.3f

        val sideFaces = listOf(
            // وجه y0
            Face(
                listOf(project3D(x0, y0, z0), project3D(x1, y0, z0), project3D(x1, y0, z1), project3D(x0, y0, z1)),
                depthOf((x0 + x1) / 2f, y0), shade(base, 0.72f)
            ),
            // وجه x0
            Face(
                listOf(project3D(x0, y0, z0), project3D(x0, y1, z0), project3D(x0, y1, z1), project3D(x0, y0, z1)),
                depthOf(x0, (y0 + y1) / 2f), shade(base, 0.8f)
            ),
            // وجه x1
            Face(
                listOf(project3D(x1, y0, z0), project3D(x1, y1, z0), project3D(x1, y1, z1), project3D(x1, y0, z1)),
                depthOf(x1, (y0 + y1) / 2f), shade(base, 0.9f)
            ),
            // وجه y1
            Face(
                listOf(project3D(x0, y1, z0), project3D(x1, y1, z0), project3D(x1, y1, z1), project3D(x0, y1, z1)),
                depthOf((x0 + x1) / 2f, y1), shade(base, 1.0f)
            )
        ).sortedBy { it.depth }

        val topColor =
            if (el.type == UnitType.BASE_CABINET || el.type == UnitType.ISLAND || el.type == UnitType.SINK_UNIT) {
                Color(0xFFECEFF1)
            } else {
                shade(base, 1.1f)
            }
        val top = Face(
            listOf(project3D(x0, y0, z1), project3D(x1, y0, z1), project3D(x1, y1, z1), project3D(x0, y1, z1)),
            0f, topColor
        )

        (sideFaces + top).forEach { face ->
            val path = Path().apply {
                moveTo(face.pts[0].x, face.pts[0].y)
                lineTo(face.pts[1].x, face.pts[1].y)
                lineTo(face.pts[2].x, face.pts[2].y)
                lineTo(face.pts[3].x, face.pts[3].y)
                close()
            }
            drawPath(path, face.fill)
            drawPath(path, strokeCol, style = Stroke(width = strokeW))
        }
    }
}
