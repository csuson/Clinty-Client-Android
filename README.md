# Clinty Client (Android)

Android Jetpack Compose client for **human-in-the-loop LangGraph agents**, ported from the [iOS Clinty client](https://github.com/clinty/clinty-client) and modeled on [Agent Inbox](https://github.com/langchain-ai/agent-inbox).

Review interrupted threads, inspect `HumanInterrupt` payloads, and send `HumanResponse` actions (accept, edit, respond, ignore) back to your remote LangGraph deployment.

## Features

- Inbox list with pull-to-refresh (interrupted threads only)
- Thread detail with accept / respond / edit actions
- Ignore and resolve (end thread) actions
- Settings: LangSmith API key + multiple inbox configurations
- Local dev support for `http://127.0.0.1:8123` (cleartext HTTP enabled)

## Setup

1. Open the project in **Android Studio** (Ladybug or newer recommended).
2. Sync Gradle and run on an emulator or device (API 26+).
3. **Settings** → save your **LangSmith API key** (required for LangGraph Cloud).
4. **Add Inbox** with:
   - **Assistant / Graph ID** — graph name from `langgraph.json` or assistant UUID
   - **Deployment URL** — e.g. `https://your-app.langgraph.app`
   - **Name** (optional)

### Local dev defaults

Edit `LocalSecretsDefaults.kt` or create a gitignored `LocalSecrets.kt`:

```kotlin
object LocalSecrets {
    val langsmithAPIKey: String? = null
    val graphId: String? = "gmail_assistant"
    val deploymentUrl: String? = "http://127.0.0.1:8123"
}
```

For emulators, use `http://10.0.2.2:8123` to reach the host machine's localhost.

## Project layout

| Path | Role |
|------|------|
| `models/` | `HumanInterrupt`, `ThreadData`, `JsonValue`, API DTOs |
| `services/InterruptParser.kt` | Interrupt extraction (ports agent-inbox `utils.ts`) |
| `services/HumanResponseBuilder.kt` | Build accept/edit/response payloads |
| `services/LangGraphClient.kt` | LangGraph HTTP API (OkHttp) |
| `services/InboxStore.kt` | Persist inboxes + API key (SharedPreferences) |
| `ui/inbox/` | Thread list screen |
| `ui/thread/` | Interrupt detail + actions |
| `ui/settings/` | API key + inbox CRUD |

## Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug
```

## References

- [Agent Inbox](https://github.com/langchain-ai/agent-inbox)
- [LangGraph streaming API](https://docs.langchain.com/langgraph-platform/streaming)
