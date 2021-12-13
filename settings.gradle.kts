rootProject.name = "zipline-root"

include(":samples:emoji-search")
include(":samples:emoji-search:presenters")
include(":samples:octane")

includeBuild("zipline-library") {
  // Allows the samples to use local build of Zipline Gradle Plugin and other published modules
  dependencySubstitution {
    substitute(module("app.cash.zipline:zipline"))
      .with(project(":zipline"))
    substitute(module("app.cash.zipline:zipline:testing"))
      .with(project(":zipline:testing"))
    substitute(module("app.cash.zipline:zipline-gradle-plugin"))
      .with(project(":zipline-gradle-plugin"))
    substitute(module("app.cash.zipline:zipline-kotlin-plugin"))
      .with(project(":zipline-kotlin-plugin"))
    substitute(module("app.cash.zipline:zipline-kotlin-plugin-hosted"))
      .with(project(":zipline-kotlin-plugin-hosted"))
    substitute(module("app.cash.zipline:zipline-kotlin-plugin:tests"))
      .with(project(":zipline-kotlin-plugin:tests"))
    substitute(module("app.cash.zipline:zipline-tools"))
      .with(project(":zipline-tools"))
    substitute(module("app.cash.zipline:zipline-bytecode"))
      .with(project(":zipline-bytecode"))
    substitute(module("app.cash.zipline:zipline-loader"))
      .with(project(":zipline-loader"))
  }
}
