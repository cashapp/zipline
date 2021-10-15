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
import app.cash.zipline.internal.bridge.inboundChannelName
import app.cash.zipline.internal.bridge.outboundChannelName
import app.cash.zipline.quickjs.JSContext
import app.cash.zipline.quickjs.JSValue
import app.cash.zipline.quickjs.JSValueVar
import app.cash.zipline.quickjs.JS_EVAL_FLAG_COMPILE_ONLY
import app.cash.zipline.quickjs.JS_Eval
import app.cash.zipline.quickjs.JS_EvalFunction
import app.cash.zipline.quickjs.JS_FreeAtom
import app.cash.zipline.quickjs.JS_FreeValue
import app.cash.zipline.quickjs.JS_GetException
import app.cash.zipline.quickjs.JS_GetGlobalObject
import app.cash.zipline.quickjs.JS_GetPropertyStr
import app.cash.zipline.quickjs.JS_HasProperty
import app.cash.zipline.quickjs.JS_Invoke
import app.cash.zipline.quickjs.JS_IsException
import app.cash.zipline.quickjs.JS_IsUndefined
import app.cash.zipline.quickjs.JS_NewArray
import app.cash.zipline.quickjs.JS_NewAtom
import app.cash.zipline.quickjs.JS_NewObjectClass
import app.cash.zipline.quickjs.JS_NewString
import app.cash.zipline.quickjs.JS_READ_OBJ_BYTECODE
import app.cash.zipline.quickjs.JS_READ_OBJ_REFERENCE
import app.cash.zipline.quickjs.JS_ReadObject
import app.cash.zipline.quickjs.JS_ResolveModule
import app.cash.zipline.quickjs.JS_SetOpaque
import app.cash.zipline.quickjs.JS_SetProperty
import app.cash.zipline.quickjs.JS_SetPropertyUint32
import app.cash.zipline.quickjs.JS_TAG_BOOL
import app.cash.zipline.quickjs.JS_TAG_EXCEPTION
import app.cash.zipline.quickjs.JS_TAG_FLOAT64
import app.cash.zipline.quickjs.JS_TAG_INT
import app.cash.zipline.quickjs.JS_TAG_NULL
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
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.set
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

private fun JSValue.toKotlinInstanceOrNull(quickJs:QuickJs): Any? {
  return when (JsValueGetNormTag(this)) {
    JS_TAG_EXCEPTION -> quickJs.throwJsException()
    JS_TAG_STRING -> JS_ToCString(quickJs.context, this)!!.toKStringFromUtf8()
    JS_TAG_BOOL -> JsValueGetBool(this) != 0
    JS_TAG_INT -> JsValueGetInt(this)
    JS_TAG_FLOAT64 -> JsValueGetFloat64(this)
    JS_TAG_NULL, JS_TAG_UNDEFINED -> null
    else -> null
  }
}

private fun QuickJs.toJsArray(strings: Array<String>): JSValue {
  val array = JS_NewArray(context)
  strings.forEachIndexed { index, string ->
    JS_SetPropertyUint32(context, array, index.convert(), JS_NewString(context, string))
  }
  return array
}

internal actual fun QuickJs.getInboundChannelPlatform(): CallChannel {
  val globalThis = JS_GetGlobalObject(context)
  val inboundChannel = JS_GetPropertyStr(context, globalThis, inboundChannelName)
  JS_FreeValue(context, globalThis)

  return InboundCallChannel(this, context, inboundChannel)
}

private class InboundCallChannel(
  private val quickJs: QuickJs,
  private val context: CPointer<JSContext>,
  private val inboundChannel: JSValue,
) : CallChannel {
  override fun serviceNamesArray(): Array<String> {
    val property = JS_NewAtom(context, "serviceNamesArray")
    val jsResult = JS_Invoke(context, inboundChannel, property, 0, null)
    val kotlinResult = jsResult.toKotlinInstanceOrNull(quickJs) as Array<String>

    JS_FreeAtom(context, property)
    JS_FreeValue(context, jsResult)

    return kotlinResult
  }

  override fun invoke(
    instanceName: String,
    funName: String,
    encodedArguments: Array<String>,
  ): Array<String> {
    val property = JS_NewAtom(context, "invoke")
    val arg0 = JS_NewString(context, instanceName)
    val arg1 = JS_NewString(context, funName)
    val arg2 = quickJs.toJsArray(encodedArguments)

    val jsResult = memScoped {
      val args = allocArray<JSValueVar>(3)
      args[0] = arg0
      args[1] = arg1
      args[2] = arg2

      JS_Invoke(context, inboundChannel, property, 3, args)
    }
    val kotlinResult = jsResult.toKotlinInstanceOrNull(quickJs) as Array<String>

    JS_FreeAtom(context, property)
    JS_FreeValue(context, arg0)
    JS_FreeValue(context, arg1)
    JS_FreeValue(context, arg2) // TODO are we leaking strings in the array?
    JS_FreeValue(context, jsResult)

    return kotlinResult
  }

  override fun invokeSuspending(
    instanceName: String,
    funName: String,
    encodedArguments: Array<String>,
    callbackName: String,
  ) {
    val property = JS_NewAtom(context, "invokeSuspending")
    val arg0 = JS_NewString(context, instanceName)
    val arg1 = JS_NewString(context, funName)
    val arg2 = quickJs.toJsArray(encodedArguments)
    val arg3 = JS_NewString(context, callbackName)

    val jsResult = memScoped {
      val args = allocArray<JSValueVar>(4)
      args[0] = arg0
      args[1] = arg1
      args[2] = arg2
      args[3] = arg3

      JS_Invoke(context, inboundChannel, property, 4, args)
    }

    JS_FreeAtom(context, property)
    JS_FreeValue(context, arg0)
    JS_FreeValue(context, arg1)
    JS_FreeValue(context, arg2) // TODO are we leaking strings in the array?
    JS_FreeValue(context, arg3)
    JS_FreeValue(context, jsResult)
  }

  override fun disconnect(instanceName: String): Boolean {
    val property = JS_NewAtom(context, "disconnect")
    val arg0 = JS_NewString(context, instanceName)

    val jsResult = memScoped {
      val args = allocArray<JSValueVar>(4)
      args[0] = arg0

      JS_Invoke(context, inboundChannel, property, 1, args)
    }
    val kotlinResult = jsResult.toKotlinInstanceOrNull(quickJs) as Boolean

    JS_FreeAtom(context, property)
    JS_FreeValue(context, jsResult)

    return kotlinResult
  }
}

internal actual fun QuickJs.initOutboundChannelPlatform(
  outboundChannel: CallChannel,
  outboundCallChannelClassId: Int,
) {
  val globalThis = JS_GetGlobalObject(context)
  try {
    val propertyName = JS_NewAtom(context, outboundChannelName)
    try {
      require(JS_HasProperty(context, globalThis, propertyName) == 0) {
        "A global object called $outboundChannelName already exists"
      }

      val jsOutboundCallChannel = JS_NewObjectClass(context, outboundCallChannelClassId)
      if (JS_IsException(jsOutboundCallChannel) != 0 ||
        JS_SetProperty(context, globalThis, propertyName, jsOutboundCallChannel) != 0) {
        throwJsException()
      }

      // TODO build function list entries
      JS_SetOpaque(jsOutboundCallChannel, TODO("function list"))
    } finally {
      JS_FreeAtom(context, propertyName)
    }
  } finally {
    JS_FreeValue(context, globalThis)
  }
}
