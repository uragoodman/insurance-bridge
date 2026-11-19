# 客户聊天 API 数据契约

本文记录当前代码实际实现的聊天数据格式。浏览器只调用 Java 客户接口；Python Agent API 保持内网服务间调用。

## 客户端到 Java

H5 通过网关使用 `POST /api/insurance-app/ai-assistant/chat/stream`，后端 Controller 路径为 `POST /ai-assistant/chat/stream`。请求须带 `user-token`，Java 从登录上下文取得用户身份，禁止客户端传 `userId`。`POST /chat` JSON 接口继续保留给非流式客户端。

```json
{
  "sessionId": "ai_0194c1be57d14b2487a07153f2f07b20",
  "clientMessageId": "cb8cb86d-20cc-4687-bc02-a20f8c9d8158",
  "message": "想给父母找医疗保障，预算不要太高",
  "selectedPolicyId": null
}
```

| 字段 | 规则 |
| --- | --- |
| `sessionId` | 客户端会话 ID；建议每个新会话生成，长度 1–64，只允许字母、数字、`_`、`-`。Java 以登录用户绑定所有权。缺省时 Java 可生成 ID，但客户端应显式传入以便重试和读取历史。 |
| `clientMessageId` | 每条用户消息一个 UUID；网络失败或 `PROCESSING` 时必须原样重试。相同 ID 携带不同消息会冲突。 |
| `message` | 1–4000 字符。当前 H5 输入框限制为 2000 字符，是 UI 限制；Java 与 Python 接受上限为 4000。 |
| `selectedPolicyId` | 可选，仅由登录后的本人保单列表选择。Java 在调用 Agent 前重新校验当前用户归属；参与幂等指纹，不能用同一 `clientMessageId` 重试另一张保单。 |

本人保单列表由 `GET /api/insurance-app/ai-assistant/policies?limit=10&cursor=...` 游标分页，详情由 `GET /api/insurance-app/ai-assistant/policies/{policyId}` 获取。两者只返回产品名称、状态、保障起止和自动续保标记；所有状态遵循 `WarrantyConstant`。聊天里的“让 AI 介绍”把所选 ID 作为元数据发送，Agent 通过 Java 归属校验后的保单详情工具介绍，不将 ID 写入聊天正文。

