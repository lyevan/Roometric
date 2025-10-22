package com.infinity.shapefactorymeasurement.views

import com.infinity.shapefactorymeasurement.data.Measurement

sealed class MeasurementItem {
    data class Header(val title: String) : MeasurementItem()
    data class Data(val measurement: Measurement) : MeasurementItem()
}