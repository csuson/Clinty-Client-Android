package com.clinty.client.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class ParsedEmailSection(
    val subject: String? = null,
    val from: String? = null,
    val to: String? = null,
    val id: String? = null,
    val body: String? = null,
)

@Serializable
data class ParsedDraftSection(
    @SerialName("reply_to_message_id") val replyToMessageId: String? = null,
    val from: String? = null,
    val body: String? = null,
)

@Serializable
data class ParsedInterruptDescription(
    val email: ParsedEmailSection? = null,
    val draft: ParsedDraftSection? = null,
)

object InterruptDescriptionParser {
    fun parse(description: String): ParsedInterruptDescription? {
        val normalized = description.replace("\r\n", "\n")
        val parts = normalized.split("\n---\n")
        if (parts.isEmpty()) return null

        val emailSection = parseEmailSection(parts[0])
        val draftSection = if (parts.size > 1) parseDraftSection(parts[1]) else null

        if (emailSection == null && draftSection == null) return null
        return ParsedInterruptDescription(email = emailSection, draft = draftSection)
    }

    fun jsonObject(description: String): JsonValue? {
        val parsed = parse(description) ?: return null
        return runCatching {
            val element = AppJson.encodeToElement(ParsedInterruptDescription.serializer(), parsed)
            JsonValue.fromJsonElement(element)
        }.getOrNull()
    }

    fun prettyJson(description: String): String? {
        val json = jsonObject(description) ?: return null
        return runCatching {
            AppJson.instance.encodeToString(json.toJsonElement())
        }.getOrNull()
    }

    private fun parseEmailSection(text: String): ParsedEmailSection? {
        val (fields, body) = parseFieldsAndBody(text)
        if (fields.isEmpty() && body.isEmpty()) return null
        return ParsedEmailSection(
            subject = fields["subject"],
            from = fields["from"],
            to = fields["to"],
            id = fields["id"],
            body = body.ifEmpty { null },
        )
    }

    private fun parseDraftSection(text: String): ParsedDraftSection? {
        val (fields, body) = parseFieldsAndBody(text)
        if (fields.isEmpty() && body.isEmpty()) return null
        return ParsedDraftSection(
            replyToMessageId = fields["reply_to_message_id"] ?: fields["replyToMessageId"],
            from = fields["from"],
            body = body.ifEmpty { null },
        )
    }

    private fun parseFieldsAndBody(text: String): Pair<Map<String, String>, String> {
        val lines = text.replace("\r\n", "\n").split("\n")
        val fields = mutableMapOf<String, String>()
        var index = 0

        while (index < lines.size && lines[index].trim().isEmpty()) {
            index++
        }

        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty()) {
                index++
                if (fields.isNotEmpty()) break
                continue
            }
            if (trimmed.startsWith("#")) {
                index++
                continue
            }
            val parsed = parseMetadataLine(trimmed)
            if (parsed != null) {
                fields[parsed.first] = parsed.second
                index++
                continue
            }
            if (fields.isNotEmpty()) break
            index++
        }

        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                index++
            } else {
                break
            }
        }

        val body = lines.drop(index).joinToString("\n").trim()
        return fields to body
    }

    private fun parseMetadataLine(line: String): Pair<String, String>? {
        if (!line.startsWith("**") || !line.contains("**:")) return null

        val closeBold = line.indexOf("**:", startIndex = 2)
        if (closeBold < 0) return null

        val label = line.substring(2, closeBold).trim()
        val value = line.substring(closeBold + 3).trim()
        if (label.isEmpty()) return null

        return normalizeFieldKey(label) to value
    }

    private fun normalizeFieldKey(label: String): String {
        return when (label.lowercase()) {
            "subject" -> "subject"
            "from" -> "from"
            "to" -> "to"
            "id" -> "id"
            "reply to message id" -> "reply_to_message_id"
            else -> label.lowercase().replace(' ', '_')
        }
    }
}

fun HumanInterrupt.displayTitle(): String {
    val description = description ?: return actionRequest.action
    val subject = InterruptDescriptionParser.parse(description)?.email?.subject
    if (!subject.isNullOrEmpty()) return subject
    return actionRequest.action
}

fun HumanInterrupt.displayFrom(): String? {
    val description = description ?: return null
    val from = InterruptDescriptionParser.parse(description)?.email?.from
    return from?.takeIf { it.isNotEmpty() }
}