成功 HTTP 响应是项目统一 envelope，成功 `code` 当前为 `200`，数据在 `data` 中：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "sessionId": "ai_0194c1be57d14b2487a07153f2f07b20",
    "requestStatus": "COMPLETED",
    "clientMessageId": "cb8cb86d-20cc-4687-bc02-a20f8c9d8158",
    "turnId": "a194c301-7d9f-4a62-8922-c5b20ee0d0b0",
    "answer": "我先确认一下保障对象的年龄和已有疾病情况。",
    "recommendations": [],
    "recommendationStatus": null,
    "evaluationCompleteness": null,
    "missingCriteria": [],
    "unsupportedCriteria": [],
    "unresolvedCriteria": [],
    "excludedProductCount": 0,
    "catalogVersion": null,
    "ruleVersion": null,
    "productComparison": null,
    "ownPolicySummary": null
  }
}
```

`requestStatus` 为 `COMPLETED`、`PROCESSING` 或 `FAILED`。`PROCESSING` 允许继续用同一个 `clientMessageId` 轮询；`FAILED` 是该请求的终态，再次尝试需生成新 UUID。完成响应的数据定义如下：

流式接口返回 `text/event-stream`，事件为 `delta`、`processing`、`complete` 或 `error`。`delta` 的 `data` 为 `{"text":"..."}`，浏览器逐段追加文字；`complete` 的 `data` 是上方 `data` 中的客户响应对象（没有 envelope）。遇到 `processing` 时客户端继续用相同请求 ID 重试；`complete` 才包含推荐/对比/本人保单卡片数据。断线重试沿用同一 `clientMessageId`，Java 回放已完成答复，不会重复运行 Agent。

首次执行遇到上游/网络异常时，Java 可能直接返回项目错误 envelope；该轮次会被记为终态失败。之后同一 UUID 重试时，才会返回 `data.requestStatus = "FAILED"`，不会再次启动 Agent。

- `answer`: 助手面向客户的文字答复。
- `recommendations`: 最多 3 个候选。每项含 `productId`、`name`、`categoryId`、`categoryCode`、`categoryName`、`matchReasons`、`productVersion`、`verificationStatus`、`facts`、`summary`。普通目录查询结果可没有版本化推荐专属的 `productVersion`、`verificationStatus` 和事实数据。
- `facts`: 每项含 `code`、`definitionVersion`、`name`、`valueType`、`unit`、`valueCode`、`valueCodes`、`valueDisplay`、`valueNumber`、`minValue`、`maxValue`、`valueStatus`。事实可能没有 `valueDisplay`，客户端须能按类型显示结构化值或“未提供/不适用”。
- `recommendationStatus`: `MATCHED`、`NEEDS_CLARIFICATION`、`UNSUPPORTED_CRITERIA`、`CATALOG_INCOMPLETE`、`CATALOG_NOT_READY`、`NO_MATCH`；非推荐回答时为空。
- `evaluationCompleteness`: `COMPLETE` 或 `PARTIAL`。
- `missingCriteria`、`unsupportedCriteria`: 字符串代码数组。
- `unresolvedCriteria`: 对象数组，字段为 `code`、`affectedProductCount`、`reason`。
- `productComparison`: 可空对象，含 `requestId`、`status`、`categoryId`、`catalogVersion`、`ruleVersion`、`products`、`unresolvedCriteria`、`comparedAt`。比较产品含 `productId`、`name`、`productVersion`、`facts`。
- `ownPolicySummary`: 可空对象，`status` 为 `FOUND`、`NO_ACTIVE_POLICIES` 或 `PROFILE_NOT_LINKED`；包含 `asOf`、`policies`、`returnedCount`、`truncated`。保单状态为 `PENDING_PAYMENT`、`PENDING_EFFECTIVE`、`IN_FORCE`。
- `excludedProductCount`、`catalogVersion`、`ruleVersion`: 推荐决策元数据。

客户页显示产品事实、缺失/不支持条件、未解决原因、比较结果和本人保单状态。历史消息的 `structuredData` 会恢复推荐、对比和本人保单卡片。

## Java 到 Python Agent

Java 使用 `client_credentials`，scope 为 `agent.chat`，流式调用 `POST /internal/chat/stream`；同步 JSON 接口为 `POST /internal/chat`。两者请求字段相同：

```json
{
  "user_id": "123456",
  "session_id": "7e694bbe-5bcb-43bd-a355-171321f03889",
  "authorization_context": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "message": "想给父母找医疗保障，预算不要太高"
}
```

`user_id` 与 `session_id` 都由 Java 从登录主体和 owner-scoped 会话映射派生；`authorization_context` 供 Python 调 Java 业务工具时授权。幂等键、Java `turnId` 和 trace ID 保存在 Java，不发送给 Agent。

示例中的授权上下文只展示 43 字符的字段形状，不是有效签名，不能用于调用。

Python 响应使用 snake_case，Java 内部 DTO 显式用 `@JsonProperty` 映射：

流式接口以 Agno 的 `RunContent` 事件输出 `delta`，完成后输出 `complete`，内容使用与 JSON 接口相同的 `ChatResponse` 字段。工具调用、推理和内部事件不会下发到客户浏览器。

```json
{
  "session_id": "7e694bbe-5bcb-43bd-a355-171321f03889",
  "run_id": "agno-run-id",
  "answer": "我先确认一下保障对象的年龄。",
  "recommendations": [],
  "product_comparison": null,
  "own_policy_summary": null,
  "recommendation_status": null,
  "evaluation_completeness": null,
  "missing_criteria": [],
  "unsupported_criteria": [],
  "unresolved_criteria": [],
  "excluded_product_count": 0,
  "catalog_version": null,
  "rule_version": null
}
```

Java 校验 Python payload 后映射为客户 camelCase DTO；客户不会收到 Python 的 `run_id`。响应结构化事实来自 Java 工具结果，不由模型重新编造。

## 客户历史读取

H5 通过 `GET /api/insurance-app/ai-assistant/conversations?cursor=...&limit=20` 获取当前用户的会话列表。Java 按 owner、有效期和删除状态筛选，再向 Python 请求对应 Agno session 的首条用户输入作为标题、最近输入作为预览；Python 返回的 session ID 仅限请求中已通过 Java 筛选的列表，并再次核验 Agno user ID。

H5 请求 `GET /api/insurance-app/ai-assistant/conversations/{sessionId}/messages?cursor=...&limit=25`，使用相同的 `user-token`。`limit` 是 run 数，范围 1–50；消息按创建时间正序，`nextCursor` 是不透明游标，只用于向更早 run 翻页。

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "sessionId": "ai_0194c1be57d14b2487a07153f2f07b20",
    "messages": [
      {
        "messageId": "4c77f3bf-52b6-5bca-94af-4906902d5308",
        "turnId": "a194c301-7d9f-4a62-8922-c5b20ee0d0b0",
        "role": "USER",
        "content": "想给父母找医疗保障",
        "createdAt": "2026-11-16T14:20:00Z"
      },
      {
        "messageId": "9a0f9bc2-1d0d-5a91-b97c-f31925269b01",
        "turnId": "a194c301-7d9f-4a62-8922-c5b20ee0d0b0",
        "role": "ASSISTANT",
        "content": "我先确认一下保障对象的年龄。",
        "createdAt": "2026-11-16T14:20:00Z",
        "structuredData": null
      }
    ],
    "nextCursor": null,
    "hasMore": false
  }
}
```

Java 每次按当前登录用户、客户 `sessionId`、会话状态和过期时间授权；再用 Java 存储的 Agno session ID 调 Python。缺失、过期、已删除和非本人会话统一返回空列表。Python 内部历史 API 要求 `agent.chat` scope，并再次校验 `X-Agno-User-Id`。只返回已完成顶层 run 的 user 输入和最终 assistant 答复。

对于没有对应 Java turn 元数据的旧 run，消息仍可显示，但 `turnId` 可能为 `null`、`structuredData` 为空。新完成的助手消息会返回 `structuredData`，包含与实时答复相同的推荐、对比或本人保单卡片字段。

## 删除

客户请求 `DELETE /api/insurance-app/ai-assistant/conversations/{sessionId}`，Java 立即将会话标记为不可读并排入 Python 远端清理任务。删除是异步的：成功返回表示 Java 接受删除，不保证后台清理已完成。响应 `data` 是 `true`（存在并接受删除）或 `false`（没有可删除的会话）。
