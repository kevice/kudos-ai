package io.kudos.ai.ability.model.audio.speaches

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 适配 Speaches 的音频 embedding。
 *
 * 约定：
 * - EmbeddingRequest.instructions(List<String>) 的每个字符串 = 音频文件路径（本地路径 or classpath 相对路径都可）
 * - options 必须是 SpeachesAudioEmbeddingOptions，里面带 model（例如 "Wespeaker/wespeaker-voxceleb-resnet34-LM"）
 *
 * Speaches API：POST /v1/audio/speech/embedding
 *
 * @author K
 * @author AI: ChatGPT
 * @author AI: Cursor
 * @since 1.0.0
 */
class SpeachesAudioEmbeddingModel(
    private val restClient: RestClient, // baseUrl 指向 Speaches，例如 http://localhost:28001
    private val defaultModel: String? = null // 默认模型名称（可选）
) : EmbeddingModel {

    override fun call(request: EmbeddingRequest): EmbeddingResponse {
        val opts = request.options as? SpeachesAudioEmbeddingOptions
            ?: error("SpeachesAudioEmbeddingModel 需要 SpeachesAudioEmbeddingOptions，但实际是：${request.options?.javaClass}")

        val modelName = opts.model
        val inputs = request.instructions ?: emptyList()
        require(inputs.isNotEmpty()) { "EmbeddingRequest.instructions 为空" }

        val embeddings = inputs.mapIndexed { index, audioPathString ->
            createEmbedding(audioPathString, modelName, index)
        }

        return EmbeddingResponse(embeddings)
    }

    override fun embed(document: Document): FloatArray {
        // 从 Document 的 content 或 metadata 中获取音频文件路径
        // Spring AI Document 使用 getContent() 方法获取内容
        val content = document.formattedContent
        val audioPath = when {
            !content.isNullOrBlank() -> content
            else -> document.metadata["audioPath"] as? String
                ?: error("Document 必须包含音频文件路径（content 或 metadata['audioPath']）")
        }

        val modelName = defaultModel
            ?: (document.metadata["model"] as? String)
            ?: error("必须指定模型名称（通过 defaultModel 或 metadata['model']）")

        val embedding = createEmbedding(audioPath, modelName, 0)
        return embedding.output
    }

    /**
     * 创建单个音频的 embedding
     */
    private fun createEmbedding(audioPathString: String, modelName: String, index: Int): Embedding {
        val audioBytes = readAudioBytes(audioPathString)

        val bodyBuilder = MultipartBodyBuilder().apply {
            part("model", modelName)
            part("file", audioBytes)
                .filename(guessFilename(audioPathString))
                .contentType(guessContentType(audioPathString))
        }

        val resp = restClient.post()
            .uri("/v1/audio/speech/embedding")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(bodyBuilder.build())
            .retrieve()
            .body(Map::class.java)
            ?: error("Speaches 返回空响应")

        val vector = extractEmbeddingVector(resp)
            ?: error("无法从 Speaches 响应解析 embedding，响应：$resp")

        return Embedding(vector.toFloatArray(), index)
    }

    private fun readAudioBytes(pathOrResource: String): ByteArray {
        // 1) 先当作本地路径
        val p: Path = Paths.get(pathOrResource)
        if (Files.exists(p) && Files.isRegularFile(p)) {
            return Files.readAllBytes(p)
        }

        // 2) 再当作 classpath resource（例如 "audio/english_with_numbers.mp3"）
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(pathOrResource)
            ?: error("找不到音频文件：'$pathOrResource'（既不是本地文件，也不是 classpath resource）")
        return url.openStream().use { it.readBytes() }
    }

    private fun guessFilename(path: String): String {
        return path.substringAfterLast('/', path.substringAfterLast('\\', "audio.mp3"))
            .takeIf { it.isNotBlank() } ?: "audio.mp3"
    }

    private fun guessContentType(path: String): MediaType {
        val extension = path.substringAfterLast('.').lowercase()
        return when (extension) {
            "wav" -> MediaType.parseMediaType("audio/wav")
            "m4a" -> MediaType.parseMediaType("audio/mp4")
            "aac" -> MediaType.parseMediaType("audio/aac")
            "ogg" -> MediaType.parseMediaType("audio/ogg")
            "flac" -> MediaType.parseMediaType("audio/flac")
            else -> MediaType.parseMediaType("audio/mpeg") // mp3 默认
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractEmbeddingVector(resp: Map<*, *>): List<Float>? {
        // 兼容两种常见形态：
        // A) {"embedding":[0.1, 0.2, ...]}
        val direct = resp["embedding"]
        if (direct is List<*>) {
            return direct.mapNotNull { (it as? Number)?.toFloat() }
        }

        // B) {"data":[{"embedding":[...]}]}
        val data = resp["data"]
        if (data is List<*> && data.isNotEmpty()) {
            val first = data[0] as? Map<*, *> ?: return null
            val emb = first["embedding"]
            if (emb is List<*>) {
                return emb.mapNotNull { (it as? Number)?.toFloat() }
            }
        }

        return null
    }
}
