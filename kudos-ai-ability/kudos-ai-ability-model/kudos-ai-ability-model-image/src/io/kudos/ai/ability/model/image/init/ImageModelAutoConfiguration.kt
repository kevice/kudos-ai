package io.kudos.ai.ability.model.image.init

import io.kudos.context.init.IComponentInitializer
import org.springframework.context.annotation.Configuration

/**
 * ImageModel自动配置类
 *
 * @author K
 * @since 1.0.0
 */
@Configuration
open class ImageModelAutoConfiguration : IComponentInitializer {

    override fun getComponentName() = "kudos-ability-model-image"

}
