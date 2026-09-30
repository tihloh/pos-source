package com.tihloh.pos.ui

import java.text.NumberFormat
import java.util.Locale

private val phpCurrency = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

fun money(cents: Long): String = phpCurrency.format(cents / 100.0)

fun quantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

fun parseMoneyToCents(value: String): Long? =
    value.trim().replace(",", "").toBigDecimalOrNull()
        ?.movePointRight(2)
        ?.toLong()
