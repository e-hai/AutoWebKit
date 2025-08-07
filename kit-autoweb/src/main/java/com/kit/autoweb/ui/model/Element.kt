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
    val x: Float,             //中心的x坐标，以px为单位
    val y: Float,             //中心的y坐标，以px为单位
    val width: Float,         //宽度，以px为单位
    val height: Float,        //高度，以px为单位
    val isVisible: Boolean, //是否可见
)

@JsonClass(generateAdapter = true)
data class ElementUrl(
    val url: String,
)