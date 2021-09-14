rootProject.name = "zipline-root"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        jcenter() // Warning: this repository is going to shut down soon
    }
}

include(":zipline")
include(":zipline:testing")
include(":zipline-kotlin-plugin")
include(":zipline-gradle-plugin")
include(":sample-emojisearch:app")
include(":sample-emojisearch:presenters")
include(":sample-octane")
