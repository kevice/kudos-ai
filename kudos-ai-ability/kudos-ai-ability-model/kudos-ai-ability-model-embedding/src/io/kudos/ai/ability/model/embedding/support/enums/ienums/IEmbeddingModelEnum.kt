package io.kudos.ai.ability.model.embedding.support.enums.ienums

import io.kudos.ai.ability.model.common.IAIModelMetaData

/**
 * embedding模型枚举接口
 *
 * @author K
 * @since 1.0.0
 */
interface IEmbeddingModelEnum : IAIModelMetaData {

    /**
     * 维度
     */
    val dimension: Int

}