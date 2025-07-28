package com.kit.autoweb.ui.model

/**
 * 页面滚动信息数据类
 */
data class PageScrollInfo(
    val scrollTop: Int = 0,
    val scrollLeft: Int = 0,
    val scrollWidth: Int = 0,
    val scrollHeight: Int = 0,
    val clientWidth: Int = 0,
    val clientHeight: Int = 0,
    val viewportWidth: Int = 0,
    val viewportHeight: Int = 0
)