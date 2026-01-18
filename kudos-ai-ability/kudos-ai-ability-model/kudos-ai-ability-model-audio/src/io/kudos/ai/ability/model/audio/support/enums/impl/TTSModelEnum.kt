package io.kudos.ai.ability.model.audio.support.enums.impl

import io.kudos.ai.ability.model.audio.support.enums.ienums.ITTSModelEnum
import io.kudos.ai.ability.model.audio.support.enums.ienums.IVoiceEnum
import kotlin.reflect.KClass

/**
 * TTS (Text-to-Speech) 文本转语音模型枚举
 *
 * @author K
 * @author AI: Cursor
 * @since 1.0.0
 */
enum class TTSModelEnum(
    override val modelName: String,
    override val parameters: Float,
    override val contextSize: Float,
    override val size: Float,
    override val provider: String
) : ITTSModelEnum {

    /**
     * Kokoro TTS 模型
     * 高质量语音合成，82M 参数，支持多种语言（主要是英文）
     * 可处理较长文本输入（约 2K token）
     * 注意：虽然模型有中文 voices，但底层使用 espeak backend 进行音素化，不支持中文语言代码 "zh"
     * 因此实际上无法处理中文文本，会报错：RuntimeError: language "zh" is not supported by the espeak backend
     * 如果主要处理中文文本，请使用 PIPER_ZH_CN_HUAYAN_MEDIUM
     * 如果主要处理中英文混合文本，目前 speaches-ai 没有完美支持的模型
     */
    KOKORO_82M("speaches-ai/Kokoro-82M-v1.0-ONNX", 0.082F, 2.0F, 0.163F, "speaches-ai"),

    /**
     * Kokoro TTS 中文优化模型（非官方）
     * 专门针对中文优化的版本，82M 参数，ONNX 格式
     * 包含 100 种来自 LongMaoData 的中文声音，以及 3 种新的英文声音
     * 优势：专门针对中英文混合文本进行了优化，能更好地处理中英文混合内容
     * 注意：此模型可能不支持 speaches-ai 的 OpenAI voice 映射（如 alloy -> af_heart）
     * 如果遇到 voice 映射错误（AssertionError: Voice af_heart not found），
     * 这是 speaches-ai 的 voice 映射机制问题，不是模型本身的问题
     * 建议：等待 speaches-ai 更新以支持该模型的 voice 映射，或使用其他模型
     */
    SURONEK_KOKORO_82M_V1_1_ZH_ONNX("suronek/Kokoro-82M-v1.1-zh-ONNX", 0.082F, 2.0F, 0.163F, "suronek"),

    /**
     * Piper TTS 中文模型（高质量）
     * 专门针对中文优化的 Piper 模型，支持中文语音合成
     * 轻量级，适合快速部署，中文语音质量较好
     * 推荐用于中文 TTS 场景
     */
    PIPER_ZH_CN_HUAYAN_MEDIUM("speaches-ai/piper-zh_CN-huayan-medium", 0.05F, 1.0F, 0.072F, "speaches-ai"),

    /**
     * Piper TTS 中文模型（低质量，快速）
     * 专门针对中文优化的 Piper 模型，低质量但速度快
     * 适合对速度要求较高的场景
     */
    PIPER_ZH_CN_HUAYAN_X_LOW("speaches-ai/piper-zh_CN-huayan-x_low", 0.05F, 1.0F, 0.072F, "speaches-ai");


    override fun getDefaultVoice(): IVoiceEnum? {
        return when (this) {
            KOKORO_82M -> KokoroVoiceEnum.getDefaultVoice()
            SURONEK_KOKORO_82M_V1_1_ZH_ONNX -> KokoroVoiceEnum.getDefaultChineseVoice()
            PIPER_ZH_CN_HUAYAN_MEDIUM -> PiperVoiceEnum.getRecommendedVoiceForModel(modelName)
            PIPER_ZH_CN_HUAYAN_X_LOW -> PiperVoiceEnum.getRecommendedVoiceForModel(modelName)
        }
    }
    
    override fun findVoice(voiceId: String): IVoiceEnum? {
        return when (this) {
            KOKORO_82M, SURONEK_KOKORO_82M_V1_1_ZH_ONNX -> KokoroVoiceEnum.fromVoiceId(voiceId)
            PIPER_ZH_CN_HUAYAN_MEDIUM, PIPER_ZH_CN_HUAYAN_X_LOW -> PiperVoiceEnum.fromVoiceId(voiceId)
        }
    }
    
    override fun getAvailableVoices(): List<IVoiceEnum> {
        return when (this) {
            KOKORO_82M, SURONEK_KOKORO_82M_V1_1_ZH_ONNX -> KokoroVoiceEnum.entries.toList()
            PIPER_ZH_CN_HUAYAN_MEDIUM, PIPER_ZH_CN_HUAYAN_X_LOW -> PiperVoiceEnum.entries.toList()
        }
    }

    override fun getDefaultChineseVoice(): IVoiceEnum? {
        return when (this) {
            KOKORO_82M, SURONEK_KOKORO_82M_V1_1_ZH_ONNX -> KokoroVoiceEnum.getDefaultChineseVoice()
            PIPER_ZH_CN_HUAYAN_MEDIUM, PIPER_ZH_CN_HUAYAN_X_LOW -> PiperVoiceEnum.getChineseVoices().firstOrNull()
        }
    }
    
    override fun getDefaultEnglishVoice(): IVoiceEnum? {
        return when (this) {
            KOKORO_82M -> KokoroVoiceEnum.getDefaultVoice()
            SURONEK_KOKORO_82M_V1_1_ZH_ONNX -> KokoroVoiceEnum.getEnglishVoices().firstOrNull()
            PIPER_ZH_CN_HUAYAN_MEDIUM, PIPER_ZH_CN_HUAYAN_X_LOW -> null // Piper 中文模型不支持英文
        }
    }

    // Speeches-ai不支持：
//    /**
//     * Kokoro TTS 中文优化模型
//     * 专门针对中文优化的版本，82M 参数
//     * 包含 100 种来自 LongMaoData 的中文声音，以及 3 种新的英文声音
//     * 可处理较长文本输入（约 2K token）
//     *
//     * 注意：此模型可能不在 speaches 的模型注册表中，使用时需要确认 speaches 是否支持
//     * 如果 speaches 不支持，可能需要使用其他 TTS 服务或直接使用 Hugging Face 模型
//     */
//    KOKORO_82M_V1_1_ZH("hexgrad/Kokoro-82M-v1.1-zh", 0.082F, 2.0F, 0.163F, "hexgrad"),
//
//    /**
//     * Kokoro TTS 中文优化模型（ONNX 版本）
//     * 专门针对中文优化的版本，82M 参数，ONNX 格式
//     * 包含 100 种来自 LongMaoData 的中文声音，以及 3 种新的英文声音
//     * 可处理较长文本输入（约 2K token）
//     * ONNX 格式通常具有更好的推理性能和跨平台兼容性
//     */
//    KOKORO_82M_V1_1_ZH_ONNX("onnx-community/Kokoro-82M-v1.1-zh-ONNX", 0.082F, 2.0F, 0.163F, "onnx-community"),
//
//    /**
//     * Piper TTS 模型
//     * 轻量级语音合成，适合快速部署
//     * 可处理中等长度文本输入（约 1K token）
//     */
//    PIPER_VOICES_50M("rhasspy/piper-voices", 0.05F, 1.0F, 0.072F, "Rhasspy"),

}
