package io.kudos.ai.ability.model.audio.speaches

import org.springframework.ai.embedding.EmbeddingOptions

/**
 * Speaches 的音频 embedding 只需要 model。
 * dimensions 在 Speaches audio embedding 场景不使用，这里返回 -1 作为占位。
 *
 * @author K
 * @author AI: ChatGPT
 * @since 1.0.0
 */
class SpeachesAudioEmbeddingOptions(
    private val model: String,
    private val dimensions: Int = -1,
) : EmbeddingOptions {

    override fun getModel(): String = model

    override fun getDimensions(): Int = dimensions
}
