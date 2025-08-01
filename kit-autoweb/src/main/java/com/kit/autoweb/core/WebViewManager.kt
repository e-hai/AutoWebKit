package com.kit.autoweb.core


import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.kit.autoweb.ui.model.ElementInfo
import com.kit.autoweb.ui.model.ElementUrl
import com.kit.autoweb.ui.model.ElementWrap
import com.kit.autoweb.ui.model.PageScrollInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONArray
import java.lang.reflect.Type
import kotlin.math.abs

class WebViewManager(private val webView: WebView) {


    companion object {
        const val TAG = "WebViewManager"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val RETRY_DELAY = 500L

        // 懒加载相关常量
        private const val LAZY_LOAD_WAIT_TIME = 500L // 等待懒加载的时间
        private const val MAX_SCROLL_STEPS = 15 // 最大滑动步数
        private const val MIN_SCROLL_DISTANCE = 100f // 最小滑动距离（像素）
        private const val MAX_SCROLL_DISTANCE = 300f // 最大滑动距离（像素）
        private const val PAGE_STABILITY_CHECK_INTERVAL = 200L // 页面稳定性检查间隔
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gestureSimulator = GestureSimulator(webView)

    /**
     * 智能滑动到元素位置，处理懒加载问题
     */
    private fun scrollToMakeElementVisibleSmart(element: ElementInfo, callback: (String) -> Unit) {
        Log.d(TAG, "开始智能滑动到元素: ${element.identifier}")

        // 首先检查元素是否已经接近视口
        if (isElementNearViewport(element)) {
            Log.d(TAG, "元素接近视口，使用精确滑动")
            performPreciseScroll(element, callback)
        } else {
            Log.d(TAG, "元素距离较远，使用分步滑动")
            // 获取初始页面状态
            getPageScrollInfo { initialPageInfo ->
                performSmartScroll(element, initialPageInfo, 0, callback)
            }
        }
    }

    /**
     * 检查元素是否接近视口（在1.5倍视口范围内）
     */
    private fun isElementNearViewport(element: ElementInfo): Boolean {
        val viewportWidth = webView.width
        val viewportHeight = webView.height
        val threshold = 1.5f

        return element.x >= -viewportWidth * threshold && element.x <= viewportWidth * (1 + threshold) &&
                element.y >= -viewportHeight * threshold && element.y <= viewportHeight * (1 + threshold)
    }

    /**
     * 执行精确滑动（当元素距离不远时）
     */
    private fun performPreciseScroll(element: ElementInfo, callback: (String) -> Unit) {
        val viewportWidth = webView.width
        val viewportHeight = webView.height
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f

        // 计算需要滑动的精确距离，但限制最大值
        val deltaX = element.x - centerX
        val deltaY = element.y - centerY

        // 限制滑动距离为较小的值
        val maxPreciseDistance = 100f
        val scrollX = when {
            abs(deltaX) > maxPreciseDistance -> maxPreciseDistance * if (deltaX > 0) 1f else -1f
            abs(deltaX) < 50f -> 0f // 太小的距离不滑动
            else -> deltaX
        }

        val scrollY = when {
            abs(deltaY) > maxPreciseDistance -> maxPreciseDistance * if (deltaY > 0) 1f else -1f
            abs(deltaY) < 50f -> 0f // 太小的距离不滑动
            else -> deltaY
        }


        if (abs(scrollX) > abs(scrollY)) {
            // 主要进行水平滑动
            val toX = centerX - scrollX
            Log.d(TAG, "精确水平滑动: ${abs(scrollX)}px")
            simulateSwipe(centerX, centerY, toX, centerY, 200) { result ->
                waitForLazyLoadComplete {
                    callback(result)
                }
            }
        } else if (abs(scrollY) > 10f) {
            // 主要进行垂直滑动
            val toY = centerY - scrollY
            Log.d(TAG, "精确垂直滑动: ${abs(scrollY)}px 最终位置：$toY")
            simulateSwipe(centerX, centerY, centerX, toY, 200) { result ->
                waitForLazyLoadComplete {
                    callback(result)
                }
            }
        } else {
            // 距离太小，不需要滑动
            Log.d(TAG, "元素已在合适位置，无需滑动")
            callback("SUCCESS: 元素位置合适，无需滑动")
        }
    }

    /**
     * 执行智能滑动，分步进行并处理懒加载
     */
    private fun performSmartScroll(
        targetElement: ElementInfo,
        initialPageInfo: PageScrollInfo,
        currentStep: Int,
        callback: (String) -> Unit
    ) {
        if (currentStep >= MAX_SCROLL_STEPS) {
            callback("ERROR: 达到最大滑动步数，未能找到目标元素")
            return
        }

        Log.d(TAG, "执行滑动步骤 ${currentStep + 1}/$MAX_SCROLL_STEPS")

        // 首先检查目标元素是否已经可见
        getElementPosition(targetElement) { positionResult ->
            if (!positionResult.startsWith("ERROR")) {
                // 解析元素位置
                parseElementPosition(positionResult) { currentElement ->
                    if (currentElement != null && isElementInViewport(currentElement)) {
                        callback("SUCCESS: 元素已在视口内，无需继续滑动")
                        return@parseElementPosition
                    }

                    // 元素存在但不在视口内，继续滑动
                    if (currentElement != null) {
                        executeSmartScrollStep(currentElement, initialPageInfo) { scrollResult ->
                            // 等待懒加载完成
                            waitForLazyLoadComplete {
                                // 递归继续下一步
                                performSmartScroll(
                                    targetElement,
                                    initialPageInfo,
                                    currentStep + 1,
                                    callback
                                )
                            }
                        }
                    } else {
                        // 元素不存在，尝试滑动加载更多内容
                        executeExploratoryScroll(initialPageInfo, currentStep) { scrollResult ->
                            waitForLazyLoadComplete {
                                performSmartScroll(
                                    targetElement,
                                    initialPageInfo,
                                    currentStep + 1,
                                    callback
                                )
                            }
                        }
                    }
                }
            } else {
                // 元素不存在，尝试滑动加载更多内容
                executeExploratoryScroll(initialPageInfo, currentStep) { scrollResult ->
                    waitForLazyLoadComplete {
                        performSmartScroll(
                            targetElement,
                            initialPageInfo,
                            currentStep + 1,
                            callback
                        )
                    }
                }
            }
        }
    }

    /**
     * 执行智能滑动步骤 - 基于元素位置计算滑动方向和距离
     */
    private fun executeSmartScrollStep(
        element: ElementInfo,
        pageInfo: PageScrollInfo,
        callback: (String) -> Unit
    ) {
        val viewportWidth = webView.width
        val viewportHeight = webView.height
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f

        // 计算元素相对于视口中心的位置
        val elementCenterX = element.x
        val elementCenterY = element.y

        // 计算需要滑动的方向和距离
        val deltaX = elementCenterX - centerX
        val deltaY = elementCenterY - centerY

        // 确定主要滑动方向（垂直或水平）
        val isVerticalScroll = abs(deltaY) > abs(deltaX)

        val scrollDistance = if (isVerticalScroll) {
            // 垂直滑动：使用更小且固定的滑动距离
            val targetDistance = abs(deltaY)
            when {
                targetDistance > MAX_SCROLL_DISTANCE -> MAX_SCROLL_DISTANCE
                targetDistance < MIN_SCROLL_DISTANCE -> MIN_SCROLL_DISTANCE
                else -> targetDistance
            } * if (deltaY > 0) 1f else -1f
        } else {
            // 水平滑动：使用更小且固定的滑动距离
            val targetDistance = abs(deltaX)
            when {
                targetDistance > MAX_SCROLL_DISTANCE -> MAX_SCROLL_DISTANCE
                targetDistance < MIN_SCROLL_DISTANCE -> MIN_SCROLL_DISTANCE
                else -> targetDistance
            } * if (deltaX > 0) 1f else -1f
        }

        // 执行滑动
        if (isVerticalScroll) {
            val fromY = centerY
            val toY = centerY - scrollDistance // 注意方向：向上滑动是负值

            Log.d(TAG, "垂直滑动: 从 $fromY 到 $toY (实际距离: ${abs(scrollDistance)}px)")
            simulateSwipe(centerX, fromY, centerX, toY, 300, callback)
        } else {
            val fromX = centerX
            val toX = centerX - scrollDistance // 注意方向：向左滑动是负值

            Log.d(TAG, "水平滑动: 从 $fromX 到 $toX (实际距离: ${abs(scrollDistance)}px)")
            simulateSwipe(fromX, centerY, toX, centerY, 300, callback)
        }
    }

    /**
     * 执行探索性滑动 - 当元素不存在时，逐步滑动页面寻找元素
     */
    private fun executeExploratoryScroll(
        pageInfo: PageScrollInfo,
        currentStep: Int,
        callback: (String) -> Unit
    ) {
        val viewportWidth = webView.width
        val viewportHeight = webView.height
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f

        // 使用固定的小距离进行探索性滑动
        val verticalScrollDistance = MIN_SCROLL_DISTANCE * 1.5f // 150px
        val horizontalScrollDistance = MIN_SCROLL_DISTANCE * 1.2f // 120px

        when (currentStep % 4) {
            0 -> {
                // 向下滑动
                Log.d(TAG, "探索性滑动: 向下 ${verticalScrollDistance}px")
                simulateSwipe(
                    centerX,
                    centerY,
                    centerX,
                    centerY - verticalScrollDistance,
                    250,
                    callback
                )
            }

            1 -> {
                // 向上滑动
                Log.d(TAG, "探索性滑动: 向上 ${verticalScrollDistance}px")
                simulateSwipe(
                    centerX,
                    centerY,
                    centerX,
                    centerY + verticalScrollDistance,
                    250,
                    callback
                )
            }

            2 -> {
                // 向右滑动
                Log.d(TAG, "探索性滑动: 向右 ${horizontalScrollDistance}px")
                simulateSwipe(
                    centerX,
                    centerY,
                    centerX - horizontalScrollDistance,
                    centerY,
                    250,
                    callback
                )
            }

            3 -> {
                // 向左滑动
                Log.d(TAG, "探索性滑动: 向左 ${horizontalScrollDistance}px")
                simulateSwipe(
                    centerX,
                    centerY,
                    centerX + horizontalScrollDistance,
                    centerY,
                    250,
                    callback
                )
            }
        }
    }

    /**
     * 等待懒加载完成
     */
    private fun waitForLazyLoadComplete(callback: () -> Unit) {
        Log.d(TAG, "等待懒加载完成...")

        var checkCount = 0
        val maxChecks = 8 // 最多检查8次

        fun checkLoadingStatus() {
            checkCount++

            val javascript = """
                (function() {
                    var status = {
                        timestamp: Date.now(),
                        documentReady: document.readyState === 'complete',
                        activeRequests: 0,
                        newImages: 0,
                        loadingImages: 0,
                        totalElements: document.querySelectorAll('*').length
                    };
                    
                    // 检查图片加载状态
                    var images = document.images;
                    for (var i = 0; i < images.length; i++) {
                        if (!images[i].complete) {
                            status.loadingImages++;
                        }
                    }
                    
                    // 检查是否有新加载的内容（通过检查最近添加的元素）
                    var recentElements = document.querySelectorAll('[data-lazy], [loading="lazy"], .lazy, .lazyload');
                    status.lazyElements = recentElements.length;
                    
                    return JSON.stringify(status);
                })();
            """.trimIndent()

            webView.evaluateJavascript(javascript) { result ->
                Log.d(TAG, "懒加载状态检查 $checkCount/$maxChecks: $result")

                mainHandler.postDelayed({
                    if (checkCount >= maxChecks) {
                        Log.d(TAG, "懒加载等待完成")
                        callback()
                    } else {
                        checkLoadingStatus()
                    }
                }, PAGE_STABILITY_CHECK_INTERVAL)
            }
        }

        // 初始等待时间
        mainHandler.postDelayed({
            checkLoadingStatus()
        }, LAZY_LOAD_WAIT_TIME)
    }

    /**
     * 获取页面滚动信息
     */
    private fun getPageScrollInfo(callback: (PageScrollInfo) -> Unit) {
        val javascript = """
            (function() {
                return {
                    scrollTop: window.pageYOffset || document.documentElement.scrollTop,
                    scrollLeft: window.pageXOffset || document.documentElement.scrollLeft,
                    scrollWidth: document.documentElement.scrollWidth,
                    scrollHeight: document.documentElement.scrollHeight,
                    clientWidth: document.documentElement.clientWidth,
                    clientHeight: document.documentElement.clientHeight,
                    viewportWidth: window.innerWidth,
                    viewportHeight: window.innerHeight
                };
            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            try {
                val moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(PageScrollInfo::class.java)
                val pageInfo = adapter.fromJson(result) ?: PageScrollInfo()
                callback(pageInfo)
            } catch (e: Exception) {
                Log.e(TAG, "解析页面滚动信息失败", e)
                callback(PageScrollInfo())
            }
        }
    }

    /**
     * 改进的分步点击元素方法
     */
    fun clickElementWithStepsImproved(elementInfo: ElementInfo, callback: (String) -> Unit) {
        Log.d(TAG, "开始改进的分步点击元素: $elementInfo")

        // 第一步：尝试直接获取元素
        if (isElementInViewport(elementInfo)) {
            // 元素可见，直接点击
            performFinalClick(elementInfo, callback)
            return
        }

        getElementPosition(elementInfo) { positionResult ->
            if (!positionResult.startsWith("ERROR")) {
                // 元素存在，检查是否可见
                parseElementPosition(positionResult) { element ->
                    if (element != null) {
                        if (isElementInViewport(element)) {
                            // 元素可见，直接点击
                            performFinalClick(elementInfo, callback)
                        } else {
                            // 元素存在但不可见，智能滑动
                            scrollToMakeElementVisibleSmart(element) { scrollResult ->
                                Log.d(TAG, "智能滑动结果: $scrollResult")
                                if (scrollResult.startsWith("SUCCESS") || scrollResult.startsWith("ERROR")) {
                                    // 滑动完成，执行最终点击
                                    performFinalClick(elementInfo, callback)
                                } else {
                                    callback("ERROR: 滑动到元素失败 - $scrollResult")
                                }
                            }
                        }
                    } else {
                        callback("ERROR: 无法解析元素位置信息")
                    }
                }
            } else {
                // 元素不存在，尝试智能滑动寻找
                Log.d(TAG, "元素不存在，开始智能滑动寻找")
                getPageScrollInfo { pageInfo ->
                    performSmartScroll(elementInfo, pageInfo, 0) { searchResult ->
                        if (searchResult.startsWith("SUCCESS")) {
                            performFinalClick(elementInfo, callback)
                        } else {
                            callback("ERROR: 未能找到目标元素 - $searchResult")
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取网页内所有元素及其位置信息
     */
    fun getAllElements(callback: (List<ElementInfo>) -> Unit) {
        Log.d("WebViewManager", "getAllElements")
        val javascript = """
                            (function() {
                                    var allElements = [];
                                    
                                    // 获取设备像素比和viewport信息
                                    var devicePixelRatio = window.devicePixelRatio || 1;
                                    var viewportWidth = window.innerWidth;
                                    var viewportHeight = window.innerHeight;
                                    
                                    // 添加调试信息
                                    console.log('设备像素比:', devicePixelRatio);
                                    console.log('viewport尺寸:', viewportWidth, 'x', viewportHeight);
                                    console.log('屏幕尺寸:', screen.width, 'x', screen.height);
                                
                                    // 获取页面中所有的元素
                                    var elements = document.querySelectorAll('*');
                                    elements.forEach(function(element) {
                                    
                                        // 仅处理包含 id 或 className 的元素
                                        if (!element.id && (!element.className || typeof element.className !== 'string')) {
                                            return;
                                        }
                                
                                        var rect = element.getBoundingClientRect();
                                        var isVisible = rect.width > 0 && rect.height > 0 &&
                                                      window.getComputedStyle(element).visibility !== 'hidden' &&
                                                      window.getComputedStyle(element).display !== 'none' &&
                                                      !(rect.bottom < 0 || rect.top > window.innerHeight ||
                                                        rect.right < 0 || rect.left > window.innerWidth);
                                
                                        // 收集基本标识符（优先使用 id，其次 class，最后 tagName）
                                        var identifier = null;
                                        if (element.id) {
                                            identifier = '#' + element.id;
                                        } else if (element.className && typeof element.className === 'string') {
                                            var className = element.className.replace(/\s+/g, '.');
                                            identifier = '.' + className;
                                        } else {
                                            identifier = element.tagName.toLowerCase();
                                        }
                                
                                        // 检查是否有title属性，有的话返回，没有就空字符串
                                        var title = element.title || '';
                                        // 限制title长度不超过20个字符
                                        title = title.length > 20 ? title.substring(0, 20) : title;
                                        
                                        // 检查元素是否可点击
                                        var url = '';
                                        
                                        // 检查是否是链接元素
                                        if (element.tagName.toLowerCase() === 'a' && element.href) {
                                            url = element.href;
                                        }
                                        
                                        // 检查是否有data-href属性
                                        if (element.hasAttribute('data-href')) {
                                            url = element.getAttribute('data-href') || '';
                                        }
                                
                                        // 收集元素的基本信息（可选择是否应用设备像素比）
                                        var elementInfo = {
                                            identifier: identifier,
                                            // 如果需要物理像素坐标，可以使用下面这些
                                            x: Math.round(rect.left + rect.width / 2),
                                            y: Math.round(rect.top + rect.height / 2),
                                            width: Math.round(rect.width),
                                            height: Math.round(rect.height),
                                            isVisible: isVisible,
                                            title: title,
                                            url: url
                                        };
                                
                                        allElements.push(elementInfo);
                                    });
                                
                                    // 添加viewport和设备信息到返回结果
                                    return {
                                        elements: allElements
                                    };
                            })();
                        """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val wrapType: Type =
                Types.newParameterizedType(ElementWrap::class.java, ElementWrap::class.java)
            val jsonAdapter = moshi.adapter<ElementWrap>(wrapType)
            try {
                jsonAdapter.fromJson(result)?.let {
                    callback(it.elements)
                }
            } catch (e: Exception) {
                Log.e(TAG, "JSON 解析失败", e)
            }
        }
    }

    /**
     * 分步点击元素：先滑动使其可见，再点击
     * @param elementId 元素标识符
     * @param callback 操作结果回调
     */
    fun clickElementWithSteps(elementInfo: ElementInfo, callback: (String) -> Unit) {
        Log.d(TAG, "开始分步点击元素: $elementInfo")

        // 第一步：获取元素初始位置信息
        getElementPosition(elementInfo) { positionResult ->
            if (positionResult.startsWith("ERROR")) {
                callback("第一步失败: $positionResult")
                return@getElementPosition
            }

            Log.d(TAG, "第一步完成 - 获取元素位置: $positionResult")

            // 解析位置信息
            parseElementPosition(positionResult) { element ->
                if (element == null) {
                    callback("ERROR: 无法解析元素位置信息")
                    return@parseElementPosition
                }

                // 第二步：检查元素是否在视口内，如果不在则滑动使其可见
                if (!isElementInViewport(element)) {
                    Log.d(TAG, "第二步 - 元素不在视口内，开始滑动使其可见")
                    scrollToMakeElementVisible(element) { scrollResult ->
                        Log.d(TAG, "滑动结果: $scrollResult")

                        // 第三步：等待页面稳定并重新获取元素位置
                        waitForPageStable {
                            Log.d(TAG, "第三步 - 页面稳定后重新获取元素位置")
                            performFinalClick(elementInfo, callback)
                        }
                    }
                } else {
                    Log.d(TAG, "元素已在视口内，直接进行点击")
                    // 元素已可见，直接点击
                    performFinalClick(elementInfo, callback)
                }
            }
        }
    }

    /**
     * 获取元素位置信息
     * 通过元素id和title来匹配，如果title为空，则根据id匹配到的第一个元素
     */
    private fun getElementPosition(elementInfo: ElementInfo, callback: (String) -> Unit) {
        val javascript = """
                        (function() {
                            try {
                                var element = null;
                                var identifier = '${elementInfo.identifier}';
                                var targetTitle = '${elementInfo.title}';
                                var searchMethod = '';
                                var candidates = [];
                                
                                // 第一步：收集所有可能的候选元素
                                
                                // 1. 通过ID查找
                                if (identifier && !identifier.startsWith('.') && !identifier.startsWith('#')) {
                                    var idElement = document.getElementById(identifier);
                                    if (idElement) {
                                        candidates.push({element: idElement, method: 'getElementById'});
                                    }
                                }
                                
                                // 2. 通过querySelector查找
                                if (identifier) {
                                    try {
                                        var elements = document.querySelectorAll(identifier);
                                        for (var i = 0; i < elements.length; i++) {
                                            candidates.push({element: elements[i], method: 'querySelector'});
                                        }
                                    } catch (e) {}
                                }
                                
                                // 3. 通过className查找
                                if (identifier.startsWith('.')) {
                                    var className = identifier.substring(1);
                                    var elements = document.getElementsByClassName(className);
                                    for (var i = 0; i < elements.length; i++) {
                                        candidates.push({element: elements[i], method: 'getElementsByClassName'});
                                    }
                                }
                                
                                // 4. 通过属性选择器查找
                                var possibleSelectors = [
                                    '[data-id="' + identifier + '"]',
                                    '[data-game-id="' + identifier + '"]',
                                    '[id*="' + identifier + '"]',
                                    '[class*="' + identifier + '"]'
                                ];
                                
                                for (var i = 0; i < possibleSelectors.length; i++) {
                                    try {
                                        var elements = document.querySelectorAll(possibleSelectors[i]);
                                        for (var j = 0; j < elements.length; j++) {
                                            candidates.push({
                                                element: elements[j], 
                                                method: 'attribute selector: ' + possibleSelectors[i]
                                            });
                                        }
                                    } catch (e) {
                                        continue;
                                    }
                                }
                                
                                // 第二步：根据title进行筛选和匹配
                                if (candidates.length === 0) {
                                    return 'ERROR: 未找到标识符为 "' + identifier + '" 的元素';
                                }
                                
                                // 去重候选元素（同一个元素可能通过多种方式找到）
                                var uniqueCandidates = [];
                                var processedElements = new Set();
                                
                                for (var i = 0; i < candidates.length; i++) {
                                    if (!processedElements.has(candidates[i].element)) {
                                        processedElements.add(candidates[i].element);
                                        uniqueCandidates.push(candidates[i]);
                                    }
                                }
                                
                                // 如果title为空，返回第一个找到的元素
                                if (!targetTitle || targetTitle.trim() === '') {
                                    element = uniqueCandidates[0].element;
                                    searchMethod = uniqueCandidates[0].method;
                                } else {
                                    // 如果title不为空，查找匹配title的元素
                                    for (var i = 0; i < uniqueCandidates.length; i++) {
                                        var candidate = uniqueCandidates[i].element;
                                        var elementTitle = candidate.title || candidate.getAttribute('title') || '';
                                        
                                        // 精确匹配title
                                        if (elementTitle === targetTitle) {
                                            element = candidate;
                                            searchMethod = uniqueCandidates[i].method + ' (title matched exactly)';
                                            break;
                                        }
                                    }
                                    
                                    // 如果没有精确匹配，尝试模糊匹配
                                    if (!element) {
                                        for (var i = 0; i < uniqueCandidates.length; i++) {
                                            var candidate = uniqueCandidates[i].element;
                                            var elementTitle = candidate.title || candidate.getAttribute('title') || '';
                                            
                                            // 模糊匹配title（包含关系）
                                            if (elementTitle.toLowerCase().includes(targetTitle.toLowerCase()) ||
                                                targetTitle.toLowerCase().includes(elementTitle.toLowerCase())) {
                                                element = candidate;
                                                searchMethod = uniqueCandidates[i].method + ' (title matched partially)';
                                                break;
                                            }
                                        }
                                    }
                                    
                                    // 如果仍然没有匹配到，返回第一个元素作为fallback
                                    if (!element) {
                                        element = uniqueCandidates[0].element;
                                        searchMethod = uniqueCandidates[0].method + ' (fallback: title not matched)';
                                    }
                                }
                                
                                // 获取设备像素比和viewport信息
                                var devicePixelRatio = window.devicePixelRatio || 1;
                                var viewportWidth = window.innerWidth;
                                var viewportHeight = window.innerHeight;
                                                
                                var rect = element.getBoundingClientRect();
                                var style = window.getComputedStyle(element);
                                
                                // 检查元素基本可见性（不考虑视口位置）
                                var isVisible = rect.width > 0 && rect.height > 0 &&
                                              window.getComputedStyle(element).visibility !== 'hidden' &&
                                              window.getComputedStyle(element).display !== 'none' &&
                                              !(rect.bottom < 0 || rect.top > window.innerHeight ||
                                                rect.right < 0 || rect.left > window.innerWidth);
                                                 
                                // 获取元素的title属性
                                var elementTitle = element.title || element.getAttribute('title') || '';    
                                
                                // 检查元素是否可点击和是否有URL
                                var url = '';
                                
                                // 检查是否是链接元素
                                if (element.tagName.toLowerCase() === 'a' && element.href) {
                                    url = element.href;
                                }
                                
                                // 检查是否有data-href属性
                                if (element.hasAttribute('data-href')) {
                                    url = element.getAttribute('data-href') || '';
                                }
                                
                                return {
                                        identifier: identifier,
                                        searchMethod: searchMethod,
                                        // 物理像素坐标
                                        x: Math.round(rect.left + rect.width / 2),
                                        y: Math.round(rect.top + rect.height / 2),
                                        width: Math.round(rect.width),
                                        height: Math.round(rect.height),
                                        isVisible: isVisible,
                                        title: elementTitle,
                                        targetTitle: targetTitle,
                                        candidatesFound: uniqueCandidates.length,
                                        url: url,
                                        };
                            } catch (e) {
                                return 'ERROR: ' + e.message;
                            }
                        })();
                        """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            mainHandler.post {
                callback(result)
            }
        }
    }

    /**
     * 解析元素位置信息
     */
    private fun parseElementPosition(positionResult: String, callback: (ElementInfo?) -> Unit) {
        try {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val wrapType: Type =
                Types.newParameterizedType(ElementInfo::class.java, ElementInfo::class.java)
            val jsonAdapter = moshi.adapter<ElementInfo>(wrapType)
            val element = jsonAdapter.fromJson(positionResult)
            callback(element)
        } catch (e: Exception) {
            Log.e(TAG, "解析元素位置失败", e)
            callback(null)
        }
    }

    /**
     * 检查元素是否在视口内
     */
    private fun isElementInViewport(element: ElementInfo): Boolean {
        // 检查元素是否在视口内（至少部分可见）
        return element.isVisible
    }

    /**
     * 滑动页面使元素可见
     */
    private fun scrollToMakeElementVisible(element: ElementInfo, callback: (String) -> Unit) {
        val viewportWidth = webView.width
        val viewportHeight = webView.height

        // 计算需要滑动的距离
        val centerX = viewportWidth / 2f
        val centerY = viewportHeight / 2f

        // 计算元素应该移动到的目标位置（屏幕中心）
        val deltaX = centerX - element.x
        val deltaY = centerY - element.y

        // 限制滑动距离，避免过度滑动
        val maxScrollDistance = viewportWidth.coerceAtMost(viewportHeight) * 0.8f
        val scrollX = (-maxScrollDistance).coerceAtLeast(maxScrollDistance.coerceAtMost(deltaX))
        val scrollY = (-maxScrollDistance).coerceAtLeast(maxScrollDistance.coerceAtMost(deltaY))

        // 执行滑动操作
        val fromX = centerX
        val fromY = centerY
        val toX = centerX + scrollX
        val toY = centerY + scrollY

        Log.d(TAG, "滑动操作: 从($fromX, $fromY) 到 ($toX, $toY)")

        simulateSwipe(fromX, fromY, toX, toY, 600, callback)
    }

    /**
     * 等待页面稳定（处理延迟加载）
     */
    private fun waitForPageStable(callback: () -> Unit) {
        var checkCount = 0
        val maxChecks = 10
        val checkInterval = 300L

        fun checkStability() {
            checkCount++
            if (checkCount >= maxChecks) {
                Log.d(TAG, "达到最大检查次数，认为页面已稳定")
                callback()
                return
            }

            // 检查页面是否还在加载
            val javascript = """
                (function() {
                    return {
                        readyState: document.readyState,
                        loading: document.readyState !== 'complete',
                        hasImages: document.images.length > 0,
                        imagesLoaded: Array.from(document.images).every(img => img.complete)
                    };
                })();
            """.trimIndent()

            webView.evaluateJavascript(javascript) { result ->
                mainHandler.postDelayed({
                    Log.d(TAG, "页面稳定性检查 $checkCount/$maxChecks: $result")

                    // 简单等待策略：等待几次检查后认为稳定
                    if (checkCount >= 3) {
                        callback()
                    } else {
                        checkStability()
                    }
                }, checkInterval)
            }
        }

        checkStability()
    }

    /**
     * 执行最终的点击操作（带重试机制）
     */
    private fun performFinalClick(elementInfo: ElementInfo, callback: (String) -> Unit) {
        performClickWithRetry(elementInfo, 0, callback)
    }

    /**
     * 带重试机制的点击操作
     */
    private fun performClickWithRetry(
        elementInfo: ElementInfo,
        attemptCount: Int,
        callback: (String) -> Unit
    ) {
        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
            callback("ERROR: 达到最大重试次数($MAX_RETRY_ATTEMPTS)，点击失败")
            return
        }

        Log.d(TAG, "尝试点击元素 (第${attemptCount + 1}次): $elementInfo")

        // 重新获取元素最新位置
        getElementPosition(elementInfo) { positionResult ->
            if (positionResult.startsWith("ERROR")) {
                // 等待后重试
                mainHandler.postDelayed({
                    performClickWithRetry(elementInfo, attemptCount + 1, callback)
                }, RETRY_DELAY)
                return@getElementPosition
            }

            // 执行位置点击
            simulateNativeTouchEvent(
                elementInfo.x.toFloat(),
                elementInfo.y.toFloat()
            ) { clickResult ->
                if (clickResult.startsWith("SUCCESS")) {
                    callback("SUCCESS: 分步点击完成 - $clickResult")
                } else {
                    // 点击失败，等待后重试
                    Log.d(TAG, "点击失败，准备重试: $clickResult")
                    mainHandler.postDelayed({
                        performClickWithRetry(elementInfo, attemptCount + 1, callback)
                    }, RETRY_DELAY)
                }
            }
        }
    }

    /**
     * 等待元素加载完成
     */
    private fun waitForElement(elementId: String, callback: (Boolean) -> Unit) {
        val javascript = """
            (function() {
                var attempts = 0;
                var maxAttempts = 50; // 5秒超时
                
                function checkElement() {
                    var element = document.getElementById('$elementId');
                    if (element && element.offsetParent !== null) {
                        return true;
                    }
                    
                    attempts++;
                    if (attempts < maxAttempts) {
                        setTimeout(checkElement, 100);
                        return false;
                    }
                    return false;
                }
                
                return checkElement();
            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            callback(result == "true")
        }
    }

    /**
     * 获取网页中所有的URL
     */
    fun getAllUrls(callback: (List<ElementUrl>) -> Unit) {
        val javascript = """
        (function() {
            var urls = new Set(); // 使用Set避免重复URL
            
            // 查找所有带href属性的链接元素
            var links = document.querySelectorAll('a[href]');
            links.forEach(function(link) {
                var url = link.href;
                if (url && url.trim() !== '' && !url.startsWith('javascript:')) {
                    // 标准化URL，去除hash部分
                    try {
                        var urlObj = new URL(url, window.location.href);
                        urls.add(urlObj.href);
                    } catch (e) {
                        // 如果URL解析失败，添加原始URL
                        urls.add(url);
                    }
                }
            });

            // 查找所有带data-href属性的元素
            var dataLinks = document.querySelectorAll('[data-href]');
            dataLinks.forEach(function(link) {
                var url = link.getAttribute('data-href');
                if (url && url.trim() !== '' && !url.startsWith('javascript:')) {
                    try {
                        var urlObj = new URL(url, window.location.href);
                        urls.add(urlObj.href);
                    } catch (e) {
                        urls.add(url);
                    }
                }
            });

            // 查找所有带onclick属性且包含location.href的元素
            var onclickElements = document.querySelectorAll('[onclick*="location.href"]');
            onclickElements.forEach(function(element) {
                var onclick = element.getAttribute('onclick');
                // 简单提取href后的URL（基本匹配）
                var match = onclick.match(/location\.href\s*=\s*["'](.*?)["']/);
                if (match && match[1]) {
                    var url = match[1];
                    if (url && url.trim() !== '' && !url.startsWith('javascript:')) {
                        try {
                            var urlObj = new URL(url, window.location.href);
                            urls.add(urlObj.href);
                        } catch (e) {
                            urls.add(url);
                        }
                    }
                }
            });
            
            // 查找所有资源URL
            // 图片资源
            var images = document.querySelectorAll('img[src]');
            images.forEach(function(img) {
                var url = img.src;
                if (url && url.trim() !== '') {
                    try {
                        var urlObj = new URL(url, window.location.href);
                        urls.add(urlObj.href);
                    } catch (e) {
                        urls.add(url);
                    }
                }
            });
            
            // 脚本资源
            var scripts = document.querySelectorAll('script[src]');
            scripts.forEach(function(script) {
                var url = script.src;
                if (url && url.trim() !== '') {
                    try {
                        var urlObj = new URL(url, window.location.href);
                        urls.add(urlObj.href);
                    } catch (e) {
                        urls.add(url);
                    }
                }
            });
            
            // 样式表资源
            var stylesheets = document.querySelectorAll('link[rel="stylesheet"][href]');
            stylesheets.forEach(function(stylesheet) {
                var url = stylesheet.href;
                if (url && url.trim() !== '') {
                    try {
                        var urlObj = new URL(url, window.location.href);
                        urls.add(urlObj.href);
                    } catch (e) {
                        urls.add(url);
                    }
                }
            });
            
            // 视频和音频资源
            var media = document.querySelectorAll('video[src], audio[src], source[src]');
            media.forEach(function(element) {
                var url = element.src;
                if (url && url.trim() !== '') {
                    try {
                        var urlObj = new URL(url, window.location.href);
                        urls.add(urlObj.href);
                    } catch (e) {
                        urls.add(url);
                    }
                }
            });
            
            // 其他嵌入资源
            var embeds = document.querySelectorAll('embed[src], object[data]');
            embeds.forEach(function(element) {
                var url = element.src || element.data;
                if (url && url.trim() !== '') {
                    try {
                        var urlObj = new URL(url, window.location.href);
                        urls.add(urlObj.href);
                    } catch (e) {
                        urls.add(url);
                    }
                }
            });
            
            // 背景图片URL
            var elementsWithBgImage = document.querySelectorAll('*');
            elementsWithBgImage.forEach(function(element) {
                var style = window.getComputedStyle(element);
                var bgImage = style.backgroundImage;
                if (bgImage && bgImage !== 'none') {
                    // 提取url("...")或url('...')中的URL
                    var match = bgImage.match(/url\(['"]?(.*?)['"]?\)/);
                    if (match && match[1]) {
                        var url = match[1];
                        if (url && url.trim() !== '') {
                            try {
                                var urlObj = new URL(url, window.location.href);
                                urls.add(urlObj.href);
                            } catch (e) {
                                urls.add(url);
                            }
                        }
                    }
                }
            });
            
            // iframe内的URL
            var iframes = document.querySelectorAll('iframe[src]');
            iframes.forEach(function(iframe) {
                // 添加iframe自身的src
                var iframeSrc = iframe.src;
                if (iframeSrc && iframeSrc.trim() !== '') {
                    try {
                        var urlObj = new URL(iframeSrc, window.location.href);
                        urls.add(urlObj.href);
                    } catch (e) {
                        urls.add(iframeSrc);
                    }
                }
                
                // 尝试获取iframe内部的URL（受同源策略限制）
                try {
                    if (iframe.contentDocument) {
                        // 获取iframe内部的链接
                        var iframeLinks = iframe.contentDocument.querySelectorAll('a[href]');
                        iframeLinks.forEach(function(link) {
                            var url = link.href;
                            if (url && url.trim() !== '' && !url.startsWith('javascript:')) {
                                try {
                                    var urlObj = new URL(url, iframe.src || window.location.href);
                                    urls.add(urlObj.href);
                                } catch (e) {
                                    urls.add(url);
                                }
                            }
                        });
                        
                        // 获取iframe内部的资源URL
                        var iframeResources = iframe.contentDocument.querySelectorAll('img[src], script[src], link[href], video[src], audio[src], source[src], embed[src], object[data]');
                        iframeResources.forEach(function(resource) {
                            var resourceUrl = resource.src || resource.href || resource.data;
                            if (resourceUrl && resourceUrl.trim() !== '') {
                                try {
                                    var urlObj = new URL(resourceUrl, iframe.src || window.location.href);
                                    urls.add(urlObj.href);
                                } catch (e) {
                                    urls.add(resourceUrl);
                                }
                            }
                        });
                    }
                } catch (e) {
                    // 忽略跨域错误
                    console.log('无法访问iframe内容（可能是跨域限制）:', e);
                }
            });
            
            // meta标签中的URL
            var metas = document.querySelectorAll('meta[content]');
            metas.forEach(function(meta) {
                // 检查常见包含URL的meta标签
                var name = meta.getAttribute('name');
                var property = meta.getAttribute('property');
                var httpEquiv = meta.getAttribute('http-equiv');
                
                // 只处理可能包含URL的meta标签
                if (['refresh', 'og:image', 'og:url', 'twitter:image', 'msapplication-TileImage', 'thumbnail'].includes(name) ||
                    ['refresh', 'og:image', 'og:url', 'twitter:image'].includes(property) ||
                    httpEquiv === 'refresh') {
                    
                    var content = meta.getAttribute('content');
                    if (content && content.trim() !== '') {
                        // 对于refresh标签，提取URL部分
                        if (name === 'refresh' || httpEquiv === 'refresh') {
                            var match = content.match(/URL=([^\s]+)/i);
                            if (match && match[1]) {
                                content = match[1];
                            }
                        }
                        
                        // 尝试解析URL
                        if (content.match(/^https?:\/\//i) || content.match(/^\//)) {
                            try {
                                var urlObj = new URL(content, window.location.href);
                                urls.add(urlObj.href);
                            } catch (e) {
                                urls.add(content);
                            }
                        }
                    }
                }
            });
            
            // 其他可能包含URL的属性
            var elementsWithUrlAttrs = document.querySelectorAll('[longdesc], [usemap], [formaction], [action], [manifest], [poster]');
            elementsWithUrlAttrs.forEach(function(element) {
                var urlAttrs = ['longdesc', 'usemap', 'formaction', 'action', 'manifest', 'poster'];
                urlAttrs.forEach(function(attr) {
                    if (element.hasAttribute(attr)) {
                        var url = element.getAttribute(attr);
                        if (url && url.trim() !== '' && (url.match(/^https?:\/\//i) || url.match(/^\//))) {
                            try {
                                var urlObj = new URL(url, window.location.href);
                                urls.add(urlObj.href);
                            } catch (e) {
                                urls.add(url);
                            }
                        }
                    }
                });
            });
            
            // 查找data-*属性中的URL
            var allElements = document.querySelectorAll('*');
            allElements.forEach(function(element) {
                // 获取所有data-*属性
                var attributes = element.attributes;
                for (var i = 0; i < attributes.length; i++) {
                    var attr = attributes[i];
                    if (attr.name.startsWith('data-') && attr.value) {
                        // 检查属性值是否可能是URL
                        var value = attr.value.trim();
                        if (value && (value.match(/^https?:\/\//i) || value.match(/^\//))) {
                            try {
                                var urlObj = new URL(value, window.location.href);
                                urls.add(urlObj.href);
                            } catch (e) {
                                // 如果不是有效URL，忽略
                            }
                        }
                    }
                }
                
                // 检查内联样式中的URL
                if (element.hasAttribute('style')) {
                    var styleValue = element.getAttribute('style');
                    if (styleValue) {
                        // 查找样式中的url()函数
                        var urlMatches = styleValue.match(/url\(['"]?(.*?)['"]?\)/g);
                        if (urlMatches) {
                            urlMatches.forEach(function(match) {
                                // 提取URL部分
                                var urlMatch = match.match(/url\(['"]?(.*?)['"]?\)/);
                                if (urlMatch && urlMatch[1]) {
                                    var url = urlMatch[1].trim();
                                    if (url) {
                                        try {
                                            var urlObj = new URL(url, window.location.href);
                                            urls.add(urlObj.href);
                                        } catch (e) {
                                            urls.add(url);
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            });

            return Array.from(urls);
        })();    
    """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            mainHandler.post {
                try {
                    val urls = mutableListOf<ElementUrl>()
                    // 处理可能的null或undefined结果
                    if (result != null && result != "null" && result != "undefined") {
                        val jsonArray = JSONArray(result)
                        for (i in 0 until jsonArray.length()) {
                            val url = jsonArray.getString(i)
                            if (url.isNotEmpty()) {
                                urls.add(ElementUrl(url))
                            }
                        }
                    }
                    callback(urls)
                } catch (e: Exception) {
                    Log.e(TAG, "获取网页URL失败", e)
                    callback(emptyList())
                }
            }
        }
    }


    /**
     * 测试元素是否存在（调试用）
     */
    fun testElementExists(elementIdentifier: String, callback: (String) -> Unit) {
        val javascript = """
            (function() {
                var identifier = '$elementIdentifier';
                var results = [];
                var foundElement = null;
                
                // 尝试各种查找方法并记录结果
                try {
                     // 方法3: getElementsByClassName (如果是类选择器)
                    if (identifier.startsWith('.')) {
                        var className = identifier.substring(1);
                        var byClass = document.getElementsByClassName(className);
                        results.push('getElementsByClassName: ' + (byClass.length > 0 ? '找到' + byClass.length + '个' : '未找到'));
                        if (byClass.length > 0 && !foundElement) foundElement = byClass[0];
                    }
                    
                    // 方法1: getElementById
                    var byId = document.getElementById(identifier);
                    results.push('getElementById: ' + (byId ? '找到' : '未找到'));
                    if (byId && !foundElement) foundElement = byId;
                    
                    // 方法2: querySelector
                    var byQuery = null;
                    try {
                        byQuery = document.querySelector(identifier);
                        results.push('querySelector: ' + (byQuery ? '找到' : '未找到'));
                        if (byQuery && !foundElement) foundElement = byQuery;
                    } catch (e) {
                        results.push('querySelector: 语法错误');
                    }
                    
                    // 方法4: 模糊匹配
                    var fuzzySelectors = [
                        '[id*="' + identifier + '"]',
                        '[class*="' + identifier + '"]',
                        '[data-id="' + identifier + '"]',
                        '[data-game-id="' + identifier + '"]'
                    ];
                    
                    fuzzySelectors.forEach(function(selector) {
                        try {
                            var elements = document.querySelectorAll(selector);
                            if (elements.length > 0) {
                                results.push(selector + ': 找到' + elements.length + '个');
                                if (!foundElement) foundElement = elements[0];
                            }
                        } catch (e) {}
                    });
                    
                    // 如果找到了元素，检查其可见性
                    if (foundElement) {
                        var rect = foundElement.getBoundingClientRect();
                        var style = window.getComputedStyle(foundElement);
                        
                        results.push('元素尺寸: ' + Math.round(rect.width) + 'x' + Math.round(rect.height));
                        results.push('CSS visibility: ' + style.visibility);
                        results.push('CSS display: ' + style.display);
                        results.push('位置: 左=' + Math.round(rect.left) + ', 上=' + Math.round(rect.top));
                        results.push('视口内: ' + !(rect.bottom < 0 || rect.top > window.innerHeight || 
                                                rect.right < 0 || rect.left > window.innerWidth));
                        
                        // 添加CSS定义的尺寸信息
                        var cssWidth = style.width;
                        var cssHeight = style.height;
                        results.push('CSS定义尺寸: width=' + cssWidth + ' height=' + cssHeight);
                        
                        // 添加综合可见性判断
                        var isVisible = rect.width > 0 && rect.height > 0 && 
                                      style.visibility !== 'hidden' &&
                                      style.display !== 'none' &&
                                      !(rect.bottom < 0 || rect.top > window.innerHeight ||
                                        rect.right < 0 || rect.left > window.innerWidth);
                        results.push('综合可见性判断: ' + (isVisible ? '可见' : '不可见'));
                    }
                    
                } catch (e) {
                    results.push('检测出错: ' + e.message);
                }
                
                return results.join('; ');
            })();
        """.trimIndent()
        Log.d("WebViewManager", "testElementExists: $elementIdentifier")
        webView.evaluateJavascript(javascript) { result ->
            Log.d("WebViewManager", "testElementExists: $result")
            mainHandler.post {
                callback(result?.replace("\"", "") ?: "检测失败")
            }
        }
    }

    /**
     * 方式1：通过JavaScript API直接触发点击事件（针对游戏网站优化）
     */
    fun clickElementByApi(elementId: String, callback: (String) -> Unit) {

        val javascript = """
            (function() {
                try {
                    var element = null;
                    var identifier = '$elementId';
                    var searchMethod = '';
                    
                    // 方法1: 尝试通过ID查找
                    if (identifier && !identifier.startsWith('.') && !identifier.startsWith('#')) {
                        element = document.getElementById(identifier);
                        if (element) searchMethod = 'getElementById';
                    }
                    
                    // 方法2: 如果ID查找失败，尝试作为选择器查找
                    if (!element) {
                        try {
                            var elements = document.querySelectorAll(identifier);
                            if (elements.length > 0) {
                                element = elements[0]; // 取第一个匹配的元素
                                searchMethod = 'querySelector';
                            }
                        } catch (e) {
                            // 选择器语法错误，继续其他方法
                        }
                    }
                    
                    // 方法3: 尝试通过类名查找
                    if (!element && identifier.startsWith('.')) {
                        var className = identifier.substring(1);
                        var elements = document.getElementsByClassName(className);
                        if (elements.length > 0) {
                            element = elements[0];
                            searchMethod = 'getElementsByClassName';
                        }
                    }
                    
                    // 方法4: 尝试通过属性查找
                    if (!element) {
                        var possibleSelectors = [
                            '[data-id="' + identifier + '"]',
                            '[data-game-id="' + identifier + '"]',
                            '[id*="' + identifier + '"]',
                            '[class*="' + identifier + '"]'
                        ];
                        
                        for (var i = 0; i < possibleSelectors.length; i++) {
                            try {
                                var elements = document.querySelectorAll(possibleSelectors[i]);
                                if (elements.length > 0) {
                                    element = elements[0];
                                    searchMethod = 'attribute selector: ' + possibleSelectors[i];
                                    break;
                                }
                            } catch (e) {
                                continue;
                            }
                        }
                    }
                    
                    if (!element) {
                        return 'ERROR: 未找到标识符为 "' + identifier + '" 的元素，已尝试所有查找方法';
                    }
                    
                    // 检查元素是否可见和可点击
                    var rect = element.getBoundingClientRect();
                    var isVisible = rect.width > 0 && rect.height > 0 &&
                                  window.getComputedStyle(element).visibility !== 'hidden' &&
                                  window.getComputedStyle(element).display !== 'none' &&
                                  // 检查元素是否在视口内或部分在视口内
                                  !(rect.bottom < 0 || rect.top > window.innerHeight ||
                                    rect.right < 0 || rect.left > window.innerWidth);
                    
                    if (!isVisible) {
                        return 'ERROR: 元素不可见 (找到方法: ' + searchMethod + ')';
                    }
                    
                    // 执行点击操作
                    var clickSuccess = false;
                    
                    // 触发事件序列
                    var events = [
                        { type: 'mouseenter', delay: 0 },
                        { type: 'mouseover', delay: 10 },
                        { type: 'mousedown', delay: 20 },
                        { type: 'touchstart', delay: 25 },
                        { type: 'touchend', delay: 50 },
                        { type: 'mouseup', delay: 60 },
                        { type: 'click', delay: 70 }
                    ];
                    
                    var eventCount = 0;
                    
                    events.forEach(function(eventInfo) {
                        setTimeout(function() {
                            try {
                                var event;
                                if (eventInfo.type.startsWith('touch')) {
                                    event = new TouchEvent(eventInfo.type, {
                                        bubbles: true,
                                        cancelable: true,
                                        touches: eventInfo.type === 'touchend' ? [] : [{
                                            clientX: rect.left + rect.width / 2,
                                            clientY: rect.top + rect.height / 2
                                        }]
                                    });
                                } else {
                                    event = new MouseEvent(eventInfo.type, {
                                        bubbles: true,
                                        cancelable: true,
                                        view: window,
                                        clientX: rect.left + rect.width / 2,
                                        clientY: rect.top + rect.height / 2
                                    });
                                }
                                element.dispatchEvent(event);
                                eventCount++;
                            } catch (e) {
                                console.log('事件触发失败:', e);
                            }
                        }, eventInfo.delay);
                    });
                    
                    // 额外的直接点击
                    setTimeout(function() {
                        try {
                            element.click();
                            element.focus();
                            clickSuccess = true;
                        } catch (e) {
                            console.log('直接点击失败:', e);
                        }
                    }, 100);
                    
                    return 'SUCCESS: 通过 ' + searchMethod + ' 找到并点击元素 - ' + 
                           element.tagName + (element.className ? '.' + element.className : '') +
                           (element.id ? '#' + element.id : '');
                           
                } catch (e) {
                    return 'ERROR: 执行失败 - ' + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            mainHandler.post {
                callback(result?.replace("\"", "") ?: "执行失败")
            }
        }
    }


    /**
     * 使用Android原生触摸事件模拟点击WebView
     */
    fun simulateNativeTouchEvent(x: Float, y: Float, callback: (String) -> Unit) {
        gestureSimulator.click(x, y, callback = callback)
    }


    /**
     * 模拟滑动操作
     *
     * @param startX 起始点X坐标
     * @param startY 起始点Y坐标
     * @param endX 结束点X坐标
     * @param endY 结束点Y坐标
     * @param duration 滑动持续时间（毫秒）
     * @param callback 操作结果回调
     */
    fun simulateSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 3000,
        callback: (String) -> Unit
    ) {
        gestureSimulator.swipe(startX, startY, endX, endY, duration, callback)
    }


    fun reload() {
        this.webView.reload()
    }

    fun loadUrl(url: String) {
        this.webView.loadUrl(url)
    }
}