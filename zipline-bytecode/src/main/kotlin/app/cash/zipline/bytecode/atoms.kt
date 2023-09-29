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
package app.cash.zipline.bytecode

/**
 * Maps each commonly-used string to an integer ID for performance. Strings built into QuickJS
 * are assigned an ID statically; user-provided strings get an ID dynamically.
 *
 * When encoding an object, built-in strings are not encoded.
 */
interface AtomSet {
  val strings: List<JsString>
  fun get(id: Int): JsString
  fun idOf(value: JsString): Int
  fun toMutableAtomSet(): MutableAtomSet
}

class MutableAtomSet(
  strings: List<JsString>,
) : AtomSet {
  private val _strings = strings.toMutableList()
  private val stringToId = mutableMapOf<JsString, Int>()

  init {
    for ((index, string) in BUILT_IN_ATOMS.withIndex()) {
      stringToId[string] = index
    }
    for ((index, string) in strings.withIndex()) {
      stringToId[string] = index + BUILT_IN_ATOMS.size
    }
  }

  override val strings: List<JsString> = _strings

  override fun get(id: Int): JsString {
    return when {
      id < BUILT_IN_ATOMS.size -> BUILT_IN_ATOMS[id]
      else -> _strings[id - BUILT_IN_ATOMS.size]
    }
  }

  override fun idOf(value: JsString): Int {
    val result = stringToId[value]
    return result ?: throw IllegalArgumentException("not an atom: $value")
  }

  /** Returns true if [string] was added to this set. */
  fun add(string: JsString): Boolean {
    if (stringToId[string] != null) return false

    val newIndex = BUILT_IN_ATOMS.size + _strings.size
    _strings += string
    stringToId[string] = newIndex
    return true
  }

  fun add(string: String): Boolean = add(string.toJsString())

  override fun toMutableAtomSet(): MutableAtomSet = MutableAtomSet(_strings)
}

/** This is computed dynamically at QuickJS boot, and depends on build flags. */
private val BUILT_IN_ATOMS: List<JsString> = listOf(
  // JS_ATOM_NULL
  "\u0000",
  "null",
  "false",
  "true",
  "if",
  "else",
  "return",
  "var",
  "this",
  "delete",
  "void",
  "typeof",
  "new",
  "in",
  "instanceof",
  "do",
  "while",
  "for",
  "break",
  "continue",
  "switch",
  "case",
  "default",
  "throw",
  "try",
  "catch",
  "finally",
  "function",
  "debugger",
  "with",
  "class",
  "const",
  "enum",
  "export",
  "extends",
  "import",
  "super",
  "implements",
  "interface",
  "let",
  "package",
  "private",
  "protected",
  "public",
  "static",
  "yield",
  "await",
  "",
  "length",
  "fileName",
  "lineNumber",
  "message",
  "errors",
  "stack",
  "name",
  "toString",
  "toLocaleString",
  "valueOf",
  "eval",
  "prototype",
  "constructor",
  "configurable",
  "writable",
  "enumerable",
  "value",
  "get",
  "set",
  "of",
  "__proto__",
  "undefined",
  "number",
  "boolean",
  "string",
  "object",
  "symbol",
  "integer",
  "unknown",
  "arguments",
  "callee",
  "caller",
  "<eval>",
  "<ret>",
  "<var>",
  "<arg_var>",
  "<with>",
  "lastIndex",
  "target",
  "index",
  "input",
  "defineProperties",
  "apply",
  "join",
  "concat",
  "split",
  "construct",
  "getPrototypeOf",
  "setPrototypeOf",
  "isExtensible",
  "preventExtensions",
  "has",
  "deleteProperty",
  "defineProperty",
  "getOwnPropertyDescriptor",
  "ownKeys",
  "add",
  "done",
  "next",
  "values",
  "source",
  "flags",
  "global",
  "unicode",
  "raw",
  "new.target",
  "this.active_func",
  "<home_object>",
  "<computed_field>",
  "<static_computed_field>",
  "<class_fields_init>",
  "<brand>",
  "#constructor",
  "as",
  "from",
  "meta",
  "*default*",
  "*",
  "Module",
  "then",
  "resolve",
  "reject",
  "promise",
  "proxy",
  "revoke",
  "async",
  "exec",
  "groups",
  "status",
  "reason",
  "globalThis",
  "not-equal",
  "timed-out",
  "ok",
  "toJSON",
  "Object",
  "Array",
  "Error",
  "Number",
  "String",
  "Boolean",
  "Symbol",
  "Arguments",
  "Math",
  "JSON",
  "Date",
  "Function",
  "GeneratorFunction",
  "ForInIterator",
  "RegExp",
  "ArrayBuffer",
  "SharedArrayBuffer",
  "Uint8ClampedArray",
  "Int8Array",
  "Uint8Array",
  "Int16Array",
  "Uint16Array",
  "Int32Array",
  "Uint32Array",
  "Float32Array",
  "Float64Array",
  "DataView",
  "Map",
  "Set",
  "WeakMap",
  "WeakSet",
  "Map Iterator",
  "Set Iterator",
  "Array Iterator",
  "String Iterator",
  "RegExp String Iterator",
  "Generator",
  "Proxy",
  "Promise",
  "PromiseResolveFunction",
  "PromiseRejectFunction",
  "AsyncFunction",
  "AsyncFunctionResolve",
  "AsyncFunctionReject",
  "AsyncGeneratorFunction",
  "AsyncGenerator",
  "EvalError",
  "RangeError",
  "ReferenceError",
  "SyntaxError",
  "TypeError",
  "URIError",
  "InternalError",
  // Symbols:
  "<brand>",
  "Symbol.toPrimitive",
  "Symbol.iterator",
  "Symbol.match",
  "Symbol.matchAll",
  "Symbol.replace",
  "Symbol.search",
  "Symbol.split",
  "Symbol.toStringTag",
  "Symbol.isConcatSpreadable",
  "Symbol.hasInstance",
  "Symbol.species",
  "Symbol.unscopables",
  "Symbol.asyncIterator",
).map { it.toJsString() }
