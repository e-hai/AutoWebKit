package com.sample.autoweb.ui.screen

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import android.util.Log
import com.kit.autoweb.core.WebViewManager
import com.kit.autoweb.ui.model.ElementInfo

// ViewModel
class WebViewViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WebViewUiState())
    val uiState: StateFlow<WebViewUiState> = _uiState.asStateFlow()

    private var webViewManager: WebViewManager? = null


    fun setWebViewManager(manager: WebViewManager) {
        Log.d("WebViewViewModel", "设置WebViewManager: $manager")
        webViewManager = manager
    }


    fun scanElements() {
        Log.d("WebViewViewModel", "开始扫描元素...")
        webViewManager?.getAllElements { elements ->
            showElementList(elements)
        }
    }


    fun showElementList(elements: List<ElementInfo>) {
        Log.d("WebViewViewModel", "显示元素列表: $elements")
        viewModelScope.launch {
            val visibleElements = elements.filter { it.isVisible }
            _uiState.update { it.copy(elements = visibleElements) }
        }
    }


    fun onElementClick(elementInfo: ElementInfo) {
        Log.d("WebViewViewModel", "点击元素: $elementInfo")
        viewModelScope.launch {
            delay(600)
            val position = PointF(elementInfo.x.toFloat(), elementInfo.y.toFloat())
            _uiState.update { it.copy(simulationClickPosition = position) }
            delay(3000)

            webViewManager?.simulateNativeTouchEvent(
                elementInfo.x.toFloat(),
                elementInfo.y.toFloat()
            ) { result ->
                Log.d("WebViewViewModel", "Click element result: $result")
            }

            // 自动清除点击位置
            delay(1000)
            _uiState.update { it.copy(simulationClickPosition = null) }
        }
    }

}

// UI State
data class WebViewUiState(
    val simulationClickPosition: PointF? = null,     //模拟点击的位置
    val elements: List<ElementInfo> = emptyList(),
)