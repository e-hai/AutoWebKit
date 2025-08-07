package com.sample.autoweb.ui.screen

import android.graphics.PointF
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kit.autoweb.ui.model.ElementInfo
import com.kit.autoweb.ui.model.ElementUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kit.autoweb.ui.widget.ClickPositionIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    webUrl: String,
) {
    val webViewViewModel: WebViewViewModel = viewModel()
    val uiState by webViewViewModel.uiState.collectAsState()
    val isFloatingActionButtonVisible = remember { mutableStateOf(false) } //Floating按钮显隐
    val isControlPanelVisible = remember { mutableStateOf(false) }         //控制面板显隐
    val webLoadProgress = remember { mutableFloatStateOf(0f) }

    PrintScreenSize()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        content = { innerPadding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // WebView 容器
                WebViewContainer(
                    url = webUrl,
                    modifier = Modifier.fillMaxSize(),
                    onProgressChanged = {
                        webLoadProgress.floatValue = it.toFloat() / 100f
                    },
                    onWebViewReady = {
                        webViewViewModel.setWebViewManager(it)
                        isFloatingActionButtonVisible.value = true
                    }
                )


                // 进度条
                LoadingProgressBar(
                    progress = webLoadProgress.floatValue,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )


                // 点击位置指示器
                ClickPositionIndicator(
                    position = uiState.simulationClickPosition,
                )

                // 控制面板

                AnimatedVisibility(
                    visible = isControlPanelVisible.value,
                    enter = slideInVertically(animationSpec = tween(durationMillis = 200)),
                    exit = slideOutVertically(animationSpec = tween(durationMillis = 200))
                ) {

                    val effectScope = rememberCoroutineScope()
                    var isClickEnabled = remember { true }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 60.dp)
                            .background(
                                MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 10.dp)
                        ) {

                            ScanElementsButton(
                                onScanElements = {
                                    webViewViewModel.scanElements()
                                }
                            )
                            ScanUrlsButton(
                                onScanUrls = {
                                    webViewViewModel.scanUrls()
                                }
                            )
                            UrlListContent(
                                urls = uiState.urls,
                                isClickEnabled = isClickEnabled,
                                onUrlClick = { url ->
                                    isControlPanelVisible.value = false
                                    if (isClickEnabled) {
                                        isClickEnabled = false
                                        // 延迟恢复点击能力
                                        effectScope.launch {
                                            delay(1500)
                                            isClickEnabled = true
                                        }
                                        webViewViewModel.onClickUrl(url)
                                    }
                                }
                            )
                            ElementListContent(
                                elements = uiState.elements,
                                isClickEnabled = isClickEnabled,
                                onElementClick = { element ->
                                    isControlPanelVisible.value = false
                                    if (isClickEnabled) {
                                        isClickEnabled = false
                                        // 延迟恢复点击能力
                                        effectScope.launch {
                                            delay(1500)
                                            isClickEnabled = true
                                        }
                                        webViewViewModel.onElementClick(element)
                                    }
                                }
                            )
                        }
                    }

                }
            }
        },
        floatingActionButton = {
            WebViewFloatingActionButton(
                isVisible = isFloatingActionButtonVisible.value,
                isSwitched = isControlPanelVisible.value,
                onSwitchChanged = {
                    isControlPanelVisible.value = it
                }
            )
        }
    )
}

@Composable
private fun PrintScreenSize() {
    // 获取屏幕尺寸并打印到日志
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
    val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
    Log.d("WebViewScreen", "Screen size: ${screenWidthDp}dp x ${screenHeightDp}dp")
}

@Composable
fun ScanUrlsButton(onScanUrls: () -> Unit) {
    Button(
        onClick = onScanUrls,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
    ) {
        Text("扫描URL")
    }
}

@Composable
fun UrlListContent(
    urls: List<ElementUrl>,
    isClickEnabled: Boolean,
    onUrlClick: (ElementUrl) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(urls.size, key = { it }) { index ->
            val element = urls[index]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = element.url,
                    modifier = Modifier.clickable(enabled = isClickEnabled) {
                        onUrlClick(element)
                    }
                )
            }
        }
    }
}


@Composable
private fun LoadingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    if (progress in 0.001f..0.999f) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}

@Composable
private fun WebViewFloatingActionButton(
    isVisible: Boolean,
    isSwitched: Boolean,
    onSwitchChanged: (isOpen: Boolean) -> Unit
) {
    if (isVisible) {
        FloatingActionButton(onClick = {
            onSwitchChanged(!isSwitched)
        }) {
            Icon(
                imageVector = if (isSwitched) Icons.Default.Close else Icons.Default.Settings,
                contentDescription = if (isSwitched) "Hide Panel" else "Show Panel"
            )
        }
    }
}


@Composable
private fun ScanElementsButton(
    onScanElements: () -> Unit
) {
    Button(
        onClick = onScanElements,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
    ) {
        Text("扫描元素")
    }
}


@Composable
private fun ElementListContent(
    elements: List<ElementInfo>,
    isClickEnabled: Boolean,
    onElementClick: (ElementInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(elements.size, key = { it }) { index ->
            val element = elements[index]
            ElementListItem(
                element = element,
                isClickEnabled = isClickEnabled,
                onClick = { onElementClick(element) }
            )
        }
    }
}

@Composable
private fun ElementListItem(
    element: ElementInfo,
    isClickEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = isClickEnabled) { onClick() }
            .alpha(if (isClickEnabled) 1f else 0.6f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "ID: ${element.identifier}",
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "位置: (${element.x}, ${element.y})",
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (element.isVisible) "可见" else "不可见",
            color = if (element.isVisible) Color.Green else Color.Gray
        )
    }
}



