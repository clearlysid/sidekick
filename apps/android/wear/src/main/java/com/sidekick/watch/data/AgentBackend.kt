package com.sidekick.watch.data

data class AgentBackend(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
)

object AgentBackends {
    val spacebot =
        AgentBackend(
            id = "spacebot",
            displayName = "Spacebot",
            defaultBaseUrl = "https://debian.finch-kelvin.ts.net",
        )

    // Add new backends here (for example: openclaw, claude-code).
    val supported: List<AgentBackend> = listOf(spacebot)

    fun fromId(id: String?): AgentBackend = supported.firstOrNull { it.id == id } ?: spacebot
}
