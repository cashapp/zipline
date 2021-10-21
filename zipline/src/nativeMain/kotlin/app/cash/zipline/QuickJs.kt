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
import app.cash.zipline.quickjs.JSContext
import app.cash.zipline.quickjs.JSMemoryUsage
import app.cash.zipline.quickjs.JSRuntime
import app.cash.zipline.quickjs.JSValue
import app.cash.zipline.quickjs.JS_ComputeMemoryUsage
import app.cash.zipline.quickjs.JS_EVAL_FLAG_COMPILE_ONLY
import app.cash.zipline.quickjs.JS_Eval
import app.cash.zipline.quickjs.JS_EvalFunction
import app.cash.zipline.quickjs.JS_FreeContext
import app.cash.zipline.quickjs.JS_FreeRuntime
import app.cash.zipline.quickjs.JS_FreeValue
import app.cash.zipline.quickjs.JS_GetException
import app.cash.zipline.quickjs.JS_GetPropertyStr
import app.cash.zipline.quickjs.JS_GetPropertyUint32
import app.cash.zipline.quickjs.JS_IsArray
import app.cash.zipline.quickjs.JS_IsException
import app.cash.zipline.quickjs.JS_IsUndefined
import app.cash.zipline.quickjs.JS_NewContext
import app.cash.zipline.quickjs.JS_NewRuntime
import app.cash.zipline.quickjs.JS_READ_OBJ_BYTECODE
import app.cash.zipline.quickjs.JS_READ_OBJ_REFERENCE
import app.cash.zipline.quickjs.JS_ReadObject
import app.cash.zipline.quickjs.JS_ResolveModule
import app.cash.zipline.quickjs.JS_SetGCThreshold
import app.cash.zipline.quickjs.JS_SetInterruptHandler
import app.cash.zipline.quickjs.JS_SetMaxStackSize
import app.cash.zipline.quickjs.JS_SetMemoryLimit
import app.cash.zipline.quickjs.JS_TAG_BOOL
import app.cash.zipline.quickjs.JS_TAG_EXCEPTION
import app.cash.zipline.quickjs.JS_TAG_FLOAT64
import app.cash.zipline.quickjs.JS_TAG_INT
import app.cash.zipline.quickjs.JS_TAG_NULL
import app.cash.zipline.quickjs.JS_TAG_OBJECT
import app.cash.zipline.quickjs.JS_TAG_STRING
import app.cash.zipline.quickjs.JS_TAG_UNDEFINED
import app.cash.zipline.quickjs.JS_ToCString
import app.cash.zipline.quickjs.JS_WRITE_OBJ_BYTECODE
import app.cash.zipline.quickjs.JS_WRITE_OBJ_REFERENCE
import app.cash.zipline.quickjs.JS_WriteObject
import app.cash.zipline.quickjs.JsValueGetBool
import app.cash.zipline.quickjs.JsValueGetFloat64
import app.cash.zipline.quickjs.JsValueGetInt
import app.cash.zipline.quickjs.JsValueGetNormTag
import app.cash.zipline.quickjs.js_free
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value
import platform.posix.size_tVar

internal fun jsInterruptHandlerGlobal(runtime: CPointer<JSRuntime>?, opaque: COpaquePointer?): Int{
  val quickJs = opaque!!.asStableRef<QuickJs>().get()
  return quickJs.jsInterruptHandler(runtime)
}

