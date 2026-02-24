# Personal Assistant System — Data and Execution Flow

This document describes the complete data and execution flow of the Clario personal assistant, broken into **inbound WhatsApp**, **MCP/tool routing**, **asynchronous reminder**, and a **visual sequence** for a sample user message.

---

## 1. Inbound WhatsApp Flow

**Trace:** Webhook receipt → user resolution → assistant context → LLM → tool execution → response text.

### 1.1 Entry: `WhatsAppWebhookController`

- **Endpoint:** `POST /webhook/whatsapp`
- **Input:** `WhatsAppWebhookPayload` (JSON from Meta), with structure:
  - `object` (must be `"whatsapp_business_account"`)
  - `entry[]` → `changes[]` → `value` → `messages[]` → first message
- **Validation:** If `payload` is null or `object != "whatsapp_business_account"`, returns 400.
- **Success path:** Calls `webhookService.processIncomingMessage(payload)` and wraps the returned string in `WhatsAppWebhookResponse` inside `ApiResponse.ok(...)` with 200.

### 1.2 `WhatsAppWebhookService.processIncomingMessage`

1. **Extract identity and text**
   - `extractPhoneNumber(payload)`: `payload.getEntry().get(0).getChanges().get(0).getValue().getMessages().get(0).getFrom()` (e.g. `919876543210`).
   - `extractMessageText(payload)`: same path, then `message.getText().getBody()`.
   - If phone or message is missing/blank → `IllegalArgumentException` → 400.

2. **Resolve user**
   - `userRepository.findByPhoneNumber(phoneNumber)` then `tryFindByNormalizedPhone(phoneNumber)` (strip non-digits, try with/without `91` prefix, or with `+`).
   - If no user → `IllegalArgumentException` → 400.

3. **Call LLM for tool decision**
   - `llmService.requestToolCall(user.getId(), messageText)`.
   - Returns `ToolCallResponse(tool, parameters)` (e.g. `create_task` + map of `title`, `dueTime`, etc.).

4. **Run tool and format response**
   - `toolRouter.invoke(toolCall.tool(), ensureUserId(toolCall.parameters(), user.getId()))` so `userId` is always present.
   - `formatResponseMessage(toolCall.tool(), toolResult)` builds the string shown to the user (e.g. "Done. Task: ..." or "Pending tasks: ...").

So the **assistant_profile is not loaded in the webhook service**. It is used inside the LLM path.

### 1.3 Loading assistant_profile and passing context to LLM

- **Where:** `LLMService.requestToolCall(userId, userMessage)`.
- **Context loading:**
  - `getSystemContextForUser(userId)` → `assistantProfileService.getSystemContextPrompt(userId)`.
  - That calls `assistantProfileRepository.findByUserId(userId)` (SQL: `SELECT ... FROM assistant_profile WHERE user_id = :user_id`).
  - Returns `profile.getPersonalityPrompt()` if present and non-blank; otherwise `AssistantProfileService.DEFAULT_PERSONALITY`.
- **LLM request:**
  - HTTP `POST` to `{baseUrl}/v1/chat/completions` with:
    - `model`: `"gpt-4"`
    - `messages`: `[ { "role": "system", "content": systemContext }, { "role": "user", "content": userMessage } ]`
    - `response_format`: `{ "type": "json_object" }`
  - Response body is parsed as JSON; the service expects top-level keys `"tool"` and `"parameters"` (e.g. `{ "tool": "create_task", "parameters": { "title": "...", "dueTime": "..." } }`). If the external API returns the usual wrapper with `choices[0].message.content`, that content must be the stringified version of this JSON, and the implementation would need to parse the content—as written, it parses the raw response body.
  - If base URL is blank or the request fails, `placeholderResponse(userId)` returns `ToolCallResponse("list_tasks", Map.of("userId", userId))`.

**Summary:** Inbound path is **Controller → WebhookService** (extract phone/message, resolve user) → **LLMService** (load assistant_profile via AssistantProfileService/AssistantProfileRepository, call LLM with system + user message, return tool + parameters) → **ToolRouter** (invoke tool) → **formatResponseMessage** → response back to controller.

---

## 2. MCP & Tool Routing Flow

**How the LLM’s tool choice becomes a Java call and then MySQL via NamedParameterJdbcTemplate.**

### 2.1 LLM decides the tool

- `LLMService.requestToolCall(userId, userMessage)` returns a `ToolCallResponse`:
  - `tool`: one of `"create_task"`, `"list_tasks"`, `"add_person"`, `"retrieve_people"`.
  - `parameters`: map of arguments (e.g. `title`, `description`, `dueTime`, `reminderTime` for `create_task`). The webhook layer injects `userId` into this map before invocation.

### 2.2 ToolRouter: JSON response → Java tool

