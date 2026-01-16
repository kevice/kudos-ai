package io.kudos.ai.ability.model.audio.support.enums.ienums

import io.kudos.ai.ability.model.audio.support.enums.impl.AudioModelTypeEnum
import io.kudos.ai.ability.model.common.support.enums.ienum.IAIModel

/**
 * STT (Speech-to-Text) 语音转文本模型枚举接口
 *
 * @author K
 * @author AI:cursor
 * @since 1.0.0
 */
interface ISTTModelEnum : IAIModel {

    /** 语音模型类型 */
    val audioModelType: AudioModelTypeEnum
        get() = AudioModelTypeEnum.STT

}
