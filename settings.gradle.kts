rootProject.name = "zipline-root"

include(":zipline")
include(":zipline-api-validator")
include(":zipline-bytecode")
include(":zipline-cli")
include(":zipline-gradle-plugin")
include(":zipline-kotlin-plugin")
include(":zipline-kotlin-plugin-tests")
include(":zipline-loader")
include(":zipline-loader-testing")
include(":zipline-profiler")
include(":zipline-testing")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