- **Component:** `ToolRouter` (Spring component), receives `List<Tool>` by constructor (all `Tool` beans: `CreateTaskTool`, `ListTasksTool`, `AddPersonTool`, `RetrievePeopleTool`).
- **Allowlist:** `ALLOWED_TOOLS = Set.of("create_task", "list_tasks", "add_person", "retrieve_people")`. Only these names are accepted.
- **invoke(toolName, arguments):**
  - `findTool(toolName)` checks allowlist and finds the bean where `tool.name().equalsIgnoreCase(toolName)`.
  - If not found → `IllegalArgumentException`.
  - Otherwise `tool.execute(arguments)` and return the result map.

So the “JSON response” from the LLM is already parsed into `ToolCallResponse(tool, parameters)`; the router maps the string `tool` to one concrete `Tool` implementation and calls `execute(parameters)`.

### 2.3 Example: CreateTaskTool → TaskService → MySQL

- **CreateTaskTool.execute(arguments):**
  - Reads `userId`, `title`, `description`, `dueTime`, `reminderTime` from the map (with `userId` required; `title` required).
  - Builds `TaskRequestDTO`, then calls `taskService.createTask(userId, request)`.
- **TaskService.createTask:**
  - Sanitizes title/description, creates `Task` entity, sets `status = "PENDING"`, calls `taskRepository.save(task)`.
- **TaskRepository (NamedParameterJdbcTemplate, no raw SQL execution from LLM):**
  - **Insert:** `insert(task)` uses a fixed SQL string:  
    `INSERT INTO tasks (user_id, title, description, due_time, reminder_time, status) VALUES (?, ?, ?, ?, ?, ?)`  
    with positional parameters set from the `Task` object and `KeyHolder` for generated ID. No user-supplied SQL.
  - **Update:** `update(task)` uses a fixed `UPDATE tasks SET ... WHERE id = :id` with `MapSqlParameterSource`.
  - All other methods (`findById`, `findByUserIdAndStatus`, `findUpcomingReminders`, etc.) use fixed SQL and `MapSqlParameterSource`/`RowMapper`.

So: **LLM → ToolCallResponse → ToolRouter → CreateTaskTool → TaskService → TaskRepository → NamedParameterJdbcTemplate** with only predefined SQL. No dynamic or raw SQL from the LLM.

---

## 3. Asynchronous Reminder Flow

**ReminderScheduler cron, reminder_log for deduplication, and WhatsAppService.**

### 3.1 Schedule and entry point

- **Class:** `ReminderScheduler` (Spring `@Component`).
- **Method:** `sendDueReminders()`, annotated `@Scheduled(fixedDelay = 60000)` (runs 60 seconds after the previous run completes).

### 3.2 Query for pending tasks

- `Instant now = Instant.now()`.
- `taskRepository.findUpcomingReminders(now)`:
  - SQL:  
    `SELECT ... FROM tasks WHERE reminder_time IS NOT NULL AND reminder_time <= :before AND status = :status ORDER BY reminder_time`  
    with `before = now`, `status = 'PENDING'`.
  - Returns tasks whose reminder time has passed and are still PENDING.

### 3.3 reminder_log: prevent duplicate alerts

- For each task in `dueTasks`:
  - `reminderLogRepository.existsByTaskId(task.getId())`:
    - SQL: `SELECT ... FROM reminder_log WHERE task_id = :task_id LIMIT 1`.
  - If a row exists → task is skipped (“already has reminder_log”).
  - So **reminder_log** is the idempotency guard: once a reminder is sent for a task, that task is never sent again by the scheduler.

### 3.4 User lookup and send

- `userRepository.findById(task.getUserId())`. If no user → log and skip.
- `formatReminderMessage(task)`: e.g. `"Reminder: <title> (due <dueTime>)\n<description>"`.
- `whatsAppService.sendReminder(user.getPhoneNumber(), message)`:
  - Delegates to `WhatsAppMessageSender.send(phoneNumber, message)` (e.g. `ConsoleWhatsAppSender` or `CloudApiWhatsAppSender`).

### 3.5 Logging the send (idempotency)

- After sending, a `ReminderLog` is created: `taskId`, `sentAt = now`, `status = "SENT"`.
- `reminderLogRepository.insert(reminderLog)` inserts into `reminder_log` (table: `task_id`, `sent_at`, `status`).

So the flow is: **ReminderScheduler** (every 60s) → **TaskRepository.findUpcomingReminders** → for each task **ReminderLogRepository.existsByTaskId** → if not sent: **UserRepository.findById** → **WhatsAppService.sendReminder** → **ReminderLogRepository.insert**.

---

## 4. Visual Architecture: “Remind me to call Santhosh tomorrow”

End-to-end journey from webhook to DB and back out via the scheduler.

