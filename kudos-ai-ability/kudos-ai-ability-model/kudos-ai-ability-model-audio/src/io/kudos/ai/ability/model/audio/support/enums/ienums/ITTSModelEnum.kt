package io.kudos.ai.ability.model.audio.support.enums.ienums

import io.kudos.ai.ability.model.audio.support.enums.impl.AudioModelTypeEnum
import io.kudos.ai.ability.model.common.support.enums.ienum.IAIModel

/**
 * TTS (Text-to-Speech) 文本转语音模型枚举接口
 *
 * @param VoiceEnumType 该模型关联的 Voice 枚举类型
 * @author K
 * @author AI: Cursor
 * @since 1.0.0
 */
interface ITTSModelEnum : IAIModel {

    /** 语音模型类型 */
    val audioModelType: AudioModelTypeEnum
        get() = AudioModelTypeEnum.TTS
    
    /**
     * 获取该模型推荐的默认 Voice 枚举
     * 
     * @return Voice 枚举，如果该模型不支持 voice 枚举则返回 null
     */
    fun getDefaultVoice(): IVoiceEnum?

    fun getDefaultChineseVoice(): IVoiceEnum?

    /**
     * 获取该模型推荐的英文 Voice 枚举
     *
     * @return 英文 Voice 枚举，如果该模型不支持英文则返回 null
     */
    fun getDefaultEnglishVoice(): IVoiceEnum?

    /**
     * 根据 voiceId 查找该模型对应的 Voice 枚举
     * 
     * @param voiceId Voice ID
     * @return Voice 枚举，如果找不到则返回 null
     */
    fun findVoice(voiceId: String): IVoiceEnum?
    
    /**
     * 获取该模型支持的所有 Voice 枚举列表
     * 
     * @return Voice 枚举列表，如果该模型不支持 voice 枚举则返回空列表
     */
    fun getAvailableVoices(): List<IVoiceEnum> = emptyList()

}
