package com.kit.autoweb.ui.model

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class Appointment(val date: String, val status: String)
