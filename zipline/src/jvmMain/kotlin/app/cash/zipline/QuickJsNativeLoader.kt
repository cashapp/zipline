/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline

import java.io.File
import java.io.IOException
import java.util.Locale.US

@Suppress("UnsafeDynamicallyLoadedCode") // Only loading from our own JAR contents.
internal actual fun loadNativeLibrary() {
  val osName = System.getProperty("os.name").lowercase(US)
  val osArch = System.getProperty("os.arch").lowercase(US)
  val nativeLibraryJarPath = if (osName.contains("linux")) {
    "/jni/linux_$osArch/libquickjs.so"
  } else if (osName.contains("mac")) {
    "/jni/macos_$osArch/libquickjs.dylib"
  } else if(osName.contains("windows")) {
    "/jni/windows_$osArch/quickjs.dll"
  } else {
    throw IllegalStateException("Unsupported OS: $osName")
  }

  val nativeLibraryUrl = QuickJs::class.java.getResource(nativeLibraryJarPath)
      ?: throw IllegalStateException("Unable to read $nativeLibraryJarPath from JAR")
  val nativeLibraryFile: File
  try {
    nativeLibraryFile = File.createTempFile("quickjs", null)

    // File-based deleteOnExit() uses a special internal shutdown hook that always runs last.
    nativeLibraryFile.deleteOnExit()
    nativeLibraryUrl.openStream().use { nativeLibrary ->
      nativeLibraryFile.outputStream().use { output ->
        nativeLibrary.copyTo(output)
      }
    }
  } catch (e: IOException) {
    throw RuntimeException("Unable to extract native library from JAR", e)
  }
  System.load(nativeLibraryFile.absolutePath)
}
