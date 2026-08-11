package com.clinty.client.services

import com.clinty.client.models.ThreadData

object InboxRefreshTracker {
    private var knownThreadIds: Set<String> = emptySet()
    private var hasEstablishedBaseline = false

    fun reset() {
        knownThreadIds = emptySet()
        hasEstablishedBaseline = false
    }

    fun detectNewThreads(threads: List<ThreadData>): List<ThreadData> {
        val currentIds = threads.map { it.id }.toSet()
        if (!hasEstablishedBaseline) {
            knownThreadIds = currentIds
            hasEstablishedBaseline = true
            return emptyList()
        }

        val newThreads = threads.filter { it.id !in knownThreadIds }
        knownThreadIds = currentIds
        return newThreads
    }
}
