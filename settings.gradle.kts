rootProject.name = "DarknesLordIPTV"

// Her alt klasörde build.gradle.kts varsa otomatik modül olarak eklenir.
rootDir.listFiles()
    ?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    ?.forEach { include(it.name) }
