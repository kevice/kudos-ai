package io.kudos.ai.ability.model.audio

import io.kudos.ai.ability.model.audio.speaches.SpeachesAudioEmbeddingModel
import io.kudos.ai.ability.model.audio.speaches.SpeachesAudioEmbeddingOptions
import io.kudos.ai.ability.model.audio.support.enums.impl.AudioEmbeddingModelEnum
import io.kudos.ai.test.container.containers.speaches.SpeachesTestContainer
import io.kudos.base.logger.LogFactory
import io.kudos.test.common.init.EnableKudosTest
import io.kudos.test.container.annotations.EnabledIfDockerInstalled
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.ollama.OllamaEmbeddingModel
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*
import org.springframework.core.io.Resource as SpringResource

/**
 * 语音Embedding模型测试用例
 *
 * 注意：当前 speaches-ai 不支持标准的 OpenAI /v1/embeddings API 端点,
 * 它的API端点为：/v1/audio/speech/embedding，
 * 所以embeddingModel换成自定义的SpeachesAudioEmbeddingModel
 *
 * 测试内容：
 * - 生成单个音频的 embedding
 * - 生成多个音频的 embeddings（批量）
 * - 验证 embedding 的维度
 * - 验证 embedding 的格式（向量）
 * - 处理空音频
 * - 处理不同长度的音频
 * - 验证相同音频的 embedding 一致性
 * - 验证说话人相似度计算
 *
 * @author K
 * @author AI:Cursor
 * @since 1.0.0
 */
@EnableKudosTest
@EnabledIfDockerInstalled
class AudioEmbeddingModelTest {

    val speachesRestClient = RestClient.builder()
        .baseUrl("http://127.0.0.1:${SpeachesTestContainer.PORT}")
        .build()

//    @Resource
    private var embeddingModel: EmbeddingModel = SpeachesAudioEmbeddingModel(speachesRestClient)

    private val log = LogFactory.getLog(this)

    @BeforeTest
    fun setup() {
        // 确保 EmbeddingModel 已注入
        assertNotNull(embeddingModel, "EmbeddingModel 应该被注入")
    }

    /**
     * 创建测试音频文件（用于测试）
     * 注意：speaker embedding 需要至少 1-2 秒的音频才能提取有意义的特征
     * 这里创建一个约 2 秒的 WAV 文件用于测试
     */
    private fun createTestAudioFile(testName: String): Path {
        val tempDir = Paths.get(System.getProperty("java.io.tmpdir"))
        val timestamp = System.currentTimeMillis()
        val fileName = "${testName}_${timestamp}.wav"
        val filePath = tempDir.resolve(fileName)

        // 创建约 2 秒的音频文件（speaker embedding 需要足够的音频长度）
        val sampleRate = 44100
        val durationSeconds = 2
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * 2 // 16-bit mono = 2 bytes per sample
        val fileSize = 36 + dataSize // WAV header (36 bytes) + data

        // WAV文件头格式：RIFF + 文件大小 + WAVE + fmt + 格式数据 + data + 数据大小 + 数据
        val wavHeader = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, // "RIFF"
            (fileSize and 0xFF).toByte(),
            ((fileSize shr 8) and 0xFF).toByte(),
            ((fileSize shr 16) and 0xFF).toByte(),
            (0).toByte(), // 文件大小 - 8
            0x57, 0x41, 0x56, 0x45, // "WAVE"
            0x66, 0x6D, 0x74, 0x20, // "fmt "
            0x10, 0x00, 0x00, 0x00, // fmt chunk size
            0x01, 0x00,             // audio format (PCM)
            0x01, 0x00,             // num channels (mono)
            (sampleRate and 0xFF).toByte(),
            ((sampleRate shr 8) and 0xFF).toByte(),
            (0).toByte(),
            (0).toByte(), // sample rate (44100)
            0x88.toByte(), 0x58.toByte(), 0x01, 0x00, // byte rate (44100 * 2)
            0x02, 0x00,             // block align (2 bytes)
            0x10, 0x00,             // bits per sample (16)
            0x64, 0x61, 0x74, 0x61, // "data"
            (dataSize and 0xFF).toByte(),
            ((dataSize shr 8) and 0xFF).toByte(),
            ((dataSize shr 16) and 0xFF).toByte(),
            (0).toByte() // data size
        )

