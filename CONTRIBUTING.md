# 贡献指南

感谢你愿意改进 CharChat。本项目是一个纯本地的 Android 角色扮演聊天客户端（BYOK，自带 API Key），
所有数据只存在设备本地，不经过任何中转服务器。

## 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | `compileOptions` / `kotlinOptions` 均为 17；更高版本 JDK 通常也能构建 |
| Android SDK | API 34（compileSdk 34） | `minSdk 26`、`targetSdk 34` |
| Gradle | 8.9 | 由 Wrapper 提供，无需手动安装 |
| Kotlin | 1.9.24 | 与 AGP 8.5.2 配套 |

`local.properties` 中的 `sdk.dir` 由本机 Android Studio 生成，**不要提交**（已在 `.gitignore` 中忽略）。

## 构建与测试

```bash
# 首次运行会自动下载 Gradle 8.9 发行版
./gradlew testDebugUnitTest      # 单元测试
./gradlew assembleDebug          # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # 安装到已连接的设备
```

> 提交 PR 前请确保前两条命令均通过，CI 也会执行同样的检查。

## 代码结构

```
app/src/main/java/com/lirui/charchat/
├── data/                  # 数据层：网络、数据库、本地文件、解析
│   ├── backup/            # 备份导出 / 导入
│   ├── cardparser/        # SillyTavern 角色卡解析与映射
│   ├── db/                # Room：entity / dao / AppDatabase（含迁移）
│   ├── group/             # 群聊相关数据
│   ├── remote/            # 各家 API 客户端（对话 / 图像 / 语音）
│   ├── settings/          # 设置项读写（加密 DataStore）
│   └── storage/           # 头像 / 照片 / 语音文件的本地存储
├── domain/                # 领域层：纯逻辑，尽量不依赖 Android
│   ├── chat/              # 提示词组装、清洗、护栏、世界书触发等纯函数
│   ├── model/             # 领域模型
│   ├── repository/        # 仓储与编排器（对话/语音/出图的完整链路）
│   └── worldbook/         # 世界书模型
├── di/                    # Hilt 依赖注入
└── ui/                    # Compose 界面层，按页面分包
```

分层约定：**`ui` → `domain` → `data`**，不要反向依赖；能写成纯函数的逻辑（清洗、解析、拼装）
一律放 `domain/chat`，并在 `app/src/test` 下补单元测试。

## 提交规范

- 提交信息用中文，格式 `类型: 简述`，类型如 `feat` / `fix` / `refactor` / `docs` / `build` / `chore`。
- 一个提交只做一件事；格式化改动与逻辑改动分开提交。
- 新增纯逻辑必须补单测（当前测试位于 `app/src/test/java/com/lirui/charchat/`）。

## 数据库迁移

Room 版本号在 `data/db/AppDatabase.kt` 的 `version` 字段。**升级必须同时提供 `Migration`**，
并遵循两条硬性要求：

1. 迁移脚本必须幂等（用 `PRAGMA table_info` 判断列是否已存在，不要直接 `ALTER TABLE ADD COLUMN` 后假设成功）。
2. 新字段必须有 Kotlin 默认值，保证老数据读出来不崩。

## 安全红线

- **绝对不要提交任何 API Key、Token、签名文件**。所有凭据由用户在设置页填写，存于加密 DataStore。
- 日志中不得打印 Key 本身（现有代码只在 Debug 构建输出脱敏诊断信息）。
- 涉及联网的新功能请保持"数据只在本机与用户自己配置的模型服务之间流转"这一前提。

## 提 PR

1. Fork 后从 `main` 切出分支，命名如 `feat/voice-message`。
2. 按 PR 模板填写：改动类型、自测情况、影响面（是否涉及 DB 迁移 / 设置项变更 / 提示词变更）。
3. UI 改动请附前后对比截图。
4. 保持 PR 聚焦，不要夹带无关的格式化或重构。
