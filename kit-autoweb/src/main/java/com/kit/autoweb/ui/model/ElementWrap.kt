package com.kit.autoweb.ui.model

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ElementWrap(val elements: List<ElementInfo>)