# Subtext Translator（潜台词翻译器）

一个运行在智能眼镜上的 AI 沟通助手 App，基于 [xg.glass SDK](https://xg.glass) 构建。

---

## 功能

戴上智能眼镜后，在与伴侣（或任何人）交谈时：

1. **聆听** — 通过眼镜麦克风录制对方说的话（~8 秒）
2. **理解** — 调用 OpenAI Whisper 语音识别 + GPT-4o-mini 分析话语背后的真实含义
3. **建议** — 在眼镜镜片上显示对方的潜台词解读和最佳回复建议

### 示例

领导说
> 「这件事我觉得还可再斟酌斟酌。」

眼镜显示
> 「潜台词」你拿的什么方案，出的什么主意，一点都不好，不甩你脸上都是给你面子了。当领导说再斟酌斟酌，再考虑考虑，那就说明他不满意，只是委婉的告我们不行，一定要懂事、知趣。
> 
> 回答「好的领导，那我回去继续完善，一定让您满意。」


## 快速运行

```bash
cd xg-glass-sample/hidden_rule_translator
xg-glass run main.kt
```

运行前，请将 `main.kt` 中的 `YOUR_OPENAI_API_KEY_HERE` 替换为你自己的 OpenAI API Key。

---

## 使用流程

1. 眼镜连接手机后，运行 App
2. 触发 **Listen & Advise** 命令
3. 眼镜显示 "🎧 正在聆听..."，此时让对方说话
4. 约 8 秒后自动停止录音，显示 "🤔 正在分析..."
5. 几秒后，镜片上显示潜台词解读和建议回复

---

## 核心逻辑（~20 行）

```kotlin
// 1. 录音
val session = ctx.client.startMicrophone().getOrThrow()
val chunks = withTimeoutOrNull(8000L) { session.audio.toList() }
session.stop()

// 2. 语音转文字 (Whisper)
val transcription = openAI.transcription(TranscriptionRequest(...))

// 3. 潜台词分析 + 回复建议 (GPT-4o-mini)
val req = chatCompletionRequest {
    model = ModelId("gpt-4o-mini")
    messages {
        system { content = systemPrompt }
        user { content = "对方刚刚说了：\"${transcription.text}\"" }
    }
}
val advice = openAI.chatCompletion(req).choices.first().message.content

// 4. 显示在眼镜上
ctx.client.display(advice, DisplayOptions())
```

---

## 前提条件

- JDK 17+
- Android SDK + `adb`
- 已安装 xg.glass CLI (`xg-glass --help` 可用)
- OpenAI API Key（需要 Whisper + Chat Completions 权限）
- 智能眼镜 + Android 手机

---

## 自定义

- **录音时长**：修改 `withTimeoutOrNull(8000L)` 中的毫秒数
- **语言**：修改 Whisper 的 `language` 参数（当前为 `"zh"` 中文）
- **Prompt 风格**：修改 `systemPrompt` 以适配不同场景（职场、社交等）
- **模型**：可替换为 `gpt-4o` 以获得更高质量的分析


