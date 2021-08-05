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

import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * TODO: support conversions for more typed array types?
 */
public final class JsTypedArraysTest {
  private QuickJs quickJs;

  @Before public void setUp() {
    quickJs = QuickJs.create();
  }

  @After public void tearDown() {
    quickJs.close();
  }

  @Test public void uint8ArrayConvertedToByteArray() {
    byte[] byteArray = (byte[]) quickJs.evaluate(""
        + "var array = new Uint8Array(5);"
        + "array[0] = 86;"
        + "array[1] = 7;"
        + "array[2] = 53;"
        + "array[3] = 0;"
        + "array[4] = 9;"
        + "array;");
    assertThat(byteArray).asList()
        .containsExactly((byte) 86, (byte) 7, (byte) 53, (byte) 0, (byte) 9);
  }

  @Test public void uint8ArraySliceConvertedToByteArray() {
    byte[] byteArray = (byte[]) quickJs.evaluate(""
        + "var array = new Uint8Array(5);"
        + "array[0] = 86;"
        + "array[1] = 7;"
        + "array[2] = 53;"
        + "array[3] = 0;"
        + "array[4] = 9;"
        + "array.slice(1, 3);");
    assertThat(byteArray).asList()
        .containsExactly((byte) 7, (byte) 53);
  }

  @Test public void int8ArrayConvertedToByteArray() {
    byte[] byteArray = (byte[]) quickJs.evaluate(""
        + "var array = new Int8Array(5);"
        + "array[0] = 86;"
        + "array[1] = 7;"
        + "array[2] = 53;"
        + "array[3] = 0;"
        + "array[4] = 9;"
        + "array;");
    assertThat(byteArray).asList()
        .containsExactly((byte) 86, (byte) 7, (byte) 53, (byte) 0, (byte) 9);
  }

  @Test public void uint8ClampedArrayConvertedToByteArray() {
    byte[] byteArray = (byte[]) quickJs.evaluate(""
        + "var array = new Uint8ClampedArray(5);"
        + "array[0] = 86;"
        + "array[1] = 7;"
        + "array[2] = 53;"
        + "array[3] = 0;"
        + "array[4] = 9;"
        + "array;");
    assertThat(byteArray).asList()
        .containsExactly((byte) 86, (byte) 7, (byte) 53, (byte) 0, (byte) 9);
  }

  interface ByteArrayTransformer {
    byte[] transform(byte[] input);
  }

  @Test public void byteArrayWithProxy() {
    quickJs.set("transformer", ByteArrayTransformer.class, new ByteArrayTransformer() {
      @Override public byte[] transform(byte[] input) {
        byte[] result = new byte[input.length + 2];
        result[0] = (byte) 254;
        result[1] = (byte) 255;
        System.arraycopy(input, 0, result, 2, input.length);
        Arrays.sort(result);
        return result;
      }
    });

    byte[] byteArray = (byte[]) quickJs.evaluate(""
        + "var array = new Uint8Array(5);"
        + "array[0] = 86;"
        + "array[1] = 7;"
        + "array[2] = 53;"
        + "array[3] = 0;"
        + "array[4] = 9;"
        + "transformer.transform(array);");
    assertThat(byteArray).asList().containsExactly(
        (byte) 0, (byte) 9, (byte) 7, (byte) 53, (byte) 86, (byte) 254, (byte) 255);
  }

  @Test public void byteArrayWithProxyReturnsNull() {
    quickJs.set("transformer", ByteArrayTransformer.class, new ByteArrayTransformer() {
      @Override public byte[] transform(byte[] input) {
        return null;
      }
    });

    byte[] byteArray = (byte[]) quickJs.evaluate(""
        + "var array = new Uint8Array(5);"
        + "array[0] = 86;"
        + "transformer.transform(array);");
    assertThat(byteArray).isNull();
  }

  @Test public void byteArrayWithProxyReceivesNull() {
    quickJs.set("transformer", ByteArrayTransformer.class, new ByteArrayTransformer() {
      @Override public byte[] transform(byte[] input) {
        return new byte[] { 86 };
      }
    });

    byte[] byteArray = (byte[]) quickJs.evaluate(""
        + "transformer.transform(null);");
    assertThat(byteArray).asList().containsExactly((byte) 86);
  }

  @Test public void javaByteArrayConvertedToJsUint8Array() {
    quickJs.set("transformer", ByteArrayTransformer.class, new ByteArrayTransformer() {
      @Override public byte[] transform(byte[] input) {
        return new byte[] { 86 };
      }
    });

    String constructorName = (String) quickJs.evaluate(""
        + "var array = new Uint8Array(5);"
        + "array[0] = 86;"
        + "var transformed = transformer.transform(array);"
        + "transformed.constructor.name");
    assertThat(constructorName).isEqualTo("Uint8Array");
  }

  @Test public void int16ArrayUnsupported() {
    short[] shortArray = (short[]) quickJs.evaluate(""
        + "var array = new Int16Array(1);"
        + "array[0] = 8675;"
        + "array;");
    assertThat(shortArray).isNull();
  }

  @Test public void uint16ArrayUnsupported() {
    short[] shortArray = (short[]) quickJs.evaluate(""
        + "var array = new Uint16Array(1);"
        + "array[0] = 8675;"
        + "array;");
    assertThat(shortArray).isNull();
  }

  @Test public void int32ArrayUnsupported() {
    int[] intArray = (int[]) quickJs.evaluate(""
        + "var array = new Int32Array(1);"
        + "array[0] = 8675;"
        + "array;");
    assertThat(intArray).isNull();
  }

  @Test public void uint32ArrayUnsupported() {
    int[] intArray = (int[]) quickJs.evaluate(""
        + "var array = new Uint32Array(1);"
        + "array[0] = 8675;"
        + "array;");
    assertThat(intArray).isNull();
  }

  @Test public void float32ArrayUnsupported() {
    float[] floatArray = (float[]) quickJs.evaluate(""
        + "var array = new Float32Array(1);"
        + "array[0] = 867.5309;"
        + "array;");
    assertThat(floatArray).isNull();
  }

  @Test public void float64ArrayUnsupported() {
    double[] doubleArray = (double[]) quickJs.evaluate(""
        + "var array = new Float64Array(1);"
        + "array[0] = 867.5309;"
        + "array;");
    assertThat(doubleArray).isNull();
  }
}
