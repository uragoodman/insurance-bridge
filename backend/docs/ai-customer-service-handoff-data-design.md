# AI 客服转人工：数据库设计第一版

状态：Java DTO/VO、Mapper、Service、客户接口、管理接口及留存任务已实现；数据库迁移尚未执行，也没有真实客服渠道数据。迁移文件为
`bolee-insurance/insurance-app/src/main/resources/db/migration/V20261118_04__create_customer_service_handoff_tables.sql`。

## 设计目标

- 将联系渠道和客服请求作为 Java 管理的业务数据；Python Agent 不直接写工单或指定处理人。
- 客户身份由 Java 登录上下文确定；请求 DTO 不接受 `ownerUserId`。
- 客户和管理端数据按现有 `company_no` 多租户上下文隔离，同时对客户接口额外校验 `owner_user_id`。
- 首版不复制完整对话正文。用户说明和客服备注在数据库以密文保存；密钥与 AI 幂等响应密钥分开管理。
- 支持请求幂等、客服状态审计、会话删除后的关联解除，以及独立于 AI 会话的留存。
- 不插入示例电话、链接或虚构队列。渠道表启用前必须由管理员维护并核实真实数据。

## 表职责

### `tab_customer_service_channel`

维护 `HOTLINE`、`WEB_LINK`、`WECHAT` 或 `INTERNAL_QUEUE` 等渠道。每个渠道带 `company_no`，首版按租户维护，不假设存在全平台共享客服入口。`target_value` 保存该渠道公开目的地或内部队列编码；渠道默认 `DISABLED`，经核验后再启用。生效时间、时区和服务时间描述供客户端展示及 Java 侧可用性判断。

### `tab_customer_service_request`

客服请求主表。`company_no` 来自认证上下文，`owner_user_id` 来自 Java 登录主体；客户查询同时按租户和所有者过滤。`client_request_id` 与 `request_hash` 支持重复提交回放和不同内容冲突检测。`source_conversation_id`、`source_turn_id` 可空，仅作关联，不通过外键绑定 AI 生命周期。`request_type` 和 `reason_code` 保存结构化问题分类；不存完整聊天记录。`preferred_contact_method` 表示客户选择的联系方式；如客户提供账号以外的回呼信息，写入 `contact_value_ciphertext`，并记录 `contact_key_version`。用户说明和客服备注也只存密文；这些密钥必须独立于 `AI_IDEMPOTENCY_ENCRYPTION_KEY`。

`context_share_consent_at` 只记录用户明确同意分享会话上下文的时间；在实现经过权限校验的上下文导出前，该字段不得被视为已经授权客服读取整段历史。

请求状态建议由 Service 限制为：`SUBMITTED` → `ACCEPTED` → `IN_PROGRESS` → `WAITING_CUSTOMER` / `RESOLVED`，并允许按规则取消。控制器和数据库均不能接受模型自行写入状态或处理人。

`expires_at` 表示客服请求自己的留存截止时间；启用请求写入前，需要定义 `customer-service.request-retention-days` 等正数配置。不能直接复用 AI 会话期限。

### `tab_customer_service_request_event`

追加记录提交、受理、分配、状态迁移、取消和解决等事件。用户、客服操作员和系统使用不同 `actor_type`。备注密文采用独立客服密钥；事件表不记录模型思维链或完整对话正文。

## AI 会话删除与留存

AI 会话由现有删除清理链路删除，而客服请求属于独立业务记录。清理 AI 会话时应在 Java 中解除仍保留请求的 `source_conversation_id` / `source_turn_id` 关联；不能因此删除未结客服请求。客服请求到期后按其自身策略删除主表、事件和密文。

数据库层不加会话外键，原因是 AI 会话有异步远端清理和独立删除生命周期；关联验证、删除解除与 owner 检查由事务性 Service 实现。渠道停用应使用状态变更，不能硬删仍被请求引用的渠道。客服表带 `company_no` 以配合现有 MyBatis 租户拦截；管理端查询还需验证数据权限拦截不会误隐藏队列请求，并继续显式校验租户。

## 迁移和运行限制

- 本迁移仅创建空表，不填造客服渠道或请求数据。
- 按仓库约定，应先检查 `flyway:info`、核对数据库和备份，再显式执行迁移；应用启动不自动跑 DDL。
- 当前 Flyway 资源路径中，`V20261118_04` 前存在三条实验性 Ngram 迁移，其中 `V20261118_03` 会重建实验索引。正式执行本迁移前必须先确定这些前序迁移的发布/隔离方式；不能直接对开发或生产库执行整条待迁移链。
- 该 DDL 尚未实库验证，字段、索引及加密/留存配置仍需结合 Java Service 实施和 MySQL 5.7 验证。

## 当前 Java API 切片

客户侧 `customer-service` 提供渠道列表、创建请求、本人列表/详情和取消未受理请求。请求号幂等键由客户端生成 UUID；客户 ID 和租户号只从认证上下文读取。客服管理侧 `customer-service-admin` 提供租户队列、状态推进和渠道配置维护。状态推进限制为 `SUBMITTED → ACCEPTED → IN_PROGRESS → RESOLVED`，事件与主表修改同事务写入。管理接口依赖现有保险管理应用的认证/路由授权配置，发布前需要把新路径配置到对应管理权限。

当前版本只接收结构化类型、原因码、联系偏好和可选渠道，不接收手机号、对话正文或客服备注，避免在客服独立加密密钥尚未实施时写入个人明文。客服渠道 `target_value` 仅管理接口可读写；客户渠道响应不包含目标地址。请求默认留存 90 天，可由 `customer-service.request-retention-days` 覆盖；保险 App 每日批量清理到期请求及事件，可通过 `customer-service.retention-cleanup-cron` 调整时间。

这实现的是 Java 内部客服请求队列，不是实时电话转接、在线客服连接或外部工单系统集成。Python Agent 尚未调用 Java 转人工接口；后续只应由 Agent 提示用户并在用户明确确认后由客户端提交工单。当前实现未暴露会话内容共享功能。
