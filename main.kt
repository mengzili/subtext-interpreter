package com.example.subtext.logic

import com.universalglasses.appcontract.UniversalAppContext
import com.universalglasses.appcontract.UniversalAppEntrySimple
import com.universalglasses.appcontract.UniversalCommand
import com.universalglasses.core.DisplayOptions
import com.aallam.openai.api.audio.TranscriptionRequest
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer

class HiddenRuleEntry : UniversalAppEntrySimple {
    override val id: String = "hidden_rule_translator"
    override val displayName: String = "Hidden Rule Translator"

    // Replace with your own OpenAI API key (inject securely in production)
    private val openAI = OpenAI("YOUR_OPENAI_API_KEY_HERE")

    // System prompt: relationship communication expert
    private val systemPrompt = """
你是一个沟通专家和情商大师。你的任务是帮助用户理解对方（女朋友/男朋友/伴侣/领导）说的话背后的真正含义，并给出最佳回复建议。

规则：
1. 首先用一句话简要分析对方话语背后的真实意图和情感需求（标记为【她的意思】）
2. 然后给出一个最佳回复建议（标记为【你应该说】）
3. 回复要体现出关心、理解、情商高、让对方感到被重视
4. 语气要自然，像正常人说话，不要太正式
5. 回复要简短精炼，适合在眼镜上快速阅读（控制在50字以内）
6. 用中文回复

例子：
对方说："你忙吧，不用管我了。"
【她的意思】她希望你放下手头的事来关心她，她觉得被忽略了。
【你应该说】我忙完这点就来找你，你比什么都重要。

对方说："随便，都行。"
【她的意思】她其实有自己的想法，希望你能猜到或者主动做决定。
【你应该说】我觉得那家你上次喜欢的餐厅不错，我订位了我们去吧？

对方说："我没生气。"
【她的意思】她已经生气了，希望你能主动发现原因并哄她。
【你应该说】我感觉你不太开心，是不是我哪里做得不好？你跟我说，我改。
""".trimIndent()

    override fun commands(): List<UniversalCommand> {
        val listen = object : UniversalCommand {
            override val id: String = "listen_and_advise"
            override val title: String = "Listen & Advise"

            override suspend fun run(ctx: UniversalAppContext): Result<Unit> {
                // Step 1: Show recording status on glasses
                ctx.client.display("🎧 正在聆听...", DisplayOptions())

                // Step 2: Start microphone and collect audio (~8 seconds of speech)
                val session = ctx.client.startMicrophone().getOrThrow()
                val chunks = withTimeoutOrNull(8000L) {
                    session.audio.toList()
                } ?: emptyList()
                session.stop()

                if (chunks.isEmpty()) {
                    return ctx.client.display("❌ 未检测到语音，请重试", DisplayOptions())
                }

                // Step 3: Combine audio chunks into a single byte array
                val audioBytes = chunks.fold(ByteArray(0)) { acc, chunk ->
                    acc + chunk.bytes
                }

                ctx.client.display("🤔 正在分析...", DisplayOptions())

                // Step 4: Transcribe audio using Whisper
                val transcription = openAI.transcription(
                    TranscriptionRequest(
                        audio = FileSource(
                            name = "audio.wav",
                            source = Buffer().apply { write(audioBytes) }
                        ),
                        model = ModelId("whisper-1"),
                        language = "zh"
                    )
                )
                val spokenText = transcription.text

                if (spokenText.isBlank()) {
                    return ctx.client.display("❌ 未识别到有效语音", DisplayOptions())
                }

                // Step 5: Send to GPT to decode hidden meaning and generate reply
                val req = chatCompletionRequest {
                    model = ModelId("gpt-4o-mini")
                    messages {
                        system { content = systemPrompt }
                        user { content = "对方刚刚说了：\"$spokenText\"" }
                    }
                }
                val advice = openAI.chatCompletion(req)
                    .choices.firstOrNull()?.message?.content.orEmpty()
                    .ifBlank { "暂时无法分析，请重试" }

                // Step 6: Display the advice on the glasses
                return ctx.client.display(advice, DisplayOptions())
            }
        }

        return listOf(listen)
    }
}
