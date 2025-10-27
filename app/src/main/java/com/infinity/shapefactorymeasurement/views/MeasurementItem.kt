package com.infinity.roometric.views

import com.infinity.roometric.data.Measurement

sealed class MeasurementItem {
    data class Header(val title: String) : MeasurementItem()
    data class Data(val measurement: Measurement) : MeasurementItem()
}