```mermaid
sequenceDiagram
    participant User
    participant Meta as Meta WhatsApp
    participant Webhook as WhatsAppWebhookController
    participant WebhookSvc as WhatsAppWebhookService
    participant UserRepo as UserRepository
    participant LLM as LLMService
    participant ProfileSvc as AssistantProfileService
    participant ProfileRepo as AssistantProfileRepository
    participant ExtLLM as External LLM API
    participant Router as ToolRouter
    participant CreateTask as CreateTaskTool
    participant TaskSvc as TaskService
    participant TaskRepo as TaskRepository
    participant DB as MySQL
    participant Scheduler as ReminderScheduler
    participant ReminderLogRepo as ReminderLogRepository
    participant WhatsAppSvc as WhatsAppService
    participant Sender as WhatsAppMessageSender

    User->>Meta: "Remind me to call Santhosh tomorrow"
    Meta->>Webhook: POST /webhook/whatsapp (payload)
    Webhook->>WebhookSvc: processIncomingMessage(payload)

    WebhookSvc->>WebhookSvc: extractPhoneNumber(), extractMessageText()
    WebhookSvc->>UserRepo: findByPhoneNumber(phone) / tryFindByNormalizedPhone
    UserRepo->>DB: SELECT user by phone_number
    DB-->>UserRepo: User
    UserRepo-->>WebhookSvc: User (userId)

    WebhookSvc->>LLM: requestToolCall(userId, messageText)
    LLM->>ProfileSvc: getSystemContextPrompt(userId)
    ProfileSvc->>ProfileRepo: findByUserId(userId)
    ProfileRepo->>DB: SELECT assistant_profile WHERE user_id = :user_id
    DB-->>ProfileRepo: AssistantProfile
    ProfileRepo-->>ProfileSvc: personality_prompt
    ProfileSvc-->>LLM: systemContext

    LLM->>ExtLLM: POST /v1/chat/completions (system + user message, response_format json_object)
    ExtLLM-->>LLM: { "tool": "create_task", "parameters": { "title": "Call Santhosh", "dueTime": "…", "reminderTime": "…" } }
    LLM-->>WebhookSvc: ToolCallResponse(create_task, parameters)

    WebhookSvc->>WebhookSvc: ensureUserId(parameters, userId)
    WebhookSvc->>Router: invoke("create_task", params)
    Router->>CreateTask: execute(params)
    CreateTask->>TaskSvc: createTask(userId, TaskRequestDTO)
    TaskSvc->>TaskRepo: save(task)
    TaskRepo->>DB: INSERT INTO tasks (user_id, title, due_time, reminder_time, status, ...)
    DB-->>TaskRepo: generated id
    TaskRepo-->>TaskSvc: Task
    TaskSvc-->>CreateTask: TaskResponseDTO
    CreateTask-->>Router: Map(id, title, dueTime, ...)
    Router-->>WebhookSvc: toolResult

    WebhookSvc->>WebhookSvc: formatResponseMessage(tool, toolResult)
    WebhookSvc-->>Webhook: "Done. Task: Call Santhosh\n"
    Webhook-->>Meta: 200 ApiResponse(responseMessage)
    Meta-->>User: (reply could be sent via WhatsApp API later)

    Note over Scheduler,DB: Later: ReminderScheduler runs every 60s

    loop Every 60s (fixedDelay)
        Scheduler->>TaskRepo: findUpcomingReminders(now)
        TaskRepo->>DB: SELECT tasks WHERE reminder_time <= now AND status = 'PENDING'
        DB-->>TaskRepo: List<Task>
        TaskRepo-->>Scheduler: dueTasks

        loop For each due task
            Scheduler->>ReminderLogRepo: existsByTaskId(taskId)
            ReminderLogRepo->>DB: SELECT reminder_log WHERE task_id = :task_id LIMIT 1
            DB-->>ReminderLogRepo: exists?
            ReminderLogRepo-->>Scheduler: true/false

            alt No reminder_log yet
                Scheduler->>UserRepo: findById(task.userId)
                UserRepo->>DB: SELECT user BY id
                DB-->>UserRepo: User
                UserRepo-->>Scheduler: User
                Scheduler->>Scheduler: formatReminderMessage(task)
                Scheduler->>WhatsAppSvc: sendReminder(phoneNumber, message)
                WhatsAppSvc->>Sender: send(phoneNumber, message)
                Sender-->>WhatsAppSvc: (e.g. console log or Cloud API)
                Scheduler->>ReminderLogRepo: insert(ReminderLog)
                ReminderLogRepo->>DB: INSERT INTO reminder_log (task_id, sent_at, status)
            end
        end
    end

    Sender-->>User: (WhatsApp delivery if Cloud API configured)
```

---

## Summary Table

| Lifecycle              | Entry point                    | Key components                                                                 | Data stores        |
|------------------------|--------------------------------|---------------------------------------------------------------------------------|--------------------|
| Inbound WhatsApp       | `WhatsAppWebhookController`    | WebhookService → UserRepository → LLMService → AssistantProfileService/Repo    | users, assistant_profile |
| MCP & Tool routing     | `ToolRouter.invoke`            | Tool impl (e.g. CreateTaskTool) → TaskService → TaskRepository                 | tasks (MySQL)      |
| Asynchronous reminder  | `ReminderScheduler` (60s)      | TaskRepository, ReminderLogRepository, UserRepository, WhatsAppService        | tasks, reminder_log, users |

All database access uses **NamedParameterJdbcTemplate** (or its underlying `JdbcTemplate`) with fixed SQL and parameterized queries; no raw SQL is derived from the LLM or user input.
