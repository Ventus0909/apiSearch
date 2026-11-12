plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.ventus"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.ventus.apisearch"
        name = "API Search"
        version = project.version.toString()

        description = """
            在 IDEA 中快速搜索 Spring MVC / Spring Boot、JAX-RS、Feign 与 Gateway 接口，
            支持按 URL、方法名、注释检索，并可复制 URL、cURL、示例 JSON。
        """.trimIndent()

        vendor {
            name = "Ventus"
        }

        ideaVersion {
            sinceBuild = "251"
        }
    }

    buildSearchableOptions = false
}

tasks {
    test {
        useJUnit()
    }

    runIde {
        // 使用本机已安装的 IDEA 2026.3 启动沙箱，避免重复下载完整 IDE
        // 若无本地安装，注释掉本段即可回退到自动下载 2025.1
        // ideDir.set(file("C:/Users/Wxkj/AppData/Local/Programs/IntelliJ IDEA Ultimate"))
    }
}
