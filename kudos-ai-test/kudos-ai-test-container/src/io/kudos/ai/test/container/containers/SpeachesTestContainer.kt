package io.kudos.ai.test.container.containers

import com.github.dockerjava.api.model.Container
import io.kudos.test.container.kit.TestContainerKit
import io.kudos.test.container.kit.bindingPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * speeches-ai测试容器
 *
 * 兼具STT(speech-to-text)、TTS(text-to-speech)和VAD(voice-activity-detection)的本地语音能力，
 * "OpenAI API 兼容"的服务端，HTTP 接口接入。
 *
 * @author K
 * @author AI:Cursor
 * @since 1.0.0
 */
object SpeachesTestContainer {

    private const val IMAGE_NAME = "ghcr.io/speaches-ai/speaches:0.9.0-rc.3-cpu"

    const val PORT = 28001

    private const val CONTAINER_PORT = 8000

    const val LABEL = "Speeches"

    val container = GenericContainer(IMAGE_NAME).apply {
        // 创建宿主机目录用于持久化模型
        // speaches-ai 将模型存储在 /home/ubuntu/.cache/huggingface/hub 目录
        val hostModelDir: Path = Path.of(System.getProperty("user.home"), ".cache", "speaches-tc", "huggingface", "hub")
        Files.createDirectories(hostModelDir)
        
        // 使用绑定挂载（bind mount）以支持高效的数据持久化
        // 注意：speaches-ai 容器默认使用 /home/ubuntu/.cache/huggingface/hub
        withFileSystemBind(
            hostModelDir.toAbsolutePath().toString(),
            "/home/ubuntu/.cache/huggingface/hub",
            BindMode.READ_WRITE
        )
        
        withExposedPorts(CONTAINER_PORT)
        bindingPort(Pair(PORT, CONTAINER_PORT))
        
        // 环境变量配置
        withEnv("host", "0.0.0.0")
        withEnv("port", CONTAINER_PORT.toString())
        
        // 可选配置：模型 TTL（Time To Live）
        // -1 表示永不卸载，0 表示使用后立即卸载
        // 测试环境可以设置为 -1，避免频繁加载模型
        withEnv("stt_model_ttl", "-1")
        withEnv("tts_model_ttl", "-1")
        withEnv("vad_model_ttl", "-1")
        
        // 日志级别
        withEnv("log_level", "info")
        
        // 禁用 UI（测试环境通常不需要）
        withEnv("enable_ui", "False")
        
        // 等待 HTTP API 就绪
        // 使用健康检查端点
        // 注意：forPort 需要使用容器内部端口，而不是主机端口
        waitingFor(
            Wait.forHttp("/health")
                .forPort(CONTAINER_PORT)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(5))
        )
        
