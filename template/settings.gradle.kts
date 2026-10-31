pluginManagement {
    val domaCompileVersion: String by settings
    plugins {
        id("org.domaframework.doma.compile") version domaCompileVersion
    }
}

rootProject.name = "bench-app"
