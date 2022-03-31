rootProject.name = "zipline-root"

include(":zipline")
include(":zipline:testing")
include(":zipline-cli")
include(":zipline-gradle-plugin")
include(":zipline-kotlin-plugin")
include(":zipline-kotlin-plugin-hosted")
include(":zipline-kotlin-plugin:tests")
include(":zipline-bytecode")
include(":zipline-loader")
include(":zipline-loader-testing")
include(":zipline-profiler")

include(":samples:emoji-search")
include(":samples:emoji-search:presenters")
include(":samples:octane")
include(":samples:trivia:trivia-host")
include(":samples:trivia:trivia-js")
include(":samples:trivia:trivia-shared")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
