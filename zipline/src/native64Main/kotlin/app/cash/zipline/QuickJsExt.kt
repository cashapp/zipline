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

import app.cash.zipline.quickjs.JSValue
import app.cash.zipline.quickjs.JS_EVAL_FLAG_COMPILE_ONLY
import app.cash.zipline.quickjs.JS_Eval
import app.cash.zipline.quickjs.JS_EvalFunction
import app.cash.zipline.quickjs.JS_FreeValue
import app.cash.zipline.quickjs.JS_GetException
import app.cash.zipline.quickjs.JS_GetPropertyStr
import app.cash.zipline.quickjs.JS_GetPropertyUint32
import app.cash.zipline.quickjs.JS_IsArray
import app.cash.zipline.quickjs.JS_IsException
import app.cash.zipline.quickjs.JS_IsUndefined
import app.cash.zipline.quickjs.JS_READ_OBJ_BYTECODE
import app.cash.zipline.quickjs.JS_READ_OBJ_REFERENCE
import app.cash.zipline.quickjs.JS_ReadObject
import app.cash.zipline.quickjs.JS_ResolveModule
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
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.value
import platform.posix.size_tVar

internal actual fun QuickJs.throwJsException(): Nothing {
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

internal actual fun QuickJs.compilePlatform(sourceCode: String, fileName: String): ByteArray{
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

internal actual fun QuickJs.executePlatform(bytecode: ByteArray): Any?{
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

  val result = value.toKotlinInstanceOrNull(this)
  JS_FreeValue(context, value)
  return result
}

internal actual fun QuickJs.evaluatePlatform(script: String, fileName: String): Any?{
  val evalValue = JS_Eval(context, script, script.length.convert(), fileName, 0)
  val result = evalValue.toKotlinInstanceOrNull(this)
  JS_FreeValue(context, evalValue)
  return result;
}

private fun CValue<JSValue>.toKotlinInstanceOrNull(quickJs:QuickJs): Any? {
  return when (JsValueGetNormTag(this)) {
    JS_TAG_EXCEPTION -> quickJs.throwJsException()
    JS_TAG_STRING -> JS_ToCString(quickJs.context, this)!!.toKStringFromUtf8()
    JS_TAG_BOOL -> JsValueGetBool(this) != 0
    JS_TAG_INT -> JsValueGetInt(this)
    JS_TAG_FLOAT64 -> JsValueGetFloat64(this)
    JS_TAG_NULL, JS_TAG_UNDEFINED -> null
    JS_TAG_OBJECT -> {
      if (JS_IsArray(quickJs.context, this) != 0) {
        val lengthProperty = JS_GetPropertyStr(quickJs.context, this, "length")
        val length = JsValueGetInt(lengthProperty)
        JS_FreeValue(quickJs.context, lengthProperty)

        Array(length) {
          val element = JS_GetPropertyUint32(quickJs.context, this, it.convert())
          val value = element.toKotlinInstanceOrNull(quickJs)
          JS_FreeValue(quickJs.context, element)
          value
        }
      } else {
        null
      }
    }
    else -> null
  }
}
