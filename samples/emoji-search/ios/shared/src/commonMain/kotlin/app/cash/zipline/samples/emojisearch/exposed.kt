package app.cash.zipline.samples.emojisearch

import okio.ByteString
import okio.toByteString
import platform.Foundation.NSData

@Suppress("unused", "UNUSED_PARAMETER") // Used to export types to Objective-C / Swift.
fun exposedTypes(
  emojiSearchZipline: EmojiSearchZipline,
  emojiSearchEvent: EmojiSearchEvent
) {
  throw AssertionError()
}

fun byteStringOf(data: NSData): ByteString = data.toByteString()
