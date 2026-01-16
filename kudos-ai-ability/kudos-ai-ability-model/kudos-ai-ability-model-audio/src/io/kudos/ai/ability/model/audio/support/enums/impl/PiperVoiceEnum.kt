package io.kudos.ai.ability.model.audio.support.enums.impl

import io.kudos.ai.ability.model.audio.support.enums.ienums.IVoiceEnum
import io.kudos.ai.ability.model.audio.support.enums.ienums.VoiceGender
import io.kudos.ai.ability.model.audio.support.enums.ienums.VoiceType

/**
 * Piper TTS 模型的 Voice 枚举
 * 
 * 注意：Piper 模型通常是单语言的，每个模型只支持特定语言的 voices
 * 例如：piper-zh_CN-huayan-medium 只支持中文 voices
 * 
 * @author K
 * @author AI:cursor
 * @since 1.0.0
 */
enum class PiperVoiceEnum(
    override val voiceId: String,
    override val displayName: String? = null,
    override val language: String? = null,
    override val voiceType: VoiceType? = null,
    override val gender: VoiceGender? = null,
    override val isDefault: Boolean = false
) : IVoiceEnum {
    
    // ========== 中文 Piper 模型 Voices ==========
    // 注意：piper-zh_CN-huayan-medium 等中文模型通常只有一个默认 voice
    // 具体 voice ID 需要从模型 API 获取，这里列出常见的
    
    /** 中文 Piper 模型的默认 voice（通常模型名就是 voice） */
    ZH_CN_HUAYAN("zh_CN-huayan", "华严", "zh-CN", VoiceType.CHINESE, VoiceGender.FEMALE, isDefault = true),
    
    // ========== 英文 Piper 模型 Voices ==========
    // 英文 Piper 模型可能有多个 voices，具体需要查询模型 API
    
    /** 英文 Piper 模型的默认 voice */
    EN_US_DEFAULT("en_US-default", "Default", "en", VoiceType.ENGLISH, VoiceGender.NEUTRAL);
    
    companion object {
        /**
         * 根据 voiceId 查找对应的枚举
         */
        fun fromVoiceId(voiceId: String): PiperVoiceEnum? {
            return entries.firstOrNull { it.voiceId == voiceId }
        }
        
        /**
         * 获取所有中文 voices
         */
        fun getChineseVoices(): List<PiperVoiceEnum> {
            return entries.filter { it.voiceType == VoiceType.CHINESE }
        }
        
        /**
         * 获取所有英文 voices
         */
        fun getEnglishVoices(): List<PiperVoiceEnum> {
            return entries.filter { it.voiceType == VoiceType.ENGLISH }
        }
        
        /**
         * 获取默认 voice
         */
        fun getDefaultVoice(): PiperVoiceEnum {
            return entries.firstOrNull { it.isDefault } ?: ZH_CN_HUAYAN
        }
        
        /**
         * 根据模型 ID 获取推荐的 voice
         * 注意：Piper 模型的 voice 通常需要从模型 API 动态获取
         */
        fun getRecommendedVoiceForModel(modelId: String): PiperVoiceEnum? {
            return when {
                modelId.contains("zh_CN") || modelId.contains("zh-CN") -> {
                    getChineseVoices().firstOrNull { it.isDefault } ?: ZH_CN_HUAYAN
                }
                modelId.contains("en_US") || modelId.contains("en-US") -> {
                    getEnglishVoices().firstOrNull { it.isDefault } ?: EN_US_DEFAULT
                }
                else -> getDefaultVoice()
            }
        }
    }
}
