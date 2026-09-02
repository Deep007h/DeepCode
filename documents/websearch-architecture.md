# Web Search Architecture — How OpenCode Wires Web Search to AI Models

This document explains how the original [opencode (TypeScript)](https://github.com/anomalyco/opencode) implements web search and connects it to LLMs. The Android port follows the same conceptual architecture.

---

## Overview

```
User types "search for X"
  → LLM sees tool "websearch" with JSON Schema in system prompt
  → LLM responds with tool_call({ query: "X" })
  → AI SDK routes to tool.execute()
    → Permission check (ctx.ask)
    → MCP request to Exa/Parallel search provider
    → Text result returned
  → Result fed back to LLM as tool response
  → LLM incorporates results into its answer
```

---

## 1. Tool Definition (`tool/websearch.ts`)

Each tool is defined via `Tool.define(id, effect)`:

```ts
export const WebSearchTool = Tool.define("websearch",
  Effect.gen(function* () {
    const http = yield* HttpClient.HttpClient
    return {
      description: DESCRIPTION,   // from websearch.txt
      parameters: Parameters,     // Effect Schema
      execute: (params, ctx) => {
        // 1. Select provider (Exa or Parallel)
        const provider = selectWebSearchProvider(ctx.sessionID, { exa, parallel })
        // 2. Ask user permission
        yield* ctx.ask({ permission: "websearch", patterns: [params.query] })
        // 3. Call MCP backend
        const result = yield* callProvider(http, provider, params, ctx)
        return { output: result ?? "No results", title: `Web Search: ${params.query}` }
      },
    }
  }),
)
```

**Parameters schema** (Effect Schema → auto-converted to JSON Schema):

```ts
export const Parameters = Schema.Struct({
  query: Schema.String,                              // Websearch query
  numResults: Schema.optional(Schema.Number),        // default: 8
  livecrawl: Schema.optional(Schema.Literals(["fallback", "preferred"])),
  type: Schema.optional(Schema.Literals(["auto", "fast", "deep"])),
  contextMaxCharacters: Schema.optional(Schema.Number), // default: 10000
})
```

---

## 2. Provider Selection

Two MCP-based backends are supported:

| Provider | URL | Auth | Features |
|----------|-----|------|----------|
| **Exa** | `https://mcp.exa.ai/mcp` | Optional `EXA_API_KEY` (free without key) | `query`, `type`, `numResults`, `livecrawl`, `contextMaxCharacters` |
| **Parallel** | `https://search.parallel.ai/mcp` | Optional `PARALLEL_API_KEY` | `objective`, `search_queries`, `session_id`, `model_name` |

Selection logic (`selectWebSearchProvider`):

```ts
function selectWebSearchProvider(sessionID, flags) {
  // 1. Env override
  if (override === "exa" || override === "parallel") return override
  // 2. Feature flags
  if (flags.parallel) return "parallel"
  if (flags.exa) return "exa"
  // 3. Deterministic hash-based split (50/50)
  return checksum(sessionID) % 2 === 0 ? "exa" : "parallel"
}
```

---

## 3. MCP Call (`tool/mcp-websearch.ts`)

Both providers speak the Model Context Protocol (JSON-RPC 2.0):

```ts
export const call = (http, url, tool, args, value, timeout, headers) =>
  Effect.gen(function* () {
    const request = yield* HttpClientRequest.post(url).pipe(
      HttpClientRequest.schemaBodyJson(McpRequest(args))({
        jsonrpc: "2.0", id: 1, method: "tools/call",
        params: { name: tool, arguments: value },
      }),
    )
    const response = yield* http.execute(request).pipe(
      Effect.timeoutOrElse({ duration: timeout, orElse: ... }),
    )
    return yield* parseResponse(yield* response.text)
  })
```

**Exa request example:**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "web_search_exa",
    "arguments": {
      "query": "latest AI news",
      "type": "auto",
      "numResults": 8,
      "livecrawl": "fallback",
      "contextMaxCharacters": 10000
    }
  }
}
```

**Parallel request example:**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "web_search",
    "arguments": {
      "objective": "latest AI news",
      "search_queries": ["latest AI news"],
      "session_id": "abc123",
      "model_name": "claude-sonnet-4-20250514"
    }
  }
}
```

**Response parsing** — handles both single JSON and SSE `data:` lines:

```ts
export const parseResponse = Effect.fn(function* (body: string) {
  // Try direct JSON parse first
  const direct = yield* parsePayload(trimmed)
  if (direct) return direct
  // Fall back to SSE data: lines
  for (const line of body.split("\n")) {
    if (!line.startsWith("data: ")) continue
    const data = yield* parsePayload(line.substring(6))
    if (data) return data
  }
})
```

The `McpResult` schema extracts `.result.content[].text` from the MCP response.

---

## 4. Tool Registry (`tool/registry.ts`)

All tools are collected and conditionally exposed:

```ts
const tool = yield* Effect.all({
  fetch: Tool.init(webfetch),
  search: Tool.init(websearch),
  // ... 15+ other tools
})

// Filter: websearch only enabled for opencode provider or when Exa/Parallel flags set
const filtered = all.filter(tool => {
  if (tool.id === WebSearchTool.id)
    return webSearchEnabled(input.providerID, { exa, parallel })
})
```

