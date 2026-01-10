package io.kudos.ai.ability.model.chat.init

import io.kudos.context.init.IComponentInitializer
import org.springframework.context.annotation.Configuration

/**
 * ChatModel自动配置类
 *
 * @author K
 * @since 1.0.0
 */
@Configuration
open class ChatModelAutoConfiguration : IComponentInitializer {

    override fun getComponentName() = "kudos-ability-model-chat"

}
