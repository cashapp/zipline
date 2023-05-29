package app.cash.zipline.internal

import app.cash.zipline.QuickJs
import kotlinx.serialization.json.Json

internal fun QuickJs.collectModuleDependencies() {
  evaluate(COLLECT_DEPENDENCIES_DEFINE_JS, "collectDependencies.js")
}

internal fun QuickJs.getModuleDependencies(): List<String> {
  val dependenciesString = getGlobalThis(CURRENT_MODULE_DEPENDENCIES)
  val dependencies = Json.decodeFromString<List<String>>(
    dependenciesString
    // If define is never called, dependencies is returned as null
      ?: "[]",
  )
  return dependencies
}

internal fun QuickJs.getGlobalThis(key: String): String? {
  return evaluate("globalThis.$key", "getGlobalThis.js") as String?
}

internal fun getLog(quickJs: QuickJs): String? = quickJs.getGlobalThis("log")

internal fun QuickJs.initModuleLoader() {
  evaluate(DEFINE_JS, "define.js")
}

internal fun QuickJs.loadJsModule(script: String, id: String) {
  evaluate("globalThis.$CURRENT_MODULE_ID = '$id';")
  evaluate(script, id)
  evaluate("delete globalThis.$CURRENT_MODULE_ID;")
}

internal fun QuickJs.loadJsModule(id: String, bytecode: ByteArray) {
  evaluate("globalThis.$CURRENT_MODULE_ID = '$id';")
  execute(bytecode)
  evaluate("delete globalThis.$CURRENT_MODULE_ID;")
}

internal fun runApplication(quickJs: QuickJs, mainModuleId: String, mainFunction: String) {
  quickJs.evaluate(
    script = "require('$mainModuleId').$mainFunction()",
    fileName = "RunApplication.kt",
  )
}
