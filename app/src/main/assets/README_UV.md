# RPClient UV

RPClient UV 是 [RPClient](https://github.com/KafuuNeko/RPClient) 的非官方定制分支，基线为上游 `2026.3.1`（提交 `c3ed325`）。改版作者：yes a hit。

这份文档说明本分支相对上游改了哪些内容。上游原有的角色卡、世界书、群聊、故事创作、Prompt 检查与多协议模型接入等功能全部保留。

## 应用名称与包名

- 显示名 `RPClient UV`，`app_name` 与关于页品牌名同步修改。
- `applicationId` 为 `me.kafuuneko.rpclient.uv`，debug 构建为 `me.kafuuneko.rpclient.uv.dev`。
- 与官方版 `me.kafuuneko.rpclient` 完全隔离，可以同时安装、互不覆盖。
- 包名变更后不继承旧版数据，换用本版本时需要重新导入角色卡、世界书与模型配置。
- 代码包名 `namespace` 仍为 `me.kafuuneko.rpclient`，源码目录结构未调整。

## 默认语言

- 默认资源集整体替换为简体中文，系统语言不在支持列表内时（包括英文系统）界面同样显示中文。
- 其余 12 个语言目录保持上游内容，对应语言的系统仍显示各自翻译。
- 搬运过程中逐条校验过格式化占位符，可与中文对齐的 1263 条全部一致。

## 助手

首页快捷区新增「助手」入口，进入后是独立会话页面，与角色聊天、群聊、故事创作并列，不占用角色卡与会话数据。

- **独立会话**：消息保存在 `filesDir/assistant/session.json`，单会话、可一键清空，不写入数据库。
- **沙箱文件权限**：助手只能访问 `filesDir/assistant/sandbox`，可用 `list_files`、`read_file`、`write_file`、`move_file`、`delete_file`。路径规范化后越界即拒绝。
- **角色卡编辑**：`list_characters`、`get_character`、`update_character` 可读写角色卡，仅允许修改 `name`、`description`、`personality`、`scenario`、`firstMessages`、`postHistoryInstructions`、`creatorNotes`、`systemPrompt` 八个字段，不支持删除角色或改动绑定关系。
- **工具协议**：不依赖各供应商互不兼容的 function calling，而是约定模型在正文输出 `<tool name="...">{...}</tool>`，应用解析执行后以 `<tool_result>` 回填，单轮最多连续执行 5 次调用，OpenAI Compatible、Gemini、Anthropic 三种协议均可用。
- **提示词双模式**：可切换「继承全局主提示词」或「使用独立提示词」。继承时沿用设置里的 Main Prompt，并自动把 `{{char}}` 解析为 Assistant；独立模式使用单独保存的提示词。两种模式都会追加助手身份边界与工具说明。
- **模型依赖**：助手沿用当前全局模型配置，需先在「设置 > 模型配置」启用一个配置才能对话。

## 消耗统计增强

在原有 Token 统计基础上增加金额与余额两个维度，金额以人民币结算。

- 人民币计价：国内服务商按官方人民币牌价，境外模型按美元报价乘汇率折算（默认 7.2），界面统一显示 ¥。
- 峰谷分档：硅基流动 DeepSeek V4 Flash（0731）2–8 点为 ¥0.150/¥1.500/¥4.500，其余时段 ¥0.300/¥3.000/¥9.000（每百万 Token，依次为缓存命中/输入/输出）。金额按每条请求发生时间逐条折算。
- 缓存命中单独计价，不与普通输入混算。
- 站点绑定：内置价目绑定站点主机名，只在请求确实打到该站点时生效，同一模型不跨站套价；未收录站点或模型回退 OpenRouter 实时报价并按汇率折算。
- 账户余额：OpenRouter 走 `GET /api/v1/credits`，DeepSeek 走 `GET /user/balance`；其他服务商没有公开接口，页面显示说明而非错误。
## 编译修复

- `feature/characteredit/ui/CharacterEditLayout.kt` 补齐 `androidx.compose.foundation.layout.ExperimentalLayoutApi` 导入。该文件使用了 `FlowRow` 却缺少对应的 opt-in 导入，在上游当前的 Compose BOM 下无法通过编译。

## 验证状态

- `assembleDebug` 通过。
- `testDebugUnitTest` 通过：上游 279 项测试全绿，另新增 8 项覆盖助手工具协议解析。
- 构建环境：JDK 17、Android SDK（platform 36.1 + build-tools 36.1.0）、AGP 9.0.1、Gradle 9.5、Kotlin 2.0.21。

## 免责说明

- 本项目不提供模型服务，模型 API 产生的费用与内容责任由使用者自行承担。
- 与上游作者、SillyTavern、OpenAI、Google、Anthropic、DeepSeek、xAI、OpenRouter 均无隶属或合作关系。
- 角色卡、世界书及其他导入内容的版权与使用授权由提供者与使用者负责。
- 上游项目采用 MIT License，本分支同样遵循。
