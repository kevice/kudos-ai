package io.kudos.ai.test.container.containers.speaches

import com.github.dockerjava.api.model.Container
import io.kudos.test.container.kit.TestContainerKit
import io.kudos.test.container.kit.bindingPort
import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
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
        withEnv("log_level", "debug")
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("SpeachesContainer")).withPrefix("speaches"))

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
     * 下载模型（如果不存在）
     *
     * @param modelId 模型 ID（如 "Systran/faster-whisper-base"）
     * @param runningContainer 运行中的容器对象
     */
    private fun downloadModelIfAbsent(modelId: String, runningContainer: Container) {
        val port = runningContainer.ports.first().publicPort
        val baseUrl = "http://127.0.0.1:$port"

        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        // 设置较长的超时时间，因为模型下载可能需要较长时间
        val requestTimeout = Duration.ofMinutes(10)

        try {
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
        } catch (e: Exception) {
            throw RuntimeException("Error downloading/loading model $modelId: ${e.message}", e)
        }
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
     * @param modelIds 模型id列表
     * @return 运行中的容器对象
     */
    fun startIfNeeded(
        registry: DynamicPropertyRegistry?,
        modelIds: List<String> = emptyList(),
        apiKey: String? = null
    ): Container {
        synchronized(this) {
            val runningContainer = TestContainerKit.startContainerIfNeeded(LABEL, container)

            // 下载所有指定的模型
            modelIds.forEach { modelId ->
                downloadModelIfAbsent(modelId, runningContainer)
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
        val port = runningContainer.ports.first().publicPort
        val baseUrl = "http://127.0.0.1:$port"

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
     */
    @JvmStatic
    fun main(args: Array<String>?) {
            startIfNeeded(null, emptyList())
            println("Speeches container started on localhost port: $PORT")
            println("API endpoint: http://localhost:$PORT")
            println("Health check: http://localhost:$PORT/health")
            println("API docs: http://localhost:$PORT/docs")
            Thread.sleep(Long.MAX_VALUE)
    }

}