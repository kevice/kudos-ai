package io.kudos.ai.ability.model.audio.support.enums.ienums

/**
 * Voice（语音/发音人）接口
 * 
 * @author K
 * @author AI:cursor
 * @since 1.0.0
 */
interface IVoiceEnum {
    
    /** Voice 的唯一标识符（ID），用于 API 调用 */
    val voiceId: String
    
    /** Voice 的显示名称（可选） */
    val displayName: String?
        get() = null
    
    /** Voice 支持的语言代码（如 "zh", "en", "zh-CN"） */
    val language: String?
        get() = null
    
    /** Voice 类型（中文、英文等） */
    val voiceType: VoiceType?
        get() = null
    
    /** Voice 性别（可选） */
    val gender: VoiceGender?
        get() = null
    
    /** 是否为默认 voice */
    val isDefault: Boolean
        get() = false
}

/**
 * Voice 类型枚举
 */
enum class VoiceType {
    /** 中文 voice */
    CHINESE,
    
    /** 英文 voice */
    ENGLISH,
    
    /** 多语言 voice */
    MULTILINGUAL,
    
    /** 其他语言 voice */
    OTHER
}

/**
 * Voice 性别枚举
 */
enum class VoiceGender {
    /** 女性 */
    FEMALE,
    
    /** 男性 */
    MALE,
    
    /** 中性/未指定 */
    NEUTRAL
}
