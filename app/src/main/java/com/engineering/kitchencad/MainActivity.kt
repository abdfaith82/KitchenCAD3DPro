package com.engineering.kitchencad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlin.math.*

enum class UnitType(val label: String, val defaultW: Float, val defaultD: Float, val defaultH: Float, val baseZ: Float, val baseColor: Long) {
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
    val x: Float,      // بالسنتيمتر (cm)
    val y: Float,      // بالسنتيمتر (cm)
    val width: Float,  // العرض (cm)
    val depth: Float,  // العمق (cm)
    val height: Float, // الارتفاع (cm)
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

    private fun snap(v: Float, grid: Float): Float = round(v / grid) * grid

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
            val hit = s.elements.lastOrNull { el ->
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
                if (el.id == id) el.copy(width = el.depth, depth = el.width, rotationDeg = (el.rotationDeg + 90f) % 360f)
                else el
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

@Composable
fun KitchenCadApp(vm: KitchenCadViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val selectedElement = state.elements.find { it.id == state.selectedId }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF12161F))) {
        // الشريط العلوي
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2433)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("KITCHEN CAD 3D PRO", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val totalAreaM2 = state.elements.filter { it.type != UnitType.WALL }.sumOf { (it.width * it.depth).toDouble() } / 10000.0
                Text(
                    "الوحدات: ${state.elements.size} | المساحة الإجمالية: ${String.format("%.2f", totalAreaM2)} م²",
                    color = Color.LightGray, fontSize = 11.sp
                )
            }
            Button(
                onClick = { vm.toggleViewMode() },
                colors = ButtonDefaults.buttonColors(containerColor = if (state.is3DMode) Color(0xFFFF6D00) else Color(0xFF00B0FF))
            ) {
                Text(if (state.is3DMode) "مسقط 2D CAD" else "معاينة 3D ISO", fontWeight = FontWeight.Bold)
            }
        }

        // شريط إضافة الوحدات المعمارية
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF181D29)).horizontalScroll(rememberScrollState()).padding(6.dp),
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

        // لوحة الرسم الهندسي 2D / 3D
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.is3DMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            vm.onPanZoom(pan, zoom)
                        }
                    }
                    .pointerInput(state.is3DMode, state.zoom, state.panOffset) {
                        if (!state.is3DMode) {
                            detectTapGestures { tap ->
                                val worldX = (tap.x - state.panOffset.x) / state.zoom
                                val worldY = (tap.y - state.panOffset.y) / state.zoom
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

            // بوصلة التوجيه وإرشادات التحكم
            Text(
                text = if (state.is3DMode) "وضع 3D: اسحب لتدوير الكاميرا بزاوية 360° | قرّب للتكبير"
                       else "وضع 2D CAD (مقياس 1سم : ${String.format("%.1f", state.zoom)}px) | الشبكة: 10سم",
                color = Color(0xFF90A4AE),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(6.dp)
            )
        }

        // لوحة التحكم بالوحدة المحددة (أسفل الشاشة)
        if (selectedElement != null) {
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1E2433)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "العنصر المحدد: ${selectedElement.type.label} | الأبعاد: ${selectedElement.width.toInt()}×${selectedElement.depth.toInt()}×${selectedElement.height.toInt()} سم | الموقع: (${selectedElement.x.toInt()}, ${selectedElement.y.toInt()})",
                    color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallCtrlBtn("◀ يسار") { vm.moveSelected(-10f, 0f) }
                    SmallCtrlBtn("يمين ▶") { vm.moveSelected(10f, 0f) }
                    SmallCtrlBtn("▲ أعلى") { vm.moveSelected(0f, -10f) }
                    SmallCtrlBtn("أسفل ▼") { vm.moveSelected(0f, 10f) }
                    SmallCtrlBtn("🔄 تدوير 90°") { vm.rotateSelected() }
                    SmallCtrlBtn("عرض +10سم") { vm.resizeSelected(10f, 0f) }
                    SmallCtrlBtn("عرض -10سم") { vm.resizeSelected(-10f, 0f) }
                    SmallCtrlBtn("عمق +10سم") { vm.resizeSelected(0f, 10f) }
                    SmallCtrlBtn("🗑️ حذف", Color(0xFFC62828)) { vm.deleteSelected() }
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

// دالة الرسم الهندسي ثنائي الأبعاد (2D CAD Blueprint)
private fun DrawScope.draw2DCadPlan(state: CadState) {
    val z = state.zoom
    val ox = state.panOffset.x
    val oy = state.panOffset.y

    // 1. رسم شبكة القياس المتري (كل 20 سم خط فرعي وكل 100 سم خط رئيسي)
    val stepSub = 20f * z
    val cols = (size.width / stepSub).toInt() + 20
    val rows = (size.height / stepSub).toInt() + 20
    for (i in -10..cols) {
        val x = (ox % (100f * z)) + i * stepSub
        val isMajor = i % 5 == 0
        drawLine(
            color = if (isMajor) Color(0xFF263238) else Color(0xFF1A2228),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (isMajor) 1.8f else 0.8f
        )
    }
    for (j in -10..rows) {
        val y = (oy % (100f * z)) + j * stepSub
        val isMajor = j % 5 == 0
        drawLine(
            color = if (isMajor) Color(0xFF263238) else Color(0xFF1A2228),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (isMajor) 1.8f else 0.8f
        )
    }

    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = (10f * z).coerceIn(18f, 32f)
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // 2. رسم العناصر والوحدات مع خطوط الأبعاد
    state.elements.sortedBy { it.elevationZ }.forEach { el ->
        val sx = ox + el.x * z
        val sy = oy + el.y * z
        val sw = el.width * z
        val sd = el.depth * z
        val isSelected = el.id == state.selectedId

        drawRect(
            color = Color(el.type.baseColor).copy(alpha = if (el.type == UnitType.WALL_CABINET) 0.55f else 0.88f),
            topLeft = Offset(sx, sy),
            size = Size(sw, sd)
        )

        drawRect(
            color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.7f),
            topLeft = Offset(sx, sy),
            size = Size(sw, sd),
            style = Stroke(width = if (isSelected) 3.5f else 1.5f)
        )

        // رمز هندسي لحوض الغسيل
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

        // كتابة المقاس بالسنتيمتر داخل الوحدة
        drawContext.canvas.nativeCanvas.drawText(
            "${el.width.toInt()}×${el.depth.toInt()}cm",
            sx + sw / 2f,
            sy + sd / 2f + 8f,
            textPaint
        )
    }
}

// دالة الإسقاط الهندسي ثلاثي الأبعاد (3D Isometric Projection + Shading)
private fun DrawScope.draw3DIsometricScene(state: CadState) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.62f
    val scale = state.zoom * 1.15f
    val yawRad = Math.toRadians(state.yawDeg.toDouble())
    val pitchRad = Math.toRadians(state.pitchDeg.toDouble())

    fun project3D(xCm: Float, yCm: Float, zCm: Float): Offset {
        val shiftedX = xCm - 200f
        val shiftedY = yCm - 150f
        val rx = shiftedX * cos(yawRad) - shiftedY * sin(yawRad)
        val ry = shiftedX * sin(yawRad) + shiftedY * cos(yawRad)
        val screenX = cx + (rx * scale).toFloat()
        val screenY = cy + ((ry * sin(pitchRad) - zCm * cos(pitchRad)) * scale).toFloat()
        return Offset(screenX, screenY)
    }

    // 1. رسم بلاط أرضية المطبخ ثلاثي الأبعاد (400×350 سم)
    for (gx in 0..400 step 50) {
        val p1 = project3D(gx.toFloat(), 0f, 0f)
        val p2 = project3D(gx.toFloat(), 350f, 0f)
        drawLine(Color(0xFF263238), p1, p2, 1.5f)
    }
    for (gy in 0..350 step 50) {
        val p1 = project3D(0f, gy.toFloat(), 0f)
        val p2 = project3D(400f, gy.toFloat(), 0f)
        drawLine(Color(0xFF263238), p1, p2, 1.5f)
    }

    // 2. ترتيب الكتل حسب البعد عن الكاميرا (Painter's Algorithm)
    val sortedElements = state.elements.sortedBy { el ->
        val midX = el.x + el.width / 2f
        val midY = el.y + el.depth / 2f
        (midX * sin(yawRad) + midY * cos(yawRad)) + el.elevationZ * 0.2
    }

    // 3. رسم كل وحدة ثلاثية الأبعاد مع تظليل الأوجه والرخام العلوي
    sortedElements.forEach { el ->
        val x0 = el.x
        val x1 = el.x + el.width
        val y0 = el.y
        val y1 = el.y + el.depth
        val z0 = el.elevationZ
        val z1 = el.elevationZ + el.height

        val b00 = project3D(x0, y0, z0)
        val b10 = project3D(x1, y0, z0)
        val b11 = project3D(x1, y1, z0)
        val b01 = project3D(x0, y1, z0)

        val t00 = project3D(x0, y0, z1)
        val t10 = project3D(x1, y0, z1)
        val t11 = project3D(x1, y1, z1)
        val t01 = project3D(x0, y1, z1)

        val baseCol = Color(el.type.baseColor)
        val isSelected = el.id == state.selectedId
        val strokeCol = if (isSelected) Color(0xFF00E5FF) else Color(0xFF10141D)
        val strokeW = if (isSelected) 3f else 1.3f

        fun drawQuad(p1: Offset, p2: Offset, p3: Offset, p4: Offset, fill: Color) {
            val path = Path().apply {
                moveTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                lineTo(p4.x, p4.y)
                close()
            }
            drawPath(path, fill)
            drawPath(path, strokeCol, style = Stroke(width = strokeW))
        }

        // الأوجه الجانبية والأمامية بدرجات إضاءة واقعية
        drawQuad(b00, b10, t10, t00, baseCol.copy(alpha = 0.75f))
        drawQuad(b00, b01, t01, t00, baseCol.copy(alpha = 0.80f))
        drawQuad(b10, b11, t11, t10, baseCol.copy(alpha = 0.88f))
        drawQuad(b01, b11, t11, t01, baseCol.copy(alpha = 0.95f))

        // السطح العلوي (رخام أبيض للخزائن الأرضية والجزيرة)
        val topSurfaceColor = if (el.type == UnitType.BASE_CABINET || el.type == UnitType.ISLAND || el.type == UnitType.SINK_UNIT) {
            Color(0xFFECEFF1)
        } else {
            baseCol
        }
        drawQuad(t00, t10, t11, t01, topSurfaceColor)
    }
}
