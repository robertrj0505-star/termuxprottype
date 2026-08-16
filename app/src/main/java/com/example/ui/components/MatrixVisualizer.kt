package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MatrixVisualizer(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glyphs = "ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ0123456789:・.\"=*+-<>|"
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            tick++
        }
    }

    val columns = 28
    val drops = remember { IntArray(columns) { Random.nextInt(-30, 0) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000500))
            .testTag("matrix_visualizer")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val colWidth = size.width / columns
            val rowHeight = 28f
            val maxRows = (size.height / rowHeight).toInt() + 5

            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 22f
            }

            for (i in 0 until columns) {
                val y = drops[i]

                for (j in 0..16) {
                    val row = y - j
                    if (row in 0 until maxRows) {
                        val charIndex = (i * 7 + row * 13 + tick.toInt()) % glyphs.length
                        val char = glyphs[abs(charIndex)]

                        if (j == 0) {
                            paint.color = android.graphics.Color.WHITE
                            paint.setShadowLayer(8f, 0f, 0f, android.graphics.Color.GREEN)
                        } else {
                            val alpha = ((16 - j) / 16f * 255).toInt().coerceIn(40, 255)
                            paint.color = android.graphics.Color.argb(alpha, 0, 255, 100)
                            paint.clearShadowLayer()
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            char.toString(),
                            i * colWidth + colWidth / 4,
                            row * rowHeight,
                            paint
                        )
                    }
                }

                drops[i]++
                if (drops[i] * rowHeight > size.height && Random.nextFloat() > 0.95f) {
                    drops[i] = 0
                }
            }
        }

        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0x99000000),
                contentColor = Color(0xFF00FF66)
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Exit (Q)", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}
private fun abs(n: Int): Int = if (n < 0) -n else n
