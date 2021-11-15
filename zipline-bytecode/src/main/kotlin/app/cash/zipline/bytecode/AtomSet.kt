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
 * Maps each commonly-used string to an integer index for performance. Strings built into QuickJS
 * are assigned an index statically; user-provided strings get an index dynamically.
 *
 * When encoding an object, built-in strings are not encoded.
 */
class AtomSet(
  val strings: List<String>
) {
  private val stringToIndex = mutableMapOf<String, Int>()

  init {
    for ((index, string) in BUILT_IN_ATOMS.withIndex()) {
      stringToIndex[string] = index
    }
    for ((index, string) in strings.withIndex()) {
      stringToIndex[string] = index + BUILT_IN_ATOMS.size
    }
  }

  fun get(value: Int): String {
    return when {
      value < BUILT_IN_ATOMS.size -> BUILT_IN_ATOMS[value]
      else -> strings[value - BUILT_IN_ATOMS.size]
    }
  }

  fun indexOf(value: String): Int {
    val result = stringToIndex[value]
    return result ?: throw IllegalArgumentException("not an atom: $value")
  }
}

/** This is computed dynamically at QuickJS boot, and depends on build flags. */
private val BUILT_IN_ATOMS = listOf(
  "\u0000", // JS_ATOM_NULL
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
  "<brand>",  // Symbols
  "Symbol.toPrimitive",  // Symbols
  "Symbol.iterator",  // Symbols
  "Symbol.match",  // Symbols
  "Symbol.matchAll",  // Symbols
  "Symbol.replace",  // Symbols
  "Symbol.search",  // Symbols
  "Symbol.split",  // Symbols
  "Symbol.toStringTag",  // Symbols
  "Symbol.isConcatSpreadable",  // Symbols
  "Symbol.hasInstance",  // Symbols
  "Symbol.species",  // Symbols
  "Symbol.unscopables",  // Symbols
  "Symbol.asyncIterator",  // Symbols
)
