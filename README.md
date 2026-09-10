# CharChat

[![Android CI](https://github.com/liguge9111/aichat-jws/actions/workflows/android.yml/badge.svg)](https://github.com/liguge9111/aichat-jws/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个 **BYOK（自带 Key）** 的 Android 角色聊天 App：导入 SillyTavern / TavernAI 角色卡，自动生成攻略属性，与角色模拟真实聊天——完全第一人称、像发微信一样，角色还会在合适的时机给你"发照片"、发语音。

**本 App 不含任何后端、不收集任何数据、没有账号系统。** 所有 API 调用由你的设备直连你自己配置的端点。

> ⚠️ **仅限 18+ 成年用户。** 本 App 在年龄确认入口后支持成人（NSFW）内容。所有角色均为**虚构的成年角色**。你对自己生成的内容负全部责任，并须遵守你所配置端点的服务条款。

---

## 功能

| 模块 | 说明 |
|---|---|
| 角色卡导入 | PNG（V2/V3 `tEXt` 嵌卡）与 JSON，支持粘贴 JSON；自动抽取头像 |
| 攻略属性 | 从卡中自动解析 外貌 / 穿着 / 性格 / 所在位置 / 在做什么 / 性癖，缺段回落默认；可在属性面板手改 |
| 玩家背景 | 你的名字、性格、与角色的关系、补充设定 —— 让角色认识你 |
| 单角色聊天 | 流式逐字返回；单轮最多连发 5 条气泡；长按自己的消息可编辑并**截断重生成** |
| 开场白选择 | 带开场白的卡（firstMes / `alternate_greetings`），首次进聊天弹框预览并选择用哪条开场（可跳过） |
| 回复语言跟随 | 回复语言跟随你每一轮的输入语言（默认简体中文），英文角色卡不会把对话带跑成英文 |
| 照片生成 | 角色在回复里写 `[[PHOTO: 画面]]` 即触发图像 API 出图，以"对方正在发图"的拟真接收气泡插入对话 |
| 角色一致性 | 首次出图前把整张卡提炼成**固定中文形象档案**（≤200 字：发色/瞳色/体型/常服/画风），此后每张图都用"档案 + 近期剧情 + 画面"拼提示词，大幅缓解跨图"变脸/换装"漂移 |
| 共同回忆 | 回复里的 `[[MEM:...]]` 信号把角色该记住的事落库，之后每轮作为【共同回忆】注入；属性面板可查看/编辑 |
| 好感度系统 | 好感度与关系阶段随对话自然演进（隐藏信号 `[[AFF:+n]]` / `[[REL:阶段]]`），玩家不能直接改（防作弊护栏） |
| 语音消息 | 长按「按住 说话」录音发送（上滑取消）；录音经语音识别转写后发给角色，**角色回复会带一条语音**，点气泡播放。TTS / ASR 均走你自己配置的 OpenAI 兼容端点，可按角色单独设音色 |
| 群聊 | 多角色同场；`@名字` 定向，无 @ 或 `@全体成员` 则全员依次回复；各自独立发图、各自推进好感 |
| 备份 | 导出/导入 JSON（头像以 base64 内嵌，备份自包含） |
| 数据安全 | API Key 存 Android Keystore 加密区；已关闭整机备份与云备份 |

## 交互约束（设计选择）

- **第一人称锁定**：回复禁止第三人称旁白与心理描写（"她…""他心想…"），不使用 `*动作*` 剧场标注，但允许 emoji。
- **单轮 ≤5 条**：模型一次返回用 `[[NEXT]]` 切分，模拟真实聊天的连发节奏。
- **输入护栏**：玩家输入中"以第三人称描述角色"或"试图直接修改好感/关系"的片段会在发送前被剥离，模型不会执行。
- **截断重生成**：长按编辑较早的消息时，该轮**及其之后**的全部消息会被删除并重新续写（保证上下文一致）。

## 图像服务商支持

出图链路按域名自动路由：

| 服务商 | 工作方式 |
|---|---|
| OpenAI 兼容网关（`/v1/images/generations`） | 任何暴露标准接口的网关：OpenAI、硅基流动、智谱、火山等 |
| 阿里云百炼 / DashScope（`dashscope.aliyuncs.com`） | 自动识别 → 走**原生异步任务协议**（提交 `text2image` 任务 → 每 3s 轮询 ≤120s → 取结果 URL）；若配置的模型名被判无效，自动改用官方推荐 `wanx2.1-t2i-turbo` 重试一次 |

对话与出图端点独立配置（见下）。

## 快速开始

1. 克隆仓库：`git clone https://github.com/liguge9111/aichat-jws.git`
2. 用 **Android Studio（Koala 或更新）** 打开项目根目录，等待 Gradle 同步（Wrapper 已随仓库提交，无需手动生成）。
3. 编译运行到模拟器或真机（**minSdk 26 / Android 8.0+**）：
   ```bash
   ./gradlew installDebug      # 或直接在 Android Studio 里点 Run
   ```
4. 进 App → 18+ 确认 → 右下 `+` 导入一张角色卡 → 去 **设置** 填你的 API 配置 → 开始聊。

> `local.properties`（本机 SDK 路径）由 Android Studio 自动生成，已列入 `.gitignore`，不会被提交。

### API 配置（设置页）

任何 **OpenAI 兼容**的端点都可以，对话与出图分开配置：

| 字段 | 示例 |
|---|---|
| 对话 Base URL | `https://api.openai.com/v1` |
| 对话模型 | `gpt-4o-mini` |
| 出图 Base URL | `https://dashscope.aliyuncs.com/compatible-mode/v1`（百炼）或 OpenAI 兼容出图网关 |
| 出图模型 | `qwen-image` / `wanx2.1-t2i-turbo` / 你的兼容出图模型 |
| 语音 TTS | OpenAI 兼容 `POST /v1/audio/speech`（如 `tts-1` / 网关提供的语音合成模型）+ 全局音色 |
| 语音 ASR | OpenAI 兼容 `POST /v1/audio/transcriptions`（如 `whisper-1`），把录音转成文字 |

- 图像配置留空会自动复用对话的 Key 与 Base URL；语音配置同样逐级回落：**ASR → TTS → 对话**，只填一处也能用。
- 语音消息依赖 TTS 与 ASR 两段配置；未配置时按住说话会给出提示并放弃该条语音，不会误发。
- 填完点 **测试连接** 会先探测 `GET /models`（不消耗额度），端点不支持时降级为一次极短对话确认连通。
- 图像段另有 **测试图像接口**（探测并列出出图模型，点击 chip 可一键填入）与 **发一张测试图**（真实出图，消耗额度）。

---

## ⚠️ 安全与合规，请务必先读

### 关于你的 API Key

- Key 保存在**你自己设备的 Keystore 加密区**，App 不上传、不做后端中转。
- **不要把它交给不信任的人**：Key 绑定的是你的付费账号。建议**单独创建一个设了额度上限的专用 Key** 给这个 App 用，即便出问题损失也可控。
- **已 root 或解锁 bootloader 的设备**上，系统级加密保护可被绕过，请自行判断风险。
- 你填的 Base URL 由你决定——**那个端点的运营者能看到你的 Key 和你发送的全部对话内容**。只用你信任的端点。
- 开源不会导致 Key 泄露：加密靠的是设备上的 Keystore 硬件密钥，不是靠算法保密。

### 18+ 与内容免责

- 本 App **含成人（NSFW）内容支持，仅供成年人使用**，入口有年龄确认。
- 所有角色均为**虚构的成年角色**；用户对自己生成的内容负全部责任。
- **重要**：若你把 NSFW 内容发往 OpenAI 等**官方端点**，会违反其服务条款，可能导致 **Key 被吊销或账号被封**。NSFW 场景请改用自托管方案（本地 Stable Diffusion / ComfyUI）或明确允许此类内容的兼容端点。
- 本项目不在中国大陆地区分发，不提供任何形式的内容托管与分发。

### 隐私

- 无账号、无云端同步、无埋点、无统计。角色卡与聊天记录仅存于你的设备本地。
- 聊天内容只会发往**你自己配置**的端点。

---

## 已知限制

1. **照片一致性是缓解而非根治**：通用文生图模型无法锁定同一张脸。上述"视觉档案 + 剧情上下文"拼装已把漂移压到很低；要做强一致需自托管 SD + 角色 LoRA。
2. **百炼出图 URL 约 24 小时后过期**：久远的历史图片可能无法加载（服务端限制；OpenAI 兼容网关的 URL 通常更持久）。
3. **好感演进依赖模型配合**：模型偶尔会漏写隐藏信号 `[[AFF:+n]]`，此时好感默认"无变化"（不做额外调用补偿）。
4. **备份不含聊天照片的二进制**：只保存路径，还原后历史图片可能显示占位。
5. **群聊是串行调用**：N 个角色 = N 倍延迟，这是为了保证消息顺序与界面稳定。
6. **未做图片内容审核**：出图完全取决于你配置的端点策略。
7. **中文第三人称旁白**主要靠提示词约束（无代词开头的中文旁白客户端正则无法可靠识别），偶发残留属预期。

## 技术栈

Kotlin · Jetpack Compose · Hilt · Room · OkHttp(SSE) · kotlinx.serialization · Coil · AndroidX Security（加密 DataStore）

架构为 MVVM：UI（Compose）→ ViewModel → domain（编排器 / 纯函数工具）→ data（Room / 远程客户端）。

```
app/src/main/java/com/lirui/charchat/
├─ data/         远程客户端（对话 / 图像 / 语音）、Room、卡解析、备份、加密存储
│  ├─ remote/    OpenAIChatClient / OpenAIImageClient / DashScopeImageClient / OpenAISpeechClient
│  ├─ db/        Room entity / dao / AppDatabase（含版本迁移）
│  ├─ cardparser/ SillyTavern 角色卡解析与映射
│  ├─ settings/  加密 DataStore 设置读写
│  ├─ storage/   头像 / 照片 / 语音文件的本地存储
│  └─ backup/    备份导出与导入
├─ domain/
│  ├─ chat/      PromptBuilder / InputGuardrail / RoundController / PhotoIntent
│  │             ReplyLanguage / VisualAnchor / PhotoPromptComposer / GreetingOptions …
│  ├─ repository/ ChatOrchestrator / GroupOrchestrator / 各仓储
│  ├─ worldbook/ 世界书模型
│  └─ model/
└─ ui/           gate / home / import / chat / group / attr / profile / worldbook / settings
```

纯函数集中在 `domain/chat/`，均配有单元测试（`app/src/test/`，共 **148 例**）。

## 构建

```bash
# 环境：JDK 17 + Android SDK 34（Gradle Wrapper 已提交，首次运行自动下载 Gradle 8.9）
./gradlew testDebugUnitTest     # 单元测试（148 例）
./gradlew assembleDebug         # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug          # 安装到已连接设备
```

CI（GitHub Actions）在每次 push / PR 时执行单测与打包，配置见 [`.github/workflows/android.yml`](.github/workflows/android.yml)。

## 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 更新历史

完整迭代记录见 [CHANGELOG.md](CHANGELOG.md)。

## 协议

本项目采用 [MIT License](LICENSE) 开源，可自由使用、修改与商用（含闭源二次开发），请保留版权声明。

> 使用前请确认：`app/build.gradle.kts` 中的 `applicationId`、签名配置与版本号符合你自己的发布预期。
