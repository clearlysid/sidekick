package com.sidekick.watch.data

data class AgentBackend(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
)

object AgentBackends {
    val spark =
        AgentBackend(
            id = "spark",
            displayName = "Spark",
            defaultBaseUrl = "https://debian.finch-kelvin.ts.net",
        )

    val supported: List<AgentBackend> = listOf(spark)

    fun fromId(id: String?): AgentBackend = supported.firstOrNull { it.id == id } ?: spark
}
