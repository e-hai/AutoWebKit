package com.kit.autoweb.ui.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceInfo(
  val  devicePixelRatio: Float,
  val  viewportWidth: Float,
  val  viewportHeight: Float,
  val  screenWidth: Float,
  val  screenHeight: Float
)
