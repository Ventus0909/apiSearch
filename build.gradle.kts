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
    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
            .orElse(providers.environmentVariable("PUBLISH_TOKEN"))
    }

    pluginConfiguration {
        id = "com.ventus.apisearch"
        name = "API Search"
        version = project.version.toString()

        description = """
            API Search is an IntelliJ IDEA plugin for searching Spring MVC / Spring Boot, JAX-RS, Feign and Spring Cloud Gateway endpoints. It supports fuzzy search by URL, method name and comments, a REST Services sidebar with module and HTTP method filters, and copy URL / cURL / JSON actions with automatic module port resolution.

            Usage:
            1. Press Alt+' (configurable in Keymap) to open the API search popup. Type a URL, method name or comment; use the "Only this module" checkbox and HTTP method filter to narrow results.
            2. Right-click a Controller method and choose SearchApi to copy URL, full URL, cURL or JSON.
            3. Open the "REST Services" tool window on the right for a browsable list with filters, cell copy and column configuration.
            4. Ports are resolved from Run/Debug configuration or the module's application.yml/properties; override them in Settings > Tools > API Search.

            中文说明：
            API Search 是一款 IntelliJ IDEA 插件，用于搜索 Spring MVC / Spring Boot、JAX-RS、Feign 与 Spring Cloud Gateway 接口。支持按 URL、方法名、注释模糊检索，右侧提供 REST Services 侧边栏（模块/HTTP 方法筛选、单元格复制、列配置），并可复制 URL、完整 URL、cURL、示例 JSON，端口按模块配置自动解析。

            使用方法：
            1. 按 Alt+'（可在 Keymap 修改）打开搜索弹窗，输入 URL、方法名或注释；可勾选 Only this module、选择 HTTP 方法筛选。
            2. 在 Controller 方法上右键 SearchApi，复制 URL / 完整 URL / cURL / JSON。
            3. 打开右侧 REST Services 侧边栏浏览、筛选、复制单元格。
            4. 端口优先读取 Run/Debug 配置，其次 application.yml/properties；可在 Settings > Tools > API Search 中覆写。
        """.trimIndent()

        changeNotes = """
            0.1.0
            - 初始版本：接口搜索、侧边栏筛选、复制 URL / Full URL / cURL / JSON
        """.trimIndent()

        vendor {
            name = "Ventus"
            url = "https://github.com/Ventus0909"
            email = "1020575774@qq.com"
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
