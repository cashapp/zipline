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
package app.cash.quickjs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Locale.US;

final class QuickJsNativeLoader {
  @SuppressWarnings("UnsafeDynamicallyLoadedCode") // Only loading from our own JAR contents.
  static void load() {
    String osName = System.getProperty("os.name").toLowerCase(US);

    String nativeLibraryJarPath;
    if (osName.contains("linux")) {
      nativeLibraryJarPath = "/libquickjs.so";
    } else if (osName.contains("mac")) {
      nativeLibraryJarPath = "/libquickjs.dylib";
    } else {
      throw new IllegalStateException("Unsupported OS: " + osName);
    }

    URL nativeLibraryUrl = QuickJsNativeLoader.class.getResource(nativeLibraryJarPath);
    if (nativeLibraryUrl == null) {
      throw new IllegalStateException("Unable to read " + nativeLibraryJarPath + " from JAR");
    }

    Path nativeLibraryFile;
    try {
      nativeLibraryFile = Files.createTempFile("quickjs", null);

      // File-based deleteOnExit() uses a special internal shutdown hook that always runs last.
      nativeLibraryFile.toFile().deleteOnExit();

      try (InputStream nativeLibrary = nativeLibraryUrl.openStream()) {
        Files.copy(nativeLibrary, nativeLibraryFile, REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to extract native library from JAR", e);
    }

    System.load(nativeLibraryFile.toAbsolutePath().toString());
  }
}
