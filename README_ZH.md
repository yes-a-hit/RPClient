<div align="center">
  <img src="app/src/main/res/drawable/app_logo.png" alt="RPClient Logo" width="112" />

  # RPClient Unoffical Version(RPClient UV)

  简体中文 | [English](README.md)

 KafuuNeko/RPClient 的非官方定制分支

  一个面向 Android 的本地优先 AI 角色扮演聊天客户端。

  支持角色卡、世界书、单人对话、群聊、长期摘要记忆、Prompt 检查与多种 LLM 接口。

  <p>
    <a href="https://play.google.com/store/apps/details?id=me.kafuuneko.rpclient">
      <img src="https://play.google.com/intl/en_us/badges/images/generic/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="70" />
    </a>
    <a href="https://f-droid.org/packages/me.kafuuneko.rpclient/">
      <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-zh-cn.png" alt="Get it on F-Droid" height="80" />
    </a>
  </p>

  [定制版改动](#定制版改动) · [项目简介](#项目简介) · [界面展示](#界面展示) · [功能特性](#功能特性) · [快速开始](#快速开始) · [参与贡献](#参与贡献)
</div>

## 定制版改动

本仓库是 [RPClient](https://github.com/KafuuNeko/RPClient) 的个人定制分支，基线为上游 `2026.3.1`（提交 `c3ed325`）。除下列改动外，其余功能与上游保持一致。

### 应用名称与包名

- 应用显示名改为 `RPClient UV`（UV 取 Unoffical Version 之意），`app_name` 与关于页的 `about_brand_name` 同步修改，debug 构建不再追加 `(Dev)` 字样。
- `applicationId` 改为 `me.kafuuneko.rpclient.uv`，debug 构建为 `me.kafuuneko.rpclient.uv.dev`。与官方版 `me.kafuuneko.rpclient` 完全隔离，可同时安装、互不覆盖。
- 包名变更后不继承旧版应用内数据，从官方版或早期定制版升级时需重新导入角色卡、世界书与模型配置。
- 代码包名 `namespace` 保持 `me.kafuuneko.rpclient` 不变，源码目录结构未调整。

### 关于页

- 新增「定制版」分组：显示改版作者 `yes a hit`，并提供「定制版说明」导出入口。
- 说明文档内置在 `assets/README_UV.md`，导出走系统文件选择器（`ACTION_CREATE_DOCUMENT`），不需要存储权限，也不依赖应用私有目录可见性。

### 本地小说导入

新建故事页增加「导入本地小说」，与故事编辑器里既有的单文件导入互补：

- **整本自动分章**：`NovelChapterParser` 识别 `第X章/回/节/篇/卷/话`、`Chapter N`、Markdown `#` 标题以及 `序章`、`楔子`、`番外` 等固定词，按标题切段。
- **散装多文件**：一次可多选，按文件名自然排序合并（`chapter2` 排在 `chapter10` 之前），单文件没有标题时用文件名兜底。
- **不误判正文**：标题行长度上限 40 字符，正文里提到「第三章」的长句不会被当成标题。
- **编码兜底**：按 UTF-8 读取，出现大量替换字符时回退 GB18030，避免中文 TXT 乱码。
- **接入续写**：导入后按原样建立章节骨架并写入正文，直接在故事编辑器里续写、生成摘要与使用世界书。
- 导入模式与角色、世界书选择共存，创建时一并关联。

### 默认语言

- 默认资源集 `res/values/strings.xml` 整体替换为简体中文，因此系统语言不在支持列表内时（含英文系统）界面仍显示中文。
- 其余 12 个语言目录保持上游内容，对应语言的系统仍会显示各自翻译。
- 搬运时逐条校验过格式化占位符，可与中文翻译对齐的 1263 条全部一致；XML 良构性与条目数量（1265 条 `string`、3 个 `plurals`）均已校验。

### 助手

首页快捷区新增「助手」入口，进入后是一个独立会话页面，与角色聊天、群聊、故事创作并列，不占用角色卡与会话数据。

- **独立会话**：消息保存在 `filesDir/assistant/session.json`，单会话、可一键清空；不写入 Room，因此不涉及数据库迁移。
- **沙箱权限**：助手只能访问 `filesDir/assistant/sandbox` 目录内的文件，可用 `list_files`、`read_file`、`write_file`、`move_file`、`delete_file`。路径经规范化后越界即拒绝。
- **角色卡编辑**：`list_characters`、`get_character`、`update_character` 三个工具可读写角色卡，仅允许修改 `name`、`description`、`personality`、`scenario`、`firstMessages`、`postHistoryInstructions`、`creatorNotes`、`systemPrompt` 八个字段，不支持删除角色或改动绑定关系。
- **世界书整理**：`list_worldbooks`、`create_worldbook`、`add_worldbook_entry`、`get_worldbook_entries` 四个工具可以把沙箱里的零散设定文件转成世界书。既可以新建一本，也可以追加到已有世界书；没有关键词的条目按常驻写入。
- **工具协议**：不使用各供应商互不兼容的 function calling，而是约定模型在正文输出 `<tool name="...">{...}</tool>`，由应用解析执行并以 `<tool_result>` 回填，单轮回复最多连续执行 5 次调用；OpenAI Compatible、Gemini、Anthropic 三种协议均可使用。
- **提示词双模式**：助手页右上角可切换「继承全局主提示词」或「使用独立提示词」。继承时沿用设置里的 Main Prompt，并自动把 `{{char}}` 解析为 Assistant、`{{user}}` 解析为当前用户名；独立模式使用单独保存的提示词。两种模式都会追加助手身份边界与工具说明。
- **模型依赖**：助手沿用当前全局模型配置，需先在「设置 > 模型配置」启用一个配置才能对话。

### 消耗统计增强

在原有 Token 统计基础上增加了金额与余额两个维度，金额以人民币结算。

- **人民币计价**：国内服务商按官方人民币牌价，境外模型按美元报价乘以汇率折算（默认 7.2，存于偏好可改），界面统一显示 `¥`。
- **峰谷分档**：硅基流动 DeepSeek V4 Flash（0731）内置官方档位——2–8 点缓存命中 ¥0.150/输入 ¥1.500/输出 ¥4.500 每百万 Token，其余时段 ¥0.300/¥3.000/¥9.000。金额按每条请求的发生时间逐条折算，因为聚合后的总量无法区分峰谷。
- **缓存命中单独计价**：命中缓存的输入走缓存档单价，不与普通输入混算。
- **站点绑定**：内置价目绑定站点主机名（如 `siliconflow.cn`），只在请求确实打到该站点时生效——同一个模型在不同服务商的价目并不相同，不跨站套价。站点或模型未收录时回退 OpenRouter 实时报价并按汇率折算。
- **在线报价**：收录不到的模型从 OpenRouter 公开模型列表实时拉取（`https://openrouter.ai/api/v1/models`），结果缓存在本地，离线沿用上次快照；卡片同时显示覆盖率与未定价模型。
- **模型名归一化**：请求写法与报价表键名经常不一致（`openai/gpt-4o-2024-08-06`、`claude-3-5-sonnet@20240620`），匹配时依次去供应商前缀、去明确版本尾巴；只裁剪版本标记，不会把 `gemini-2.5-flash` 的 `-flash` 误当版本。
- **账户余额**：接入服务商官方余额接口。OpenRouter 走 `GET /api/v1/credits`（余额 = 累计充值 − 累计用量），DeepSeek 走 `GET /user/balance`（直接返回可用、赠送、充值余额）。其他服务商没有公开接口，页面显示说明而不是错误。
- **状态区分**：余额区分「未查询 / 查询中 / 可用 / 服务商不支持 / 查询失败」，不用 0 或占位符混淆这些情况。

> 智谱、月之暗面等站点暂未内置人民币价目：公开渠道只有第三方按美元折算的参考值，与官方牌价未必一致，与其写一个可能算错的数字，不如交给 OpenRouter 实时报价兜底。需要精确账单时以服务商控制台为准。


- `feature/characteredit/ui/CharacterEditLayout.kt` 补齐 `androidx.compose.foundation.layout.ExperimentalLayoutApi` 导入。该文件使用了 `FlowRow` 却缺少对应 opt-in 导入，在上游当前 Compose BOM 下无法通过编译。

### 验证

- `assembleDebug` 通过。
- `testDebugUnitTest` 通过：原有 279 项测试全绿，另新增 23 项（8 项助手工具协议解析、8 项报价匹配与计价、7 项小说分章解析）。
- 构建环境：JDK 17、Android SDK（platform 36.1 + build-tools 36.1.0）、AGP 9.0.1、Gradle 9.5、Kotlin 2.0.21。

### 附带脚本

不参与应用构建，仅用于本地维护：

- `tools/localize-default-zh.py`：把 `values-b+zh+Hans` 的翻译合并回默认资源集。上游更新文案后重跑一次即可。
- `tools/backup/`：中文化之前的原始 `strings.xml` 备份。
- `tools/run-emulator.ps1`：启动 AVD 并把窗口摆进可见桌面，可选自动安装 APK。模拟器窗口尺寸按设备像素高计算，在高分屏缩放环境下会溢出屏幕，脚本负责纠正。
- `dist/`：debug 安装包与界面截图，本地产物，已在 `.gitignore` 中排除。

## 项目简介

RPClient 使用 Kotlin 与 Jetpack Compose 编写，在 Android 设备上提供完整、可控的 AI 角色扮演体验。聊天记录、角色、世界书和应用配置默认保存在本地，模型请求则直接发送到用户配置的服务商。

项目参考了 SillyTavern 的角色卡、世界书、Prompt 和 Regex 脚本生态，并提供相应的导入、导出与兼容能力。RPClient 并非 SillyTavern 的 Android 移植版，两者在部分高级字段和运行行为上仍可能存在差异。

> [!WARNING]
> 项目仍在开发中，数据结构、界面和兼容行为可能继续调整。导入重要角色卡、世界书或 Regex 脚本前，建议保留原始文件并定期备份。

## 界面展示

<p align="center">
  <img src="metadata/images/home.jpg" alt="RPClient 主页" width="45%" />
  <img src="metadata/images/chat.jpg" alt="RPClient 聊天页面" width="45%" />
</p>
<p align="center">
  <img src="metadata/images/story.jpg" alt="RPClient 故事创作" width="45%" />
  <img src="metadata/images/settings.jpg" alt="RPClient 设置页面" width="45%" />
</p>

## 功能特性

### 对话

- 单角色对话与多角色群聊
- 流式输出、停止生成、重新生成和续写
- 用户视角生成（Impersonate）
- 从指定消息创建对话分支
- Markdown 消息渲染与思维内容折叠
- 自动或手动生成长期摘要记忆
- 会话级世界书条目选择

### 角色与世界书

- 创建、编辑、搜索和管理角色
- 导入 Character Card V1/V2 JSON 与 PNG 角色卡
- 导出 Character Card V2 JSON 或带元数据的 PNG
- 支持角色头像、多个开场白、示例对话和扩展字段
- 创建、编辑、导入和导出 SillyTavern 风格世界书
- 支持关键字扫描、递归触发、概率、优先级、深度和 Token 预算等配置

### 模型与 Prompt

- OpenAI Compatible API
- Google Gemini API
- Anthropic Messages API
- 内置 ChatGPT、Gemini、Claude、DeepSeek、Grok 和 OpenRouter 配置模板
- 支持自定义服务地址、模型、请求头与生成参数
- Prompt 预设、宏展开和协议相关的消息后处理
- Prompt Inspector：检查最终消息、Token 预算、世界书命中、Regex 处理与省略内容
- 调试模式：在本地记录并查看原始请求和响应 JSON

### 故事创作

- 连续正文编辑器，支持本地防抖自动保存与 revision 冲突保护
- 支持按章节持久保存引导的文档末尾续写，以及会话期内的多步撤销与恢复
- Story Memory、作者注释、Story 专属角色激活、世界书匹配与 Prompt Inspector
- 流式生成、停止、部分结果恢复、会话内多步撤销与恢复
- TXT/Markdown 导入导出及可迁移的 `.rpstory.json` 归档

### Regex 脚本

- 兼容常见 SillyTavern Regex 脚本 JSON
- Global 和 Character 两种作用域
- Source、Markdown 和 Prompt 三种执行模式
- 支持脚本排序、复制、测试、导入与导出
- 角色卡内嵌脚本默认不会自动获得执行授权

### 其他

- 本地优先的数据存储
- Material 3 与动态配色
- 简体中文、繁体中文、英语、日语、韩语、德语、法语、俄语、葡萄牙语（巴西）、意大利语、越南语和印尼语界面
- Android 8.0（API 26）及以上系统

## 快速开始

1. 安装并打开 RPClient（可通过 [Google Play](https://play.google.com/store/apps/details?id=me.kafuuneko.rpclient)、[F-Droid](https://f-droid.org/packages/me.kafuuneko.rpclient/) 或 [GitHub Releases](https://github.com/KafuuNeko/RPClient/releases) 获取）。
2. 进入“设置 > 模型配置”，选择已有模板或新建模型配置。
3. 填写 API Key、模型名称和服务地址，测试连接后启用该配置。
4. 创建角色，或导入已有的 JSON/PNG 角色卡。
5. 根据需要导入世界书并关联角色。
6. 新建单人会话或群聊开始对话，或进入“故事创作”创建连续正文。

API Key 仅保存在应用本地，但会随模型请求发送给所配置的服务商。请只使用可信的 API 地址和中转服务。

## AI 编码引导

使用 AI 辅助修改代码前，必须先阅读 [doc/coding-guidelines.md](doc/coding-guidelines.md)。该引导已按 RPClient 的 Feature + MVI 结构、Room 数据层、Koin/Kotpref 使用方式，以及角色卡、世界书、Prompt、Regex 脚本、LLM 请求和请求日志等领域规则整理。

开始编辑前只读取与任务相关的专题文档，完成后运行与改动风险匹配的最小验证。

## 参与贡献

欢迎提交 Issue 和 Pull Request。使用 AI 辅助编码时，请遵循 [AI 编码引导](doc/coding-guidelines.md)。

提交代码前建议至少保证代码可构建。

报告兼容性问题时，请尽量附上脱敏后的角色卡、世界书或 Regex JSON、使用的 Provider 协议、模型名称以及复现步骤。请勿在 Issue 中公开 API Key、私密对话或未经授权的角色资源。

## 隐私与免责声明

- RPClient 不提供模型服务，使用模型 API 产生的费用与内容责任由用户自行承担。
- 请求内容会发送到用户配置的模型服务商，使用前请阅读对应服务的隐私政策。
- 项目与 SillyTavern、OpenAI、Google、Anthropic、DeepSeek、xAI 及 OpenRouter 均无隶属或官方合作关系。
- 角色卡、世界书及其他导入内容的版权与使用授权由其提供者和使用者负责。

## 联系方式

- GitHub：[KafuuNeko/RPClient](https://github.com/KafuuNeko/RPClient)
- Email：kafuuneko@gmail.com