        withLabel(TestContainerKit.LABEL_KEY, LABEL)
    }

    /**
     * 模型类型枚举
     */
    enum class SpeachesModelType(val task: String) {
        STT("automatic-speech-recognition"),
        TTS("text-to-speech"),
        EMBEDDING("speaker-embedding")
    }

    /**
     * 获取指定模型类型的所有模型 ID 列表
     *
     * @param modelType 模型类型（STT、TTS 或 VAD）
     * @param baseUrl speaches API 的基础 URL
     * @param httpClient HTTP 客户端
     * @return 模型 ID 列表，如果查询失败则返回空列表
     */
    private fun getModelIdsFromRegistry(
        modelType: SpeachesModelType,
        baseUrl: String,
        httpClient: HttpClient
    ): List<String> {
        try {
            // 按模型类型查询 speaches 的模型注册表
            // 例如：/v1/registry/stt, /v1/registry/tts, /v1/registry/vad
            val registryRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/v1/registry?task=${modelType.task}"))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build()
            
            val registryResponse = httpClient.send(registryRequest, HttpResponse.BodyHandlers.ofString())
            
            if (registryResponse.statusCode() == 200) {
                val responseBody = registryResponse.body()
                
                // 解析注册表响应，提取模型 ID 列表
                val modelIds = parseModelIdsFromResponse(responseBody)
                println("Model ids of model type ${modelType.task} by Speeches-AI : $modelIds")
                return modelIds
            } else {
                println("WARN: Failed to query registry for ${modelType.task}, status code: ${registryResponse.statusCode()}")
                return emptyList()
            }
        } catch (e: Exception) {
            println("WARN: Error querying model registry for ${modelType.task}: ${e.message}")
            return emptyList()
        }
    }

    /**
     * 从响应中解析模型 ID 列表
     *
     * @param responseBody 响应体内容
     * @return 模型 ID 列表
     */
    private fun parseModelIdsFromResponse(responseBody: String): List<String> {
        val modelIds = mutableListOf<String>()
        
        try {
            val trimmedBody = responseBody.trim()
            
            // 如果响应是 JSON 数组格式，优先解析对象数组格式 [{"id": "model1", ...}, ...]
            if (trimmedBody.startsWith("[")) {
                // 使用正则表达式提取顶层对象的 "id" 字段值
                // 匹配格式: { "id" : "model-id", ... } 或 {"id": "model-id", ...}
                // 只匹配对象开头的 "id" 字段，避免匹配嵌套对象中的 id
                // 模型 ID 通常包含 "/"，如 "speaches-ai/model-name" 或 "Systran/faster-whisper-base"
                val idPattern = """\{\s*"id"\s*:\s*"([^"]+)"\s*,""".toRegex()
                val matches = idPattern.findAll(responseBody)
                matches.forEach { matchResult ->
                    val modelId = matchResult.groupValues[1]
                    // 模型 ID 通常包含 "/"，这样可以过滤掉嵌套对象中的 id（如 voices 中的 id）
                    if (modelId.isNotBlank() && modelId.contains("/")) {
                        modelIds.add(modelId)
                    }
                }
                
                // 如果上面的模式没有匹配到，尝试更宽松的模式
                if (modelIds.isEmpty()) {
                    // 匹配对象中的第一个 "id" 字段（通常是顶层对象的 id）
                    // 使用更精确的模式：匹配 { ... "id" : "value" ... } 中的 id
                    // 但只匹配包含 "/" 的 id 值（模型 ID 的特征）
                    val flexibleIdPattern = """"id"\s*:\s*"([^"]+/[^"]+)"\s*[,}]""".toRegex()
                    val flexibleMatches = flexibleIdPattern.findAll(responseBody)
                    flexibleMatches.forEach { matchResult ->
                        val modelId = matchResult.groupValues[1]
                        if (modelId.isNotBlank()) {
                            modelIds.add(modelId)
                        }
                    }
                }
                
                // 如果还是没有找到，可能是字符串数组格式 ["model1", "model2"]
                if (modelIds.isEmpty()) {
                    // 提取数组中的字符串值
                    val stringPattern = """\[\s*"([^"]+)"(?:\s*,\s*"([^"]+)")*\s*]""".toRegex()
                    val arrayMatch = stringPattern.find(responseBody)
                    if (arrayMatch != null) {
                        // 提取所有匹配的字符串
                        val allStringPattern = """"([^"]+)"""".toRegex()
                        val stringMatches = allStringPattern.findAll(arrayMatch.value)
                        stringMatches.forEach { matchResult ->
                            val value = matchResult.groupValues[1]
                            if (value.isNotBlank()) {
                                modelIds.add(value)
                            }
                        }
                    }
                }
            }
            // 如果响应包含 "models" 键，格式: {"models": [...]}
            else if (responseBody.contains("\"models\"")) {
                // 查找 "models": [...] 部分
                val modelsPattern = """"models"\s*:\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val modelsMatch = modelsPattern.find(responseBody)
                if (modelsMatch != null) {
                    val modelsArray = modelsMatch.groupValues[1]
                    // 如果是对象数组，提取 id 字段（只匹配包含 "/" 的 id，即模型 ID）
                    if (modelsArray.contains("\"id\"")) {
                        val idPattern = """"id"\s*:\s*"([^"]+/[^"]+)"\s*[,}]""".toRegex()
                        val matches = idPattern.findAll(modelsArray)
                        matches.forEach { matchResult ->
                            val modelId = matchResult.groupValues[1]
                            if (modelId.isNotBlank()) {
                                modelIds.add(modelId)
                            }
                        }
                    } else {
                        // 如果是字符串数组，直接提取
                        val modelPattern = """"([^"]+)"""".toRegex()
                        val matches = modelPattern.findAll(modelsArray)
                        matches.forEach { matchResult ->
                            val modelId = matchResult.groupValues[1]
                            if (modelId.isNotBlank()) {
                                modelIds.add(modelId)
                            }
                        }
                    }
                }
            }
            // 如果响应包含对象格式但不在数组中，格式: {"id": "model1", ...}
            else if (responseBody.contains("\"id\"") && !responseBody.trim().startsWith("[")) {
                // 只匹配包含 "/" 的 id 值（模型 ID 的特征）
                val idPattern = """"id"\s*:\s*"([^"]+/[^"]+)"\s*[,}]""".toRegex()
                val matches = idPattern.findAll(responseBody)
                matches.forEach { matchResult ->
                    val modelId = matchResult.groupValues[1]
                    if (modelId.isNotBlank()) {
                        modelIds.add(modelId)
                    }
                }
            }
            // 如果不是 JSON 格式，尝试按行解析（简单文本列表）
            else {
                responseBody.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                        // 可能是简单的模型 ID 列表
                        modelIds.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            println("WARN: Error parsing model IDs from response: ${e.message}")
            // 如果解析失败，返回空列表
        }
        
        return modelIds.distinct() // 去重
    }

    /**
     * 检查模型是否在 speeches 注册表中支持
     *
     * @param modelId 模型 ID（如 "Systran/faster-whisper-base"）
     * @param modelType 模型类型（STT、TTS 或 VAD）
     * @param baseUrl speeches API 的基础 URL
     * @param httpClient HTTP 客户端
     * @return 如果模型在注册表中支持则返回 true，否则返回 false
     */
    private fun isModelSupportedInRegistry(
        modelId: String,
        modelType: SpeachesModelType,
        baseUrl: String,
        httpClient: HttpClient
    ): Boolean {
        val modelIds = getModelIdsFromRegistry(modelType, baseUrl, httpClient)
        return modelIds.contains(modelId)
    }

    /**
     * 下载模型（如果不存在）
     *
     * @param modelId 模型 ID（如 "Systran/faster-whisper-base"）
     * @param speachesModelType 模型类型（STT、TTS 或 VAD）
     * @param runningContainer 运行中的容器对象
     */
    private fun downloadModelIfAbsent(modelId: String, speachesModelType: SpeachesModelType, runningContainer: Container) {
        val host = runningContainer.ports.first().ip
        val port = runningContainer.ports.first().publicPort
        val baseUrl = "http://$host:$port"
        
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        
        // 设置较长的超时时间，因为模型下载可能需要较长时间
        val requestTimeout = Duration.ofMinutes(10)
        
        try {
            // 0) 首先检查模型是否在 speaches 注册表中支持
            if (!isModelSupportedInRegistry(modelId, speachesModelType, baseUrl, httpClient)) {
                throw IllegalArgumentException(
                    "Model '$modelId' (type: ${speachesModelType.task}) is not supported in speaches registry. " +
                    "Please check available models at $baseUrl/v1/registry?task=${speachesModelType.task}"
                )
            }
            
            // 0.5) 对于非官方模型，给出警告
            if (!modelId.startsWith("speaches-ai/")) {
                println("WARN: Model '$modelId' is not an official speaches-ai model. " +
                        "It may have compatibility issues. Consider using official models like " +
                        "'speaches-ai/Kokoro-82M-v1.0-ONNX' for TTS or 'Systran/faster-whisper-*' for STT.")
            }
            
            // 1) 通过 API 检查模型是否已在容器中加载
            val listRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/v1/models"))
                .GET()
                .timeout(requestTimeout)
                .build()
            
            val listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString())
            
            if (listResponse.statusCode() == 200) {
                val responseBody = listResponse.body()
                
                // 检查模型是否已在容器中加载
                if (responseBody.contains(modelId)) {
                    println("Model already loaded in container: $modelId")
                    // 即使模型已加载，也等待一下确保完全就绪
                    waitForModelReady(modelId, speachesModelType, baseUrl, httpClient)
                    return
                }
            }
            
            // 2) 模型未加载，需要下载/加载
            // 首先检查宿主机目录中是否已有模型文件
            val hostModelDir: Path = Path.of(System.getProperty("user.home"), ".cache", "speaches-tc", "huggingface", "hub")
            val modelDirName = modelId.replace("/", "--")
            val modelPath = hostModelDir.resolve("models--$modelDirName")
            
            val modelFilesExist = if (hostModelDir.exists() && modelPath.exists()) {
                val files = try {
                    modelPath.listDirectoryEntries()
                } catch (_: Exception) {
                    emptyList()
                }
                files.isNotEmpty()
            } else {
                false
            }
            
            if (modelFilesExist) {
                println("DEBUG: Model found in host directory: $modelPath")
                println("Model files exist locally, but not loaded in container. Loading model: $modelId ...")
            } else {
                println("Start downloading model: $modelId ...")
            }
            
            val time = System.currentTimeMillis()
            
            // 构建下载/加载 URL
            // 注意：modelId 可能包含 '/'，这里需要 URL 编码成单个 path segment，
            // 否则路由可能会把它当成多段路径导致下载失败。
            val encodedModelId = URLEncoder.encode(modelId, StandardCharsets.UTF_8)
            val downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/v1/models/$encodedModelId"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(requestTimeout)
                .build()
            
            val downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofString())

            check(downloadResponse.statusCode() in 200..299) {
                "Failed to download/load model $modelId, status code: ${downloadResponse.statusCode()}, response: ${downloadResponse.body()}"
            }
            
            if (modelFilesExist) {
                println("Finish loading model: $modelId in ${System.currentTimeMillis() - time}ms")
            } else {
                println("Finish downloading model: $modelId in ${System.currentTimeMillis() - time}ms")
            }
            
            // 3) 等待模型完全加载并可用
            waitForModelReady(modelId, speachesModelType, baseUrl, httpClient)
        } catch (e: Exception) {
            throw RuntimeException("Error downloading/loading model $modelId: ${e.message}", e)
        }
    }

    /**
     * 等待模型完全加载并可用
     *
     * @param modelId 模型 ID
     * @param speachesModelType 模型类型
     * @param baseUrl speaches API 的基础 URL
     * @param httpClient HTTP 客户端
     */
    private fun waitForModelReady(
        modelId: String,
        speachesModelType: SpeachesModelType,
        baseUrl: String,
        httpClient: HttpClient
    ) {
        val maxWaitTime = Duration.ofMinutes(2)
        val checkInterval = Duration.ofSeconds(2)
        val startTime = System.currentTimeMillis()
        
        println("Waiting for model $modelId to be ready...")
        
        var modelFoundInList = false
        
        // 第一步：等待模型出现在已加载模型列表中
        while (System.currentTimeMillis() - startTime < maxWaitTime.toMillis()) {
            try {
                // 通过查询 /v1/models 端点来检查模型是否已加载
                val listRequest = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/v1/models"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build()
                
                val listResponse = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString())
                
                if (listResponse.statusCode() == 200) {
                    val responseBody = listResponse.body()
                    // 检查模型是否在已加载的模型列表中
                    if (responseBody.contains(modelId)) {
                        modelFoundInList = true
                        println("Model $modelId found in loaded models list.")
                        break
                    }
                }
            } catch (e: Exception) {
                // 忽略检查过程中的错误，继续等待
                println("DEBUG: Error checking model in list: ${e.message}")
            }
            
            // 等待一段时间后再次检查
            Thread.sleep(checkInterval.toMillis())
        }
        
        if (!modelFoundInList) {
            println("WARN: Model $modelId not found in loaded models list after ${maxWaitTime.seconds} seconds.")
            return
        }
        
        // 第二步：额外等待一段时间，确保模型完全初始化
        // 对于 TTS 模型，可能需要更长的初始化时间
        val initWaitTime = if (speachesModelType == SpeachesModelType.TTS) {
            Duration.ofSeconds(5) // TTS 模型需要更长的初始化时间
        } else {
            Duration.ofSeconds(2)
        }
        
        println("Model $modelId is in loaded list, waiting ${initWaitTime.seconds} seconds for full initialization...")
        Thread.sleep(initWaitTime.toMillis())
        
        println("Model $modelId is ready.")
    }

    /**
     * 启动容器(若需要)
     *
     * 保证批量测试时共享一个容器，避免多次开/停容器，浪费大量时间。
     * 另外，亦可手动运行该clazz类的main方法来启动容器，跑测试用例时共享它。
     * 并注册 JVM 关闭钩子，当批量测试结束时自动停止容器，
     * 而不是每个测试用例结束时就关闭，前提条件是不要加@Testcontainers注解。
     * 当docker没安装时想忽略测试用例，可以用@EnabledIfDockerInstalled
     *
     * @param registry spring的动态属性注册器，可用来注册或覆盖已注册的属性
     * @param apiKey 可选，API 密钥（如果设置了，所有 API 请求都需要此密钥）
     * @param models 模型映射，键为模型类型名称（STT、TTS、EMBEDDING），值为模型名称
     *               例如：mapOf("STT" to "Systran/faster-whisper-base", "TTS" to "speaches-ai/Kokoro-82M-v1.0-ONNX")
     * @return 运行中的容器对象
     */
    fun startIfNeeded(
        registry: DynamicPropertyRegistry?,
        models: Map<String, String> = emptyMap(),
        apiKey: String? = null
    ): Container {
        synchronized(this) {
            val runningContainer = TestContainerKit.startContainerIfNeeded(LABEL, container)
            
            // 下载所有指定的模型
            models.forEach { (modelType, modelId) ->
                val speachesModelType = SpeachesModelType.valueOf(modelType)
                downloadModelIfAbsent(modelId, speachesModelType, runningContainer)
            }
            
            // 如果指定了 API Key，记录日志
            apiKey?.let {
                println("Note: API Key specified, but container environment is already set. " +
                        "Consider setting API_KEY environment variable before container creation.")
            }
            
            if (registry != null) {
                registerProperties(registry, runningContainer, apiKey)
            }
            return runningContainer
        }
    }

    /**
     * 注册 Spring 动态属性
     */
    private fun registerProperties(
        registry: DynamicPropertyRegistry,
        runningContainer: Container,
        apiKey: String?
    ) {
        val host = runningContainer.ports.first().ip
        val port = runningContainer.ports.first().publicPort
        val baseUrl = "http://$host:$port"
        
        // 注册 speaches API 的基础 URL
        registry.add("spring.ai.speaches.base-url") { baseUrl }
        
        // 如果指定了 API Key，注册 API Key 配置
        apiKey?.let {
            registry.add("spring.ai.speaches.api-key") { it }
        }
    }

    /**
     * 返回运行中的容器对象
     *
     * @return 容器对象，如果没有返回null
     */
    fun getRunningContainer(): Container? = TestContainerKit.getRunningContainer(LABEL)

    /**
     * 主方法，用于手动启动容器
     * 
     * 参数格式：
     * - args[0]: API Key (可选，如果不包含 ":" 且不是模型类型定义，则视为 API Key)
     * - 后续参数：模型定义，支持以下格式：
     *   - "stt:模型名" - STT 模型
     *   - "tts:模型名" - TTS 模型
     *   - "vad:模型名" - VAD 模型
     *   - 或者直接使用模型名称（向后兼容：第一个模型默认为 STT，第二个默认为 TTS）
     */
    @JvmStatic
    fun main(args: Array<String>?) {
        if (args.isNullOrEmpty()) {
            startIfNeeded(null, emptyMap())
            println("Speeches container started on localhost port: $PORT")
            println("API endpoint: http://localhost:$PORT")
            println("Health check: http://localhost:$PORT/health")
            println("API docs: http://localhost:$PORT/docs")
            Thread.sleep(Long.MAX_VALUE)
            return
        }
        
        var apiKey: String? = null
        val models = buildMap {
            var hasModelTypeDef = false // 是否有明确的模型类型定义（包含 ":"）
            var modelIndex = 0 // 用于向后兼容的模型索引
            
            args.forEach { arg ->
                when {
                    // 如果参数包含 ":"，则视为模型类型定义
                    arg.startsWith("stt:") -> {
                        put(SpeachesModelType.STT.name, arg.substringAfter("stt:"))
                        hasModelTypeDef = true
                    }
                    arg.startsWith("tts:") -> {
                        put(SpeachesModelType.TTS.name, arg.substringAfter("tts:"))
                        hasModelTypeDef = true
                    }
                    arg.startsWith("embedding:") -> {
                        put(SpeachesModelType.EMBEDDING.name, arg.substringAfter("embedding:"))
                        hasModelTypeDef = true
                    }
                    // 如果第一个参数不包含 ":"，且还没有明确的模型类型定义，则可能是 API Key
                    modelIndex == 0 && !hasModelTypeDef && !arg.contains(":") && args.size > 1 -> {
                        apiKey = arg
                    }
                    // 向后兼容：第一个模型默认为 STT，第二个默认为 TTS
                    else -> {
                        when (modelIndex) {
                            0 -> put(SpeachesModelType.STT.name, arg)
                            1 -> put(SpeachesModelType.TTS.name, arg)
                            else -> {
                                println("WARN: Unknown model argument: $arg (use format 'stt:model', 'tts:model', or 'vad:model')")
                            }
                        }
                        modelIndex++
                    }
                }
            }
        }
        
        startIfNeeded(null, models, apiKey)
        
        println("Speeches container started on localhost port: $PORT")
        apiKey?.let {
            println("API Key: $it")
        }
        models.forEach { (type, modelId) ->
            println("$type Model: $modelId")
        }
        println("API endpoint: http://localhost:$PORT")
        println("Health check: http://localhost:$PORT/health")
        println("API docs: http://localhost:$PORT/docs")
        Thread.sleep(Long.MAX_VALUE)
    }
}
