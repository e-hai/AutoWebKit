package com.sample.autoweb.ui.screen

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kit.autoweb.core.AutoWebViewGroup
import com.kit.autoweb.core.WebViewManager


@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    url: String,
    modifier: Modifier = Modifier,
    onProgressChanged: (Int) -> Unit,
    onWebViewReady: (WebViewManager) -> Unit
) {
    AndroidView(
        factory = { context ->
            AutoWebViewGroup(context).apply {
                setup(onWebViewReady = onWebViewReady, onProgressChanged = onProgressChanged)
                loadUrl(url)
            }
        },
        modifier = modifier
    )
}