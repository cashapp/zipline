package app.cash.zipline.internal

import app.cash.zipline.QuickJs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun QuickJs.collectModuleDependencies() {
  evaluate(COLLECT_DEPENDENCIES_DEFINE_JS, "collectDependencies.js")
}

fun QuickJs.getModuleDependencies(): List<String> {
  val dependenciesString = getGlobalThis(CURRENT_MODULE_DEPENDENCIES)
  val dependencies = Json.decodeFromString<List<String>>(
    dependenciesString
    // If define is never called, dependencies is returned as null
      ?: "[]"
  )
  return dependencies
}

fun QuickJs.getGlobalThis(key: String): String? {
  return evaluate("globalThis.$key", "getGlobalThis.js") as String?
}

fun QuickJs.getLog(): String? = getGlobalThis("log")

fun QuickJs.initModuleLoader() {
  evaluate(DEFINE_JS, "define.js")
}

fun QuickJs.loadJsModule(script: String, id: String) {
  evaluate("globalThis.$CURRENT_MODULE_ID = '$id';")
  evaluate(script, id)
  evaluate("delete globalThis.$CURRENT_MODULE_ID;")
}

fun QuickJs.loadJsModule(id: String, bytecode: ByteArray) {
  evaluate("globalThis.$CURRENT_MODULE_ID = '$id';")
  execute(bytecode)
  evaluate("delete globalThis.$CURRENT_MODULE_ID;")
}

fun QuickJs.runApplication(mainModuleId: String, mainFunction: String) {
  evaluate(
    script = "require('${mainModuleId}').$mainFunction()",
    fileName = "RunApplication.kt",
  )
}
