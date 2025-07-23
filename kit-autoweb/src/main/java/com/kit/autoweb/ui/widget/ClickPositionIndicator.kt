package com.kit.autoweb.ui.widget

import android.graphics.PointF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClickPositionIndicator(
    position: PointF?
) {
    position?.let {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

            Log.d("ClickPositionIndicator", "maxWidthPx: $maxWidthPx, maxHeightPx: $maxHeightPx")
            Log.d("ClickPositionIndicator", "position: $position")

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val density = this.density
                val strokeWidth = 1.dp.toPx()
                val coordTextSize = 14.sp.toPx()
                val scaleInterval = 10.dp.toPx()
                val scaleTextSize = 10.sp.toPx()

                // 绘制水平刻度线（顶部）
                var x = 0f
                while (x <= maxWidthPx) {
                    // 刻度线
                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, if (x % (scaleInterval * 5) == 0f) 20f else 10f),
                        strokeWidth = strokeWidth
                    )

                    // 刻度数字（每150像素显示一次）
                    if (x % (scaleInterval * 5) == 0f) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "${x.toInt()}",
                            x,
                            35f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = scaleTextSize
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                    x += scaleInterval
                }

                // 绘制垂直刻度线（左侧）
                var y = 0f
                while (y <= maxHeightPx) {
                    // 刻度线
                    drawLine(
                        color = Color.White,
                        start = Offset(0f, y),
                        end = Offset(if (y % (scaleInterval * 5) == 0f) 20f else 10f, y),
                        strokeWidth = strokeWidth
                    )

                    // 刻度数字（每150像素显示一次）
                    if (y % (scaleInterval * 5) == 0f && y > 0) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "${y.toInt()}",
                            35f,
                            y + coordTextSize / 3,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = scaleTextSize
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                        )
                    }
                    y += scaleInterval
                }

                // 绘制点击位置
                val clickX = it.x
                val clickY = it.y

                // 水平线（连接到左边刻度）
                drawLine(
                    color = Color.Red,
                    start = Offset(0f, clickY),
                    end = Offset(clickX, clickY),
                    strokeWidth = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                )

                // 垂直线（连接到顶部刻度）
                drawLine(
                    color = Color.Red,
                    start = Offset(clickX, 0f),
                    end = Offset(clickX, clickY),
                    strokeWidth = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                )

                // 点击位置圆点
                drawCircle(
                    color = Color.Red,
                    radius = 15f,
                    center = Offset(clickX, clickY)
                )

                // 坐标信息背景
                val coordText = "(${clickX.toInt()}, ${clickY.toInt()})"
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = coordTextSize
                    isAntiAlias = true
                }

                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(coordText, 0, coordText.length, textBounds)

                // 计算坐标文本位置（避免超出边界）
                val textX = when {
                    clickX + textBounds.width() + 30 > maxWidthPx -> clickX - textBounds.width() - 30
                    else -> clickX + 30
                }

                val textY = when {
                    clickY - 30 < textBounds.height() -> clickY + textBounds.height() + 30
                    else -> clickY - 10
                }

                // 绘制坐标文本背景
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.8f),
                    topLeft = Offset(textX - 8, textY - textBounds.height() - 8),
                    size = Size(
                        textBounds.width() + 16f,
                        textBounds.height() + 16f
                    ),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // 绘制坐标文本
                drawContext.canvas.nativeCanvas.drawText(
                    coordText,
                    textX,
                    textY,
                    textPaint
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewClickPositionIndicator() {
    ClickPositionIndicator(
        PointF(100f, 100f)
    )
}