actual class QuickJs private constructor(
  private val runtime: CPointer<JSRuntime>,
  private val context: CPointer<JSContext>
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

  internal fun jsInterruptHandler(runtime: CPointer<JSRuntime>?): Int {
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

  actual fun evaluate(script: String, fileName: String): Any? {
    val evalValue = JS_Eval(context, script, script.length.convert(), fileName, 0)
    val result = evalValue.toKotlinInstanceOrNull()
    JS_FreeValue(context, evalValue)
    return result
  }

  actual fun compile(sourceCode: String, fileName: String): ByteArray {
    val compiled =
      JS_Eval(context, sourceCode, sourceCode.length.convert(), fileName, JS_EVAL_FLAG_COMPILE_ONLY)
    if (JS_IsException(compiled) != 0) {
      throwJsException()
    }
    val result = memScoped {
      val bufferLengthVar = alloc<size_tVar>()
      val buffer = JS_WriteObject(context, bufferLengthVar.ptr, compiled,
        JS_WRITE_OBJ_BYTECODE or JS_WRITE_OBJ_REFERENCE
      )
      val bufferLength = bufferLengthVar.value.toInt()

      val result = if (buffer != null && bufferLength > 0) {
        buffer.readBytes(bufferLength)
      } else {
        null
      }

      JS_FreeValue(context, compiled)
      js_free(context, buffer)

      result
    }
    return result ?: throwJsException()
  }

  actual fun execute(bytecode: ByteArray): Any? {
    @Suppress("UNCHECKED_CAST") // ByteVar and UByteVar have the same bit layout.
    val bytecodeRef = bytecode.refTo(0) as CValuesRef<UByteVar>
    val obj = JS_ReadObject(context, bytecodeRef, bytecode.size.convert(),
      JS_READ_OBJ_BYTECODE or JS_READ_OBJ_REFERENCE
    )
    if (JS_IsException(obj) != 0) {
      throwJsException()
    }
    if (JS_ResolveModule(context, obj) != 0) {
      throw QuickJsException("Failed to resolve JS module")
    }
    val value = JS_EvalFunction(context, obj)
    if (JS_IsException(value) != 0) {
      JS_FreeValue(context, value)
      throwJsException()
    }
    val result = value.toKotlinInstanceOrNull()
    JS_FreeValue(context, value)
    return result
  }

  internal actual fun initOutboundChannel(outboundChannel: CallChannel) {
    TODO()
  }

  internal actual fun getInboundChannel(): CallChannel {
    TODO()
  }

  actual fun close() {
    JS_FreeContext(context)
    JS_FreeRuntime(runtime)
    thisPtr.dispose()
  }

  private fun throwJsException(): Nothing {
    val exceptionValue = JS_GetException(context)

    val messageValue = JS_GetPropertyStr(context, exceptionValue, "message")
    val stackValue = JS_GetPropertyStr(context, exceptionValue, "stack")

    val message = JS_ToCString(context,
      messageValue.takeUnless { JS_IsUndefined(messageValue) != 0 } ?: exceptionValue
    )?.toKStringFromUtf8() ?: ""
    JS_FreeValue(context, messageValue)

    val stack = JS_ToCString(context, stackValue)!!.toKStringFromUtf8()
    JS_FreeValue(context, stackValue)
    JS_FreeValue(context, exceptionValue)

    // TODO extract cause

    throw QuickJsException(message) // TODO add stack
  }

  private fun CValue<JSValue>.toKotlinInstanceOrNull(): Any? {
    return when (JsValueGetNormTag(this)) {
      JS_TAG_EXCEPTION -> throwJsException()
      JS_TAG_STRING -> JS_ToCString(context, this)!!.toKStringFromUtf8()
      JS_TAG_BOOL -> JsValueGetBool(this) != 0
      JS_TAG_INT -> JsValueGetInt(this)
      JS_TAG_FLOAT64 -> JsValueGetFloat64(this)
      JS_TAG_NULL, JS_TAG_UNDEFINED -> null
      JS_TAG_OBJECT -> {
        if (JS_IsArray(context, this) != 0) {
          val lengthProperty = JS_GetPropertyStr(context, this, "length")
          val length = JsValueGetInt(lengthProperty)
          JS_FreeValue(context, lengthProperty)

          Array(length) {
            val element = JS_GetPropertyUint32(context, this, it.convert())
            val value = element.toKotlinInstanceOrNull()
            JS_FreeValue(context, element)
            value
          }
        } else {
          null
        }
      }
      else -> null
    }
  }
}
