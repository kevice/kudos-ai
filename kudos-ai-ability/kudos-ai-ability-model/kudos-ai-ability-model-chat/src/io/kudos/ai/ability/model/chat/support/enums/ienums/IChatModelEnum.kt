package io.kudos.ai.ability.model.chat.support.enums.ienums

import io.kudos.ai.ability.model.common.IAIModel

/**
 * chat模型枚举接口
 *
 * @author K
 * @since 1.0.0
 */
interface IChatModelEnum : IAIModel {

    /**
     * 参数数量，单位B
     */
    val parameters: Float

    /**
     * 包尺寸(体积)，单位GB
     */
    val size: Float

    /**
     * 提供商
     */
    val provider: String

}