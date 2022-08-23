package app.cash.zipline.internal

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual val systemEpochMsClock: () -> Long =
  { (NSDate().timeIntervalSince1970() * 1000).toLong() }
