package app.cash.zipline.internal

actual val systemEpochMsClock: () -> Long = System::currentTimeMillis