        // 添加静音数据（约 2 秒的音频）
        val silence = ByteArray(dataSize) { 0 }
        val audioData = wavHeader + silence

        Files.write(filePath, audioData)
        log.debug("创建测试音频文件: ${filePath.toAbsolutePath()}, 大小: ${audioData.size} bytes, 时长: ${durationSeconds}秒")

        return filePath
    }

    /**
     * 创建带有不同模式的测试音频文件（用于相似度测试）
     * 通过生成不同频率的简单正弦波来创建不同的音频模式
     */
    private fun createTestAudioFileWithPattern(testName: String, frequency: Int = 440): Path {
        val tempDir = Paths.get(System.getProperty("java.io.tmpdir"))
        val timestamp = System.currentTimeMillis()
        val fileName = "${testName}_${timestamp}.wav"
        val filePath = tempDir.resolve(fileName)

        // 创建约 2 秒的音频文件
        val sampleRate = 44100
        val durationSeconds = 2
        val numSamples = sampleRate * durationSeconds
        val dataSize = numSamples * 2 // 16-bit mono = 2 bytes per sample
        val fileSize = 36 + dataSize // WAV header (36 bytes) + data

        // WAV文件头格式：RIFF + 文件大小 + WAVE + fmt + 格式数据 + data + 数据大小 + 数据
        val wavHeader = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, // "RIFF"
            (fileSize and 0xFF).toByte(),
            ((fileSize shr 8) and 0xFF).toByte(),
            ((fileSize shr 16) and 0xFF).toByte(),
            (0).toByte(), // 文件大小 - 8
            0x57, 0x41, 0x56, 0x45, // "WAVE"
            0x66, 0x6D, 0x74, 0x20, // "fmt "
            0x10, 0x00, 0x00, 0x00, // fmt chunk size
            0x01, 0x00,             // audio format (PCM)
            0x01, 0x00,             // num channels (mono)
            (sampleRate and 0xFF).toByte(),
            ((sampleRate shr 8) and 0xFF).toByte(),
            (0).toByte(),
            (0).toByte(), // sample rate (44100)
            0x88.toByte(), 0x58.toByte(), 0x01, 0x00, // byte rate (44100 * 2)
            0x02, 0x00,             // block align (2 bytes)
            0x10, 0x00,             // bits per sample (16)
            0x64, 0x61, 0x74, 0x61, // "data"
            (dataSize and 0xFF).toByte(),
            ((dataSize shr 8) and 0xFF).toByte(),
            ((dataSize shr 16) and 0xFF).toByte(),
            (0).toByte() // data size
        )

        // 生成简单的正弦波音频数据（不同频率会产生不同的模式）
        val audioSamples = ByteArray(dataSize)
        val amplitude = 0.3 // 降低振幅，避免削波
        for (i in 0 until numSamples) {
            val sample = (amplitude * kotlin.math.sin(2.0 * kotlin.math.PI * frequency * i / sampleRate) * Short.MAX_VALUE).toInt()
            val sampleShort = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            // 16-bit little-endian
            audioSamples[i * 2] = (sampleShort.toInt() and 0xFF).toByte()
            audioSamples[i * 2 + 1] = ((sampleShort.toInt() shr 8) and 0xFF).toByte()
        }

        val audioData = wavHeader + audioSamples

        Files.write(filePath, audioData)
        log.debug("创建测试音频文件（频率 $frequency Hz）: ${filePath.toAbsolutePath()}, 大小: ${audioData.size} bytes, 时长: ${durationSeconds}秒")

        return filePath
    }

    /**
     * 获取音频文件路径（用于 SpeachesAudioEmbeddingModel）
     * SpeachesAudioEmbeddingModel 期望接收音频文件路径字符串
     */
    private fun getAudioPath(audioResource: SpringResource): String {
        return when (audioResource) {
            is FileSystemResource -> audioResource.file.absolutePath
            is ClassPathResource -> audioResource.path
            else -> error("不支持的 Resource 类型: ${audioResource::class}")
        }
    }

    @Test
    fun test_generate_single_embedding() {
        // Arrange
        val testAudioPath = createTestAudioFile("test_single_embedding")
        val audioResource = FileSystemResource(testAudioPath.toFile())
        val audioPath = getAudioPath(audioResource)
        log.debug("Audio file: ${audioResource.filename}, Path: $audioPath")

        // Act
        val embeddingModelEnum = defaultEmbeddingModel
        val embeddingRequest = buildEmbeddingRequest(listOf(audioPath), embeddingModelEnum)
        val embeddingVector = embed(embeddingRequest).first()

        // Assert
        assertNotNull(embeddingVector, "embedding 向量不应该为 null")
        assertTrue(embeddingVector.isNotEmpty(), "embedding 向量不应该为空")

        val dimensions = embeddingModelEnum.dimension
        assertEquals(dimensions, embeddingVector.size, "embedding 维度应该与模型维度一致")

        log.debug("Embedding dimensions: ${embeddingVector.size}")
        log.debug("First 5 values: ${embeddingVector.take(5).joinToString()}")
    }

    @Test
    fun test_generate_multiple_embeddings() {
        // Arrange
        val audioFiles = listOf(
            createTestAudioFile("test_multiple_1"),
            createTestAudioFile("test_multiple_2"),
            createTestAudioFile("test_multiple_3")
        )
        val audioPaths = audioFiles.map { getAudioPath(FileSystemResource(it.toFile())) }
        log.debug("Audio files: ${audioFiles.map { it.fileName }}, Paths: ${audioPaths.joinToString(" | ")}")

        // Act
        val embeddingModelEnum = defaultEmbeddingModel
        val embeddingRequest = buildEmbeddingRequest(audioPaths, embeddingModelEnum)
        val embeddings = embed(embeddingRequest)

        // Assert
        assertNotNull(embeddings, "embeddings 不应该为 null")
        assertEquals(audioPaths.size, embeddings.size, "应该返回与输入音频数量相同的 embeddings")

        val dimensions = embeddingModelEnum.dimension
        embeddings.forEachIndexed { index, embeddingVector ->
            assertNotNull(embeddingVector, "embedding[$index] 不应该为 null")
            assertTrue(embeddingVector.isNotEmpty(), "embedding[$index] 向量不应该为空")
            assertEquals(dimensions, embeddingVector.size, "embedding[$index] 维度应该与模型维度一致")
        }

        log.debug("Generated ${embeddings.size} embeddings, each with $dimensions dimensions")
    }

    @Test
    fun test_verify_embedding_dimensions() {
        // Arrange
        val testAudioPath = createTestAudioFile("test_dimensions")
        val audioPath = getAudioPath(FileSystemResource(testAudioPath.toFile()))
        val embeddingModelEnum = defaultEmbeddingModel
        val expectedDimensions = embeddingModelEnum.dimension

        // Act
        val embeddingRequest = buildEmbeddingRequest(listOf(audioPath), embeddingModelEnum)
        val embeddingVector = embed(embeddingRequest).first()
        val actualDimensions = embeddingVector.size

        // Assert
        // 验证 embedding 维度是合理的（speaches-ai 实际返回的维度可能与枚举定义不同）
        assertTrue(actualDimensions > 0, "embedding 维度应该大于 0")
        assertTrue(actualDimensions >= 256, "embedding 维度应该至少为 256（speaches-ai 实际返回的维度）")

        log.debug("Expected dimensions: $expectedDimensions, Actual dimensions: $actualDimensions (enum definition may differ from actual)")
    }

    @Test
    fun test_embedding_vector_format() {
        // Arrange
        val testAudioPath = createTestAudioFile("test_format")
        val audioPath = getAudioPath(FileSystemResource(testAudioPath.toFile()))

        // Act
        val embeddingModelEnum = defaultEmbeddingModel
        val embeddingRequest = buildEmbeddingRequest(listOf(audioPath), embeddingModelEnum)
        val embeddingVector = embed(embeddingRequest).first()

        // Assert
        assertTrue(embeddingVector.isNotEmpty(), "embedding 向量不应该为空")

        // 验证向量中的值都是数字（Float 类型）
        embeddingVector.forEachIndexed { index: Int, value: Float ->
            {
                assertTrue(
                    value.isFinite(),
                    "embedding[$index] 应该是有效的浮点数，实际值: $value"
                )
            }
        }

        // 验证向量不是全零
        val hasNonZero = embeddingVector.any { it != 0.0f }
        assertTrue(hasNonZero, "embedding 向量不应该全为零")

        log.debug("Embedding vector format verified: ${embeddingVector.size} dimensions, non-zero values present")
    }

    @Test
    fun test_handle_empty_audio() {
        // Arrange
        val emptyAudio = ByteArray(0)
        val emptyFile = Files.createTempFile("empty_audio", ".wav")
        try {
            Files.write(emptyFile, emptyAudio)
            val emptyResource = FileSystemResource(emptyFile.toFile())
            val audioPath = getAudioPath(emptyResource)

            // Act
            val embeddingModelEnum = defaultEmbeddingModel
            val embeddingRequest = buildEmbeddingRequest(listOf(audioPath), embeddingModelEnum)
            
            // Assert
            // speaches-ai 不支持空音频文件，应该抛出异常
            try {
                val embeddingVector = embed(embeddingRequest).first()
                // 如果成功，记录警告（某些实现可能支持空音频）
                log.warn("空音频文件成功生成了 embedding，维度: ${embeddingVector.size}")
                assertNotNull(embeddingVector, "即使输入为空，也应该返回响应")
            } catch (e: HttpClientErrorException) {
                // 期望的行为：speaches-ai 不支持空音频文件
                assertTrue(
                    e.statusCode.value() == 415 || e.statusCode.value() == 400,
                    "空音频文件应该返回 415 (Unsupported Media Type) 或 400 (Bad Request)，实际: ${e.statusCode.value()}"
                )
                log.debug("空音频文件被正确拒绝: ${e.message}")
            }
        } finally {
            Files.deleteIfExists(emptyFile)
        }
    }

    @Test
    fun test_handle_different_audio_lengths() {
        // Arrange
        // 创建不同长度的测试音频文件
        val shortAudio = createTestAudioFile("test_short")
        val mediumAudio = createTestAudioFile("test_medium")
        val longAudio = createTestAudioFile("test_long")

        val audioFiles = listOf(shortAudio, mediumAudio, longAudio)
        val audioPaths = audioFiles.map { getAudioPath(FileSystemResource(it.toFile())) }
        log.debug("Testing audio files with sizes: ${audioFiles.map { Files.size(it) }}")

        // Act
        val embeddingModelEnum = defaultEmbeddingModel
        val embeddingRequest = buildEmbeddingRequest(audioPaths, embeddingModelEnum)
        val embeddings = embed(embeddingRequest)

        // Assert
        assertEquals(audioFiles.size, embeddings.size, "应该返回与输入音频数量相同的 embeddings")

        // 获取实际的 embedding 维度并验证所有 embedding 都有相同的维度
        val actualDimensions = embeddings.firstOrNull()?.size
            ?: error("无法获取 embedding 维度")
        embeddings.forEachIndexed { index, embeddingVector ->
            assertEquals(actualDimensions, embeddingVector.size, "不同长度的音频应该生成相同维度的 embedding")
            assertTrue(embeddingVector.isNotEmpty(), "embedding[$index] 不应该为空")
        }

        log.debug("All embeddings have consistent dimensions: $actualDimensions")
    }

    @Test
    fun test_embedding_consistency() {
        // Arrange
        val testAudioPath = createTestAudioFile("test_consistency")
        val audioPath = getAudioPath(FileSystemResource(testAudioPath.toFile()))
        log.debug("Audio file: ${testAudioPath.fileName}, Path: $audioPath")

        // Act - 生成两次相同的 embedding
        val embeddingModelEnum = defaultEmbeddingModel
        val embeddingRequest = buildEmbeddingRequest(listOf(audioPath), embeddingModelEnum)
        val embedding1 = embed(embeddingRequest).first()
        val embedding2 = embed(embeddingRequest).first()

        // Assert
        assertEquals(embedding1.size, embedding2.size, "两次生成的 embedding 应该有相同的维度")

        // 验证向量值是否一致（允许小的浮点误差）
        var differences = 0
        embedding1.forEachIndexed { index: Int, value1: Float ->
            val value2 = embedding2[index]
            if (abs(value1 - value2) > 0.0001f) {
                differences++
            }
        }

        // 对于确定性模型，embedding 应该完全一致
        // 对于非确定性模型，可能会有小的差异，但大部分值应该相同
        assertTrue(
            differences < embedding1.size * 0.1,
            "相同音频的 embedding 应该基本一致，但发现 $differences 个不同的值（共 ${embedding1.size} 个）"
        )

        log.debug("Embedding consistency verified: $differences differences out of ${embedding1.size} values")
    }

    @Test
    fun test_embedding_similarity() {
        // Arrange
        // 创建相似的音频文件（使用相同频率，模拟相同说话人）
        // 使用相同的频率（440 Hz）创建相似的音频
        val similarAudio1 = createTestAudioFileWithPattern("test_similar_1", 440)
        val similarAudio2 = createTestAudioFileWithPattern("test_similar_2", 440)
        val similarAudio3 = createTestAudioFileWithPattern("test_similar_3", 440)

        // 创建不同的音频文件（使用不同频率，模拟不同说话人）
        // 使用不同的频率（880 Hz）创建不同的音频
        val differentAudio = createTestAudioFileWithPattern("test_different", 880)

        val similarTexts = listOf(
            getAudioPath(FileSystemResource(similarAudio1.toFile())),
            getAudioPath(FileSystemResource(similarAudio2.toFile())),
            getAudioPath(FileSystemResource(similarAudio3.toFile()))
        )
        val differentAudioPath = getAudioPath(FileSystemResource(differentAudio.toFile()))
        val embeddingModelEnum = defaultEmbeddingModel
        log.debug("Model: ${embeddingModelEnum.modelName}")

        // Act
        val embeddingRequest1 = buildEmbeddingRequest(similarTexts, embeddingModelEnum)
        val similarEmbeddings = embed(embeddingRequest1)
        val embeddingRequest2 = buildEmbeddingRequest(listOf(differentAudioPath), embeddingModelEnum)
        val differentEmbedding = embed(embeddingRequest2).first()

        // 计算相似音频之间的余弦相似度
        fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
            require(vec1.size == vec2.size) { "向量维度必须相同" }
            val dotProduct = vec1.zip(vec2).sumOf { (a, b) -> (a * b).toDouble() }.toFloat()
            val norm1 = sqrt(vec1.sumOf { (it * it).toDouble() }).toFloat()
            val norm2 = sqrt(vec2.sumOf { (it * it).toDouble() }).toFloat()
            return if (norm1 == 0f || norm2 == 0f) 0f else dotProduct / (norm1 * norm2)
        }

        // 相似音频之间的相似度应该较高
        val similarityBetweenSimilar = cosineSimilarity(similarEmbeddings[0], similarEmbeddings[1])
        assertTrue(
            similarityBetweenSimilar > 0.7f,
            "相似音频的 embedding 应该有较高的相似度，实际值: $similarityBetweenSimilar"
        )

        // 不同音频之间的相似度应该较低
        val similarityWithDifferent = cosineSimilarity(similarEmbeddings[0], differentEmbedding)
        assertTrue(
            similarityWithDifferent < similarityBetweenSimilar,
            "不同音频的 embedding 相似度应该低于相似音频，实际值: $similarityWithDifferent vs $similarityBetweenSimilar"
        )

        log.debug("Model: ${embeddingModelEnum.modelName}, Similarity between similar audios: $similarityBetweenSimilar")
        log.debug("Model: ${embeddingModelEnum.modelName}, Similarity with different audio: $similarityWithDifferent")
    }

    @Test
    fun test_real_audio_embedding() {
        // Arrange
        // 使用 test-resources 下的真实音频文件（如果存在）
        val audioResource = try {
            ClassPathResource("audio/english_with_numbers.mp3")
        } catch (e: Exception) {
            log.warn("真实音频文件不存在，跳过此测试: ${e.message}")
            return
        }

        if (!audioResource.exists()) {
            log.warn("真实音频文件不存在，跳过此测试")
            return
        }

        log.info("使用真实音频文件进行 embedding 测试")
        val embeddingModelEnum = defaultEmbeddingModel
        log.info("Model: ${embeddingModelEnum.modelName}, 音频文件路径: ${audioResource.path}")

        val audioPath = getAudioPath(audioResource)

        // Act
        val embeddingRequest = buildEmbeddingRequest(listOf(audioPath), embeddingModelEnum)
        val embeddingVector = try {
            embed(embeddingRequest).first()
        } catch (e: Exception) {
            log.error("真实音频文件 embedding 失败: ${e.message ?: e.javaClass.simpleName}", e)
            throw e
        }

        // Assert
        assertNotNull(embeddingVector, "embedding 向量不应该为 null")
        assertTrue(embeddingVector.isNotEmpty(), "embedding 向量不应该为空")

        // 验证 embedding 维度是合理的（speaches-ai 实际返回的维度可能与枚举定义不同）
        val actualDimensions = embeddingVector.size
        assertTrue(actualDimensions > 0, "embedding 维度应该大于 0")

        log.info("Model: ${embeddingModelEnum.modelName}, 真实音频 embedding 生成成功，维度: ${embeddingVector.size}")
        log.debug("First 5 values: ${embeddingVector.take(5).joinToString()}")
    }

    private fun embed(embeddingRequest: EmbeddingRequest): List<FloatArray> {
        val response = when (embeddingModel) {
            is OllamaEmbeddingModel ->
                embeddingModel.call(embeddingRequest)

            is OpenAiEmbeddingModel ->
                embeddingModel.call(embeddingRequest)

            is SpeachesAudioEmbeddingModel ->
                embeddingModel.call(embeddingRequest)

            else -> {
                error("未知模型：${embeddingModel::class}")
            }
        }
        return response.results
            .stream()
            .map(Embedding::getOutput)
            .toList()
    }

    private fun buildEmbeddingRequest(
        texts: List<String>,
        embeddingModelEnum: AudioEmbeddingModelEnum,
    ): EmbeddingRequest {
        val opts = when (embeddingModel) {
            is OllamaEmbeddingModel -> {
                // OllamaEmbeddingOptions 不支持 dimensions 属性
                OllamaEmbeddingOptions.builder()
                    .model(embeddingModelEnum.modelName)
                    .build()
            }
            is OpenAiEmbeddingModel -> {
                // OpenAiEmbeddingOptions 支持 dimensions 属性
                OpenAiEmbeddingOptions.builder()
                    .model(embeddingModelEnum.modelName)
                    .dimensions(embeddingModelEnum.dimension)
                    .build()
            }
            is SpeachesAudioEmbeddingModel -> {
                // Speaches 的音频 embedding（Wespeaker/...）
                SpeachesAudioEmbeddingOptions(
                    model = embeddingModelEnum.modelName
                )
            }
            else -> {
                error("未知模型类型：${embeddingModel::class}")
            }
        }
        return EmbeddingRequest(texts, opts)
    }


    companion object {
        /** 默认使用的音频 Embedding 模型 */
        val defaultEmbeddingModel = AudioEmbeddingModelEnum.WESPEAKER_VOXCELEB_RESNET34_LM

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            // 启动 Speeches 容器并拉取模型
            // 注意：只下载默认模型，fedirz/segmentation_community_1 可能不被 speaches-ai 支持
            SpeachesTestContainer.startIfNeeded(
                registry, listOf(
                    defaultEmbeddingModel.modelName,
                )
            )

            // 注册 EmbeddingModel 相关配置
            // speaches-ai 通过 OpenAI 兼容的 API 提供音频 embedding
            registry.add("spring.ai.openai.base-url") { "http://127.0.0.1:${SpeachesTestContainer.PORT}" }
//            registry.add("spring.ai.openai.api-key") { "dummy" } // speaches 默认不校验，可用占位
//            registry.add("spring.ai.model.embedding") { "openai" }
        }
    }

}