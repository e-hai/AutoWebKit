package com.kit.autoweb.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RelativeLayout

@SuppressLint("SetJavaScriptEnabled")
class AutoWebViewGroup : RelativeLayout {

    private lateinit var webView: WebView

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initWebView()
    }

    private fun initWebView() {
        webView = WebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        // 将 webView 添加到当前的 RelativeLayout 中
        addView(webView)
    }

    fun setup(
        onWebViewReady: (manager: WebViewManager) -> Unit,
        onProgressChanged: (progress: Int) -> Unit
    ) {
        webView.settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
        }

        var isReady = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(
                    "WebViewContainer",
                    "页面完全加载完成，所有资源（图片、JS 等）都加载完毕: $url "
                )
                view?.let { webView ->
                    Log.d("WebViewContainer", "webView尺寸: ${webView.width}x${webView.height}")

                    if (isReady) return
                    val manager = WebViewManager(webView)
                    onWebViewReady(manager)
                    isReady = true
                }
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                Log.d("WebViewContainer", "页面内容首次可见，用户可以看到部分页面内容: $url")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebViewContainer", "页面开始加载，URL 第一次请求时触发: $url")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.d(
                    "WebViewContainer",
                    "页面加载错误，URL 最后一次请求时触发: ${request?.url}"
                )
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                Log.d(
                    "WebViewContainer",
                    "页面加载错误，URL 最后一次请求时触发: ${request?.url}"
                )
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.d(
                    "WebViewContainer",
                    "页面加载错误，URL 最后一次请求时触发: $error"
                )
                super.onReceivedSslError(view, handler, error)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                view?.let { webView ->
                    Log.d(
                        "WebViewContainer",
                        "页面进度更新,可以监听加载百分比（0~100），解决部分机型不回调 onPageFinished(): $newProgress"
                    )
                    onProgressChanged(newProgress)
                    if (newProgress == 100 && !isReady) {
                        isReady = true
                        val manager = WebViewManager(webView)
                        onWebViewReady(manager)
                    }
                }
            }
        }
    }

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    // 添加清理方法，避免内存泄漏
    fun destroy() {
        webView.destroy()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }
}