The `ToolRegistry.layer` stores initialized tools as `Tool.Def[]` in an Effect `InstanceState`.

---

## 5. JSON Schema Conversion (`tool/json-schema.ts`)

Effect Schema → `JSONSchema7` (Draft 2020-12):

```ts
export function fromTool(tool: Tool.Def): JSONSchema7 {
  return tool.jsonSchema ?? fromSchema(tool.parameters as Schema.Top)
}

export function fromSchema(schema: Schema.Top): JSONSchema7 {
  const document = Schema.toJsonSchemaDocument(schema, { additionalProperties: true })
  return normalize({
    $schema: JsonSchema.META_SCHEMA_URI_DRAFT_2020_12,
    ...document.schema,
    ...(Object.keys(document.definitions).length > 0
      ? { $defs: document.definitions } : {}),
  })
}
```

---

## 6. AI SDK Tool Conversion (`session/tools.ts`)

This is the **central adapter** — converts opencode's `Tool.Def` into the Vercel AI SDK `tool()` format:

```ts
for (const item of yield* registry.tools({ modelID, providerID, agent })) {
  // Convert Effect Schema → JSON Schema → provider-specific sanitization
  const schema = ProviderTransform.schema(input.model, ToolJsonSchema.fromTool(item))

  // Convert to AI SDK 'tool' format
  tools[item.id] = tool({
    description: item.description,
    inputSchema: jsonSchema(schema),   // AI SDK wraps JSON Schema
    execute(args, options) {
      return run.promise(Effect.gen(function* () {
        const ctx = {
          sessionID, messageID, agent, abort,
          extra: options?.extra,
          metadata(input) { ... },
          ask(input) { ... },
        }
        const result = yield* item.execute(args, ctx)
        return result.output
      }))
    },
  })
}
```

**Provider-specific sanitization** (`provider/transform.ts`):

```ts
// OpenAI: strips unsupported JSON Schema keywords
export function sanitizeOpenAISchema(schema: JSONSchema7): JSONSchema7 { ... }
// Google Gemini: converts to its tool format
// Moonshot: different keyword restrictions
```

---

## 7. LLM Call (`session/llm.ts`)

Tools are passed directly to Vercel AI SDK's `streamText`:

```ts
return streamText({
  tools: prepared.tools,              // Record<string, AI SDK Tool>
  model: wrapLanguageModel({ model, middleware }),
  toolChoice: input.toolChoice,
  maxSteps: 5,
  // ...
})
```

The `LLMRequestPrep.prepare()` applies permission filtering before passing tools.

---

## 8. Event Handling

Tool calls are streamed back as events:

| Event | Description |
|-------|-------------|
| `tool-call` | LLM requests a tool invocation |
| `tool-result` | Tool execution completed with output |
| `tool-input-delta` | Streaming tool input for some providers |

These are converted to a common `LLMEvent` union for both the AI SDK and native runtime paths.

---

## 9. Comparison: How the Android Port Implements It

The Android (Kotlin) port follows the same architecture but adapted for mobile:

| Component | TypeScript (original) | Android (Kotlin) |
|-----------|----------------------|------------------|
| Tool definition | `Tool.define()` | `ToolExecutor.kt` + `AgentEngine.kt` |
| Schema | Effect Schema → JSON Schema | `mapOf<String, Any>` hand-written |
| Search backend | MCP (Exa/Parallel) | Direct HTTP to Exa/Google/fallback APIs |
| Tool registration | `ToolRegistry.layer` | `getDeclaredTools()` in ToolExecutor |
| AI SDK bridge | `SessionTools.resolve()` → `tool()` | `AIProvider.streamCompletion(toolDefs)` |
| Permission | `ctx.ask()` | Inline user confirmation |
| Provider routing | `selectWebSearchProvider()` | Hard-coded per provider |

The Android port's `web_search` is implemented in:
- **`ToolExecutor.kt:959`** — Tool definition with description and JSON schema
- **`ToolExecutor.kt:80`** — Tool dispatch (`"web_search"` case)
- **`AgentEngine.kt:95`** — Alternative tool definition for agent loop
- **`AgentEngine.kt:340`** — `executeWebSearch()` implementation

---

## Key Design Patterns

1. **Tools as Effects** — Every tool is an `Effect` (from the Effect TS library), enabling structured concurrency, resource management, and composable error handling.

2. **Schema-Driven** — Parameter schemas are defined once as Effect `Schema` objects and automatically converted to JSON Schema for the LLM, validated at runtime, and used for error messages.

3. **MCP Protocol** — Search providers are abstracted behind the Model Context Protocol, making it trivial to add new providers.

4. **Permission Layer** — Every tool call goes through `ctx.ask()`, which checks user-configured permission rules before execution.

5. **Provider Adaptability** — The `ProviderTransform.schema()` step allows provider-specific JSON Schema sanitization, since OpenAI, Google, Anthropic, etc. all support different subsets of JSON Schema.
