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

import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.quickjs.JSClassDef
import app.cash.zipline.quickjs.JSClassIDVar
import app.cash.zipline.quickjs.JSContext
import app.cash.zipline.quickjs.JSMemoryUsage
import app.cash.zipline.quickjs.JSRuntime
import app.cash.zipline.quickjs.JS_ComputeMemoryUsage
import app.cash.zipline.quickjs.JS_FreeContext
import app.cash.zipline.quickjs.JS_FreeRuntime
import app.cash.zipline.quickjs.JS_NewClass
import app.cash.zipline.quickjs.JS_NewClassID
import app.cash.zipline.quickjs.JS_NewContext
import app.cash.zipline.quickjs.JS_NewRuntime
import app.cash.zipline.quickjs.JS_SetGCThreshold
import app.cash.zipline.quickjs.JS_SetInterruptHandler
import app.cash.zipline.quickjs.JS_SetMaxStackSize
import app.cash.zipline.quickjs.JS_SetMemoryLimit
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value

internal fun jsInterruptHandlerGlobal(runtime: CPointer<JSRuntime>?, opaque: COpaquePointer?): Int{
  val quickJs = opaque!!.asStableRef<QuickJs>().get()
  return quickJs.jsInterruptHandler(runtime)
}

actual class QuickJs private constructor(
  internal val runtime: CPointer<JSRuntime>,
  internal val context: CPointer<JSContext>
) {
  actual companion object {
    actual fun create(): QuickJs {
      val runtime = JS_NewRuntime() ?: throw OutOfMemoryError()
      val context = JS_NewContext(runtime)
      if (context == null) {
        JS_FreeRuntime(runtime)
        throw OutOfMemoryError()
      }
      return QuickJs(runtime, context)
        .apply {
          // Explicitly assign default values to these properties so the backing fields values
          // are consistent with their native fields. (QuickJS doesn't offer accessors for these.)
          // TODO extract this somewhere common to share with jniMain/
          memoryLimit = -1L
          gcThreshold = 256L * 1024L
          maxStackSize = 512L * 1024L // Override the QuickJS default which is 256 KiB
        }
    }

    actual val version: String
      get() = quickJsVersion
  }

  private val jsInterruptHandlerCFunction = staticCFunction(::jsInterruptHandlerGlobal)
  private val thisPtr = StableRef.create(this)
  init {
    JS_SetInterruptHandler(runtime, jsInterruptHandlerCFunction, thisPtr.asCPointer())
  }

  private var outboundCallChannelClassId = 0

  fun jsInterruptHandler(runtime: CPointer<JSRuntime>?): Int {
    val interruptHandler = interruptHandler ?: return 0

    JS_SetInterruptHandler(runtime, null, null) // Suppress re-enter.

    val result = try {
      interruptHandler.poll()
    } catch (t: Throwable) {
      // TODO: propagate the interrupt handler's exceptions through JS.
      true // Halt JS.
    } finally {
      // Restore handler.
      JS_SetInterruptHandler(runtime, jsInterruptHandlerCFunction, thisPtr.asCPointer())
    }

    return if (result) 1 else 0
  }

  actual var interruptHandler: InterruptHandler? = null

  /** Memory usage statistics for the JavaScript engine. */
  actual val memoryUsage: MemoryUsage
    get() {
      memScoped {
        val jsMemoryUsage = alloc<JSMemoryUsage>()
        JS_ComputeMemoryUsage(runtime, jsMemoryUsage.ptr)
        return MemoryUsage(
          jsMemoryUsage.malloc_count,
          jsMemoryUsage.malloc_size,
          jsMemoryUsage.malloc_limit,
          jsMemoryUsage.memory_used_count,
          jsMemoryUsage.memory_used_size,
          jsMemoryUsage.atom_count,
          jsMemoryUsage.atom_size,
          jsMemoryUsage.str_count,
          jsMemoryUsage.str_size,
          jsMemoryUsage.obj_count,
          jsMemoryUsage.obj_size,
          jsMemoryUsage.prop_count,
          jsMemoryUsage.prop_size,
          jsMemoryUsage.shape_count,
          jsMemoryUsage.shape_size,
          jsMemoryUsage.js_func_count,
          jsMemoryUsage.js_func_size,
          jsMemoryUsage.js_func_code_size,
          jsMemoryUsage.js_func_pc2line_count,
          jsMemoryUsage.js_func_pc2line_size,
          jsMemoryUsage.c_func_count,
          jsMemoryUsage.array_count,
          jsMemoryUsage.fast_array_count,
          jsMemoryUsage.fast_array_elements,
          jsMemoryUsage.binary_object_count,
          jsMemoryUsage.binary_object_size,
        )
      }
    }

  /** Default is -1. Use -1 for no limit. */
  actual var memoryLimit: Long = -1L
    set(value) {
      field = value
      JS_SetMemoryLimit(runtime, value.convert())
    }

  /** Default is 256 KiB. Use -1 to disable automatic GC. */
  actual var gcThreshold: Long = -1L
    set(value) {
      field = value
      JS_SetGCThreshold(runtime, value.convert())
    }

  /** Default is 512 KiB. Use 0 to disable the maximum stack size check. */
  actual var maxStackSize: Long = -1L
    set(value) {
      field = value
      JS_SetMaxStackSize(runtime, value.convert())
    }

  actual fun evaluate(script: String, fileName: String): Any? = evaluatePlatform(script, fileName)

  actual fun compile(sourceCode: String, fileName: String): ByteArray = compilePlatform(sourceCode, fileName)

  actual fun execute(bytecode: ByteArray): Any? = executePlatform(bytecode)

  internal actual fun initOutboundChannel(outboundChannel: CallChannel) {
    var outboundCallChannelClassId = outboundCallChannelClassId
    if (outboundCallChannelClassId == 0) {
      outboundCallChannelClassId = memScoped {
        val id = alloc<JSClassIDVar>()
        JS_NewClassID(id.ptr)

        val classDef = alloc<JSClassDef>()
        classDef.class_name = "OutboundCallChannel".cstr.ptr
        JS_NewClass(runtime, id.value, classDef.ptr)

        id.value.toInt() // Why doesn't JS_NewObjectClass accept a UInt / JSClassID?
      }
      this.outboundCallChannelClassId = outboundCallChannelClassId
    }

    initOutboundChannelPlatform(outboundChannel, outboundCallChannelClassId)
  }

  internal actual fun getInboundChannel() = getInboundChannelPlatform()

  actual fun close() {
    JS_FreeContext(context)
    JS_FreeRuntime(runtime)
    thisPtr.dispose()
  }
}

// cinterop produces slightly different code for 32 and 64 bit architectures.
internal expect fun QuickJs.throwJsException(): Nothing
internal expect fun QuickJs.compilePlatform(sourceCode: String, fileName: String): ByteArray
internal expect fun QuickJs.executePlatform(bytecode: ByteArray): Any?
internal expect fun QuickJs.evaluatePlatform(script: String, fileName: String): Any?
internal expect fun QuickJs.getInboundChannelPlatform(): CallChannel
internal expect fun QuickJs.initOutboundChannelPlatform(
  outboundChannel: CallChannel,
  outboundCallChannelClassId: Int,
)
