package com.kit.autoweb.core

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebView
import com.kit.autoweb.ui.model.Appointment
import com.kit.autoweb.ui.model.ElementInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONArray
import java.lang.reflect.Type

class WebViewManager(private val webView: WebView) {

    companion object {
        const val TAG = "WebViewManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())


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
    fun getAllUrls(callback: (List<String>) -> Unit) {
        val javascript = """
        (function() {
            var urls = [];
            var links = document.querySelectorAll('a[href]');
            
            links.forEach(function(link) {
                var url = link.href;
                if (url) {
                    urls.push(url);
                }
            });

            return JSON.stringify(urls);
        })();    
    """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            mainHandler.post {
                try {
                    val urls = mutableListOf<String>()
                    val jsonArray = JSONArray(result)
                    for (i in 0 until jsonArray.length()) {
                        urls.add(jsonArray.getString(i))
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
     * 方式2：获取元素位置，然后模拟坐标点击
     */
    fun clickElementByPosition(elementId: String, callback: (String) -> Unit) {
        val javascript = """
            (function() {
                try {
                    var element = null;
                    var identifier = '$elementId';
                    var searchMethod = '';
                    
                    // 使用与API点击相同的查找逻辑
                    if (identifier && !identifier.startsWith('.') && !identifier.startsWith('#')) {
                        element = document.getElementById(identifier);
                        if (element) searchMethod = 'getElementById';
                    }
                    
                    if (!element) {
                        try {
                            var elements = document.querySelectorAll(identifier);
                            if (elements.length > 0) {
                                element = elements[0];
                                searchMethod = 'querySelector';
                            }
                        } catch (e) {}
                    }
                    
                    if (!element && identifier.startsWith('.')) {
                        var className = identifier.substring(1);
                        var elements = document.getElementsByClassName(className);
                        if (elements.length > 0) {
                            element = elements[0];
                            searchMethod = 'getElementsByClassName';
                        }
                    }
                    
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
                        return JSON.stringify({
                            success: false,
                            error: '未找到标识符为 "' + identifier + '" 的元素',
                            searchMethod: '已尝试所有查找方法'
                        });
                    }
                    
                    var rect = element.getBoundingClientRect();
                    var centerX = rect.left + rect.width / 2;
                    var centerY = rect.top + rect.height / 2;
                    
                    // 检查元素是否可见
                    var isVisible = rect.width > 0 && rect.height > 0 &&
                                  window.getComputedStyle(element).visibility !== 'hidden' &&
                                  window.getComputedStyle(element).display !== 'none' &&
                                  // 检查元素是否在视口内或部分在视口内
                                  !(rect.bottom < 0 || rect.top > window.innerHeight ||
                                    rect.right < 0 || rect.left > window.innerWidth);
                    
                    if (!isVisible) {
                        return JSON.stringify({
                            success: false,
                            error: '元素不可见',
                            searchMethod: searchMethod
                        });
                    }
                    
                    return JSON.stringify({
                        success: true,
                        x: centerX,
                        y: centerY,
                        width: rect.width,
                        height: rect.height,
                        searchMethod: searchMethod,
                        elementInfo: element.tagName + (element.className ? '.' + element.className : '') + (element.id ? '#' + element.id : '')
                    });
                } catch (e) {
                    return JSON.stringify({
                        success: false,
                        error: e.message
                    });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            try {
                val cleanResult = result?.replace("\"", "")?.replace("\\", "") ?: ""
                Log.d("WebViewManager", "Element position result: $cleanResult")
                if (cleanResult.contains("success:true")) {
                    // 解析坐标
                    val xMatch = Regex("x:(\\d+\\.?\\d*)").find(cleanResult)
                    val yMatch = Regex("y:(\\d+\\.?\\d*)").find(cleanResult)

                    Log.d("WebViewManager", "Element coordinates: $xMatch, $yMatch")
                    if (xMatch != null && yMatch != null) {
                        val x = xMatch.groupValues[1].toFloat()
                        val y = yMatch.groupValues[1].toFloat()

                        // 执行原生触摸事件模拟点击，而不是通过JavaScript
                        simulateNativeTouchEvent(x, y) { clickResult ->
                            mainHandler.post {
                                Log.d("WebViewManager", "Click result: $clickResult")
                                callback("SUCCESS: 在位置($x, $y)执行原生点击 - $clickResult")
                            }
                        }
                    } else {
                        mainHandler.post {
                            callback("ERROR: 无法解析元素坐标")
                        }
                    }
                } else {
                    mainHandler.post {
                        callback("ERROR: $cleanResult")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback("ERROR: 解析结果失败 - ${e.message}")
                }
            }
        }
    }

    /**
     * 使用Android原生触摸事件模拟点击WebView
     */
    fun simulateNativeTouchEvent(x: Float, y: Float, callback: (String) -> Unit) {
        try {
            // 获取当前系统时间作为事件时间
            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()

            // 创建按下事件
            val downEvent = MotionEvent.obtain(
                downTime, eventTime,
                MotionEvent.ACTION_DOWN,
                x, y, 0
            )

            // 创建抬起事件
            val upEvent = MotionEvent.obtain(
                downTime, eventTime + 100, // 抬起时间稍晚于按下时间
                MotionEvent.ACTION_UP,
                x, y, 0
            )

            // 分发触摸事件到WebView
            mainHandler.post {
                webView.dispatchTouchEvent(downEvent)
                webView.dispatchTouchEvent(upEvent)

                // 回收MotionEvent对象，避免内存泄漏
                downEvent.recycle()
                upEvent.recycle()

                callback("在WebView位置($x, $y)执行了原生触摸事件")
            }
        } catch (e: Exception) {
            callback("模拟原生触摸事件失败: ${e.message}")
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
     * 获取网页内所有元素及其位置信息
     */
    fun getAllElements(callback: (List<ElementInfo>) -> Unit) {
        Log.d("WebViewManager", "getAllElements")
        val javascript = """
        (function() {
            var allElements = [];

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

                // 收集元素的基本信息
                var elementInfo = {
                    identifier: identifier,
                    x: Math.round(rect.left + rect.width / 2),
                    y: Math.round(rect.top + rect.height / 2),
                    width: Math.round(rect.width),
                    height: Math.round(rect.height),
                    isVisible: isVisible
                };

                allElements.push(elementInfo);
            });

            return JSON.stringify(allElements);
        })();
    """.trimIndent()

        webView.evaluateJavascript(javascript) { result ->
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val listType: Type =
                Types.newParameterizedType(List::class.java, ElementInfo::class.java)
            val jsonAdapter = moshi.adapter<List<ElementInfo>>(listType)
            try {
                // 去除外层引号并替换转义字符
                val cleanResult = result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""

                val elements = mutableListOf<ElementInfo>()
                jsonAdapter.fromJson(cleanResult)?.forEach { element ->
                    Log.d(
                        TAG,
                        "元素: ${element.identifier}, 可见: ${element.isVisible}, 位置: (${element.x}, ${element.y})"
                    )
                    elements.add(element)
                }
                callback(elements)
            } catch (e: Exception) {
                Log.e(TAG, "JSON 解析失败", e)
            }
        }
    }


    fun reload() {
        this.webView.reload()
    }
}