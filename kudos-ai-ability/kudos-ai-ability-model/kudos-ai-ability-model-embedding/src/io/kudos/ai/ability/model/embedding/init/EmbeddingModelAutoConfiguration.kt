package io.kudos.ai.ability.model.embedding.init

import io.kudos.context.init.IComponentInitializer
import org.springframework.context.annotation.Configuration

/**
 * EmbeddingModel自动配置类
 *
 * @author K
 * @since 1.0.0
 */
@Configuration
open class EmbeddingModelAutoConfiguration : IComponentInitializer {

    override fun getComponentName() = "kudos-ability-model-embedding"

}
