package com.kit.autoweb.core

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.os.Handler
import android.os.Looper
import kotlin.math.pow

class GestureSimulator(private val targetView: View) {

    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "GestureSimulator"

    /**
     * 模拟单指点击
     */
    fun click(
        x: Float,
        y: Float,
        delay: Long = 100,
        callback: ((String) -> Unit)? = null
    ) {
        Log.d(TAG, "click: ($x,$y)")
        try {
            val downTime = SystemClock.uptimeMillis()

            // 1️⃣ 按下
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                x, y,
                1.0f, 1.0f, 0,
                1.0f, 1.0f, 0, 0
            )

            // 2️⃣ 抬起
            val upEvent = MotionEvent.obtain(
                downTime, downTime + delay,
                MotionEvent.ACTION_UP,
                x, y,
                1.0f, 1.0f, 0,
                1.0f, 1.0f, 0, 0
            )

            handler.post {
                targetView.dispatchTouchEvent(downEvent)
                handler.postDelayed({
                    targetView.dispatchTouchEvent(upEvent)

                    downEvent.recycle()
                    upEvent.recycle()

                    callback?.invoke("SUCCESS: 模拟点击 ($x,$y)")
                }, delay)
            }
        } catch (e: Exception) {
            callback?.invoke("ERROR: ${e.message}")
        }
    }

    /**
     * 模拟单指滑动
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 3000,
        callback: ((String) -> Unit)? = null
    ) {

        Log.d(TAG, "swipe: ($startX,$startY) → ($endX,$endY)")
        try {
            val downTime = SystemClock.uptimeMillis()

            // 1️⃣ 按下
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                startX, startY,
                1.0f, 1.0f, 0,
                1.0f, 1.0f, 0, 0
            )

            handler.post {
                targetView.dispatchTouchEvent(downEvent)

                val steps = 20
                val timeStep = duration / steps

                for (i in 1..steps) {
                    val progress = i.toFloat() / steps
                    val interpolated = easeInOutCubic(progress)
                    val currentX = startX + (endX - startX) * interpolated
                    val currentY = startY + (endY - startY) * interpolated
                    val eventTime = downTime + i * timeStep

                    val moveEvent = MotionEvent.obtain(
                        downTime, eventTime,
                        MotionEvent.ACTION_MOVE,
                        currentX, currentY,
                        1.0f, 1.0f, 0,
                        1.0f, 1.0f, 0, 0
                    )
                    targetView.dispatchTouchEvent(moveEvent)
                    moveEvent.recycle()
                }

                val upEvent = MotionEvent.obtain(
                    downTime, downTime + duration,
                    MotionEvent.ACTION_UP,
                    endX, endY,
                    1.0f, 1.0f, 0,
                    1.0f, 1.0f, 0, 0
                )

                targetView.dispatchTouchEvent(upEvent)

                downEvent.recycle()
                upEvent.recycle()

                callback?.invoke("SUCCESS: 模拟滑动 ($startX,$startY) → ($endX,$endY)")
            }
        } catch (e: Exception) {
            callback?.invoke("ERROR: ${e.message}")
        }
    }

    /**
     * 缓动函数：先慢→快→慢
     */
    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).toDouble().pow(3.0).toFloat() / 2
    }
}
