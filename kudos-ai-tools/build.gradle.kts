dependencies {
    api(libs.kudos.context)
    api(project(":kudos-ai-ability:kudos-ai-ability-model:kudos-ai-ability-model-text"))
    api(libs.spring.ai.model)
    api(libs.spring.ai.client.chat)
    api(libs.spring.ai.ollama)
    api(project(":kudos-ai-test:kudos-ai-test-container"))
}
