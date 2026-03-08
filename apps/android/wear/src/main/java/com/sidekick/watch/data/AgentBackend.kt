package com.sidekick.watch.data

data class AgentBackend(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String? = null,
)

object AgentBackends {
    val spacebot =
        AgentBackend(
            id = "spacebot",
            displayName = "Spacebot",
            defaultBaseUrl = "https://debian.finch-kelvin.ts.net",
        )

    val openclaw =
        AgentBackend(
            id = "openclaw",
            displayName = "OpenClaw",
            defaultBaseUrl = "https://debian.finch-kelvin.ts.net/chat",
            defaultModel = "openclaw:main",
        )

    val supported: List<AgentBackend> = listOf(spacebot, openclaw)

    fun fromId(id: String?): AgentBackend = supported.firstOrNull { it.id == id } ?: openclaw
}
