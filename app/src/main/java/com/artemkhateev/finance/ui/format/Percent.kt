package com.artemkhateev.finance.ui.format

import kotlin.math.abs
import kotlin.math.roundToInt

/** Доля [part] от [whole] в десятых процента: 108 — это 10,8 %. null — считать не от чего. */
fun percentTenths(part: Long, whole: Long): Int? =
    if (whole == 0L) null else (part * 1000.0 / abs(whole)).roundToInt()

/** «+10.8%», «-0.5%»; без знака — «10.8%». */
fun percentText(tenths: Int, signed: Boolean = true): String {
    val sign = when {
        !signed -> ""
        tenths > 0 -> "+"
        tenths < 0 -> "-"
        else -> ""
    }
    val value = abs(tenths)
    return "$sign${value / 10}.${value % 10}%"
}
