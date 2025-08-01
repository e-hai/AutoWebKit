package com.kit.autoweb.ui.model

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ElementWrap(val elements: List<ElementInfo>)

/**
 * html元素信息
 * **/
@JsonClass(generateAdapter = true)
data class ElementInfo(
    val identifier: String, //唯一标识
    val title: String,      //标题，长度不超过20个字符
    val x: Int,             //中心的x坐标
    val y: Int,             //中心的y坐标
    val width: Int,         //宽度
    val height: Int,        //高度
    val isVisible: Boolean, //是否可见
)

@JsonClass(generateAdapter = true)
data class ElementUrl(
    val url: String,
)