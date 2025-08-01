package com.sample.autoweb.ui.screen

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import android.util.Log
import com.kit.autoweb.core.WebViewManager
import com.kit.autoweb.ui.model.ElementInfo
import com.kit.autoweb.ui.model.ElementUrl

// ViewModel
class WebViewViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WebViewUiState())
    val uiState: StateFlow<WebViewUiState> = _uiState.asStateFlow()

    private var webViewManager: WebViewManager? = null


    fun setWebViewManager(manager: WebViewManager) {
        Log.d(TAG, "设置WebViewManager: $manager")
        webViewManager = manager
    }


    fun scanUrls() {
        Log.d(TAG, "开始扫描链接...")
        webViewManager?.getAllUrls {
            Log.d(TAG, "获取到的所有链接: $it")
            showUrlList(it)
        }
    }

    fun onClickUrl(url: ElementUrl) {
        Log.d(TAG, "点击链接: $url")
        webViewManager?.loadUrl(url.url)
    }

    private fun showUrlList(urls: List<ElementUrl>) {
        viewModelScope.launch {
            _uiState.update { it.copy(urls = urls) }
        }
    }

    fun scanElements() {
        Log.d(TAG, "开始扫描元素...")
        webViewManager?.getAllElements { elements ->
            showElementList(elements)
        }
    }


    fun showElementList(elements: List<ElementInfo>) {
        Log.d(TAG, "显示元素列表: $elements")
        viewModelScope.launch {
            _uiState.update { it.copy(elements = elements) }
        }
    }


    fun onElementClick(elementInfo: ElementInfo) {
        Log.d(TAG, "点击元素: $elementInfo")
        viewModelScope.launch {
//            webViewManager?.clickElementWithStepsImproved(elementInfo) {
//                Log.d(TAG, "Click element result: $it")
//            }
//
            delay(1000)
            val position = PointF(elementInfo.x.toFloat(), elementInfo.y.toFloat())
            _uiState.update { it.copy(simulationClickPosition = position) }
            delay(2000)

            webViewManager?.simulateNativeTouchEvent(
                elementInfo.x.toFloat(),
                elementInfo.y.toFloat()
            ) { result ->
                Log.d(TAG, "Click element result: $result")
            }

            // 自动清除点击位置
            delay(1000)
            _uiState.update { it.copy(simulationClickPosition = null) }
        }
    }


    companion object {
        const val TAG = "WebViewViewModel"
    }
}

// UI State
data class WebViewUiState(
    val simulationClickPosition: PointF? = null,     //模拟点击的位置
    val elements: List<ElementInfo> = emptyList(),
    val urls: List<ElementUrl> = emptyList(),
)