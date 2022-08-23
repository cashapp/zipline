package app.cash.zipline.internal

import kotlin.js.Date

actual val systemEpochMsClock: () -> Long = { Date().getMilliseconds().toLong() }
