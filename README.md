# API Search

IntelliJ IDEA 插件：在项目中搜索 Spring MVC / Spring Boot、JAX-RS、Feign、Spring Cloud Gateway 接口，并复制 URL、cURL、示例 JSON。

## 功能

### 悬浮搜索（`Alt+'`）

- 使用 IDEA 原生 `ChooseByNamePopup`（与 `Ctrl+N` 同款组件）
- 实时匹配 URL、方法名、类名、JavaDoc / KDoc、Swagger `@ApiOperation` / OpenAPI `@Operation`
- `Only this module` 复选框，仅搜索当前模块
- 底部 HTTP 方法筛选（GET / POST / PUT / DELETE / PATCH / HEAD / OPTIONS）
- 默认回填上次搜索并全选，直接输入即可覆盖
- `Alt+↑/↓` 浏览搜索历史
- 路径变量通配：输入 `.../delete/13241` 可命中 `.../delete/{contractId}`
- 结果列表最大展示行数可配置（默认 15）

### 右侧 REST Services 侧边栏

- 搜索框 + HTTP 方法筛选（带计数）+ 模块筛选（带计数）
- 计数随搜索条件实时变化，空白搜索且未筛选时显示全部
- 纯图标刷新按钮，只重新读取索引，不重建索引
- 列表列：Method、Path、Class#Method、Comment、Module
- 表头右键可勾选显示/隐藏列（会记住）
- 单元格右键 `Copy Cell`，单独复制注释、路径等
- 行右键：Jump to Source、Copy URL、Copy Full URL、Copy cURL、Filter by Module、Filter by Method、Clear Filters、Refresh

### 编辑器右键菜单

`SearchApi` → `Copy as URL` / `Copy as Full URL` / `Copy as cURL` / `Copy as JSON` / `Open in Browser`

### URL 与端口

- 完整 URL：`http://{host}:{port}{context-path}{path}`
- 端口解析优先级：
  1. 设置页「模块端口覆写」
  2. Run/Debug 配置中的 `SERVER_PORT` / `-Dserver.port=` / `--server.port=`
  3. 模块 `application*.yml/properties` 的 `server.port`
  4. 解析不到时复制前弹框手动输入并记住
- 支持 active profile 与 `${VAR:default}` 占位符
- cURL 单行、单引号，可直接导入 Postman

## 安装

1. 从 Releases 下载 `api-search-0.1.0.zip`
2. `Settings → Plugins → ⚙ → Install Plugin from Disk...`
3. 重启 IDEA

要求 **IntelliJ IDEA 2025.1+**。

## 快捷键 / IdeaVim

默认 `Alt+'`，可在 `Settings → Keymap` 中修改。

| Action ID | 说明 |
| --- | --- |
| `ApiSearch.SearchApi` | 打开搜索弹窗 |
| `ApiSearch.CopyPath` | Copy as URL |
| `ApiSearch.CopyUrl` | Copy as Full URL |
| `ApiSearch.CopyCurl` | Copy as cURL |
| `ApiSearch.CopyJson` | Copy as JSON |
| `ApiSearch.CopyOpenInBrowser` | Open in Browser |

IdeaVim 示例：

```vim
nnoremap <leader>a <Action>(ApiSearch.SearchApi)
```

或 Ex 命令：`:action ApiSearch.SearchApi`

## 设置

`Settings → Tools → API Search`

- 默认 Host（例如 `localhost` 或 `https://api.example.com`）
- 弹窗最大展示行数（默认 15，范围 5–50）
- 模块端口 / Context-Path 覆写表

## 构建

需要 JDK 21、Gradle 8.14+。

```bash
gradle buildPlugin
```

产物：`build/distributions/api-search-0.1.0.zip`

开发运行：

```bash
gradle runIde
```

首次构建会下载 IntelliJ IDEA Community 2025.1 平台 SDK。

## 许可证

MIT License，详见 [LICENSE](LICENSE)。

## 技术栈

- Kotlin 2.1
- Gradle IntelliJ Platform Plugin 2.x
- 自定义 `FileBasedIndex` 增量索引
- IDEA 原生 `ChooseByNamePopup`

## 已知限制

- 注解中的常量路径引用（如 `@RequestMapping(Constants.BASE)`）不会解析，回退为 `/`
- JAX-RS 的 `@ApplicationPath` 暂未参与路径拼接
- Gateway 仅解析 `spring.cloud.gateway.routes` 中的 `Path=` predicate
- Run/Debug 配置中的端口是声明值，不是进程实际监听端口；随机端口无法自动感知
