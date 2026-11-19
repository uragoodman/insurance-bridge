# Java 产品推荐与比较实施计划

## 目标与边界

在现有 Java 产品目录查询基础上，增加受控的个性化候选筛选和产品事实比较。Python Agent 从对话中识别客户明确表达的偏好、询问缺失条件；Java 校验条件、执行规则、返回候选和比较事实。推荐只帮助客户浏览产品，不代表核保、投保资格、价格承诺或购买建议。首期不采集健康情况，不估算保费，也不让 Agent 调用投保流程。

## 成熟方案给出的设计依据

- Salesforce Product Discovery 将 Guided Selection（收集需求）与产品搜索、资格规则和基于约束规则的推荐分开；推荐结果由业务规则产生，而不是模型直接挑选产品。[Product Discovery 概览](https://developer.salesforce.com/docs/revenue/revenue-management/guide/product-discovery-overview.html)、[Guided Selection API](https://developer.salesforce.com/docs/revenue/revenue-management/guide/actions-obj-search-products-with-guided-selection.html)、[Product Recommendations API](https://developer.salesforce.com/docs/revenue/revenue-management/guide/connect-resources-product-recommendations.html)
- DMN 决策表把输入、规则行、输出和命中策略显式化；命中策略决定多个规则匹配时如何归并，避免“配置行的先后顺序就是隐含业务规则”。可借鉴其显式规则结构和冲突校验，不要求本项目现在部署 Camunda。[Camunda DMN 决策表](https://docs.camunda.io/docs/components/modeler/dmn/)、[DMN 命中策略](https://docs.camunda.io/docs/8.8/components/modeler/dmn/decision-table-hit-policy/)
- Drools 提供可执行的完整规则引擎，规则包含条件与动作，并管理规则与运行事实。它适合需要更复杂规则执行和专门规则治理的场景；当前保险推荐若只有受控筛选与排序，直接引入会增加规则语言、安全、发布及运维成本。[Drools 规则引擎](https://docs.drools.org/8.36.0.Final/drools-docs/docs-website/drools/rule-engine/index.html)
- Guidewire 将保险产品定义建模为产品版本/edition、保障条款和规则，并通过有效起止日期处理上线后的产品变更。这说明展示用标签不能替代带版本的合同事实。[Guidewire Product Editions](https://docs.guidewire.com/cloud/apd/qusar/create/topics/c_editions.html)
- SAP Commerce 区分参与业务计算的固定属性与适合灵活维护/展示的分类属性，并支持产品目录版本和批准状态。项目可借鉴“受控属性字典 + 产品值 + 发布状态”，不必复制其整套 PIM。[SAP 产品属性建模](https://help.sap.com/docs/SAP_COMMERCE_CLOUD_PUBLIC_CLOUD/9b5366ff6eb34df5be29881ff55f97d2/8bae803786691014811eb4b6d97f06ea.html)

## 当前项目基线

| 现有能力/数据 | 当前含义 | 对 AI 推荐的限制 |
| --- | --- | --- |
| `AiProductCatalogController`、`AiProductCatalogService` | Java 动态返回末级产品分类，按分类取最多 3 个在售产品；Python 已注册对应工具。 | 是目录查询，不理解客户偏好，也不做排序理由或比较。 |
| `tab_category_condition` / `CategoryCondition` | 按分类定义筛选项 key、名称和排序。 | 无数据类型、允许值定义、敏感等级或 AI 可用标记。 |
| `tab_insurance_condition` / `InsuranceCondition` | 产品级条件 key/name 与 JSON 条件值。 | 各产品值可能不统一；不能将模型文字直接用于 `JSON_CONTAINS`。 |
| `tab_insurance_rule` / `InsuranceRule` | 以 `insuranceId` 关联 `rulesId` 和 `rulesType`，并提供常规 CRUD。 | 当前模型没有推荐条件、匹配表达式、优先级或版本语义；不能直接视为个性化推荐规则引擎。 |
| `tab_insurance` / `Insurance` | 产品分类、在售状态、标签/补充说明、关系、投保时间、等待期等。 | 多个事实是 JSON 或自由文本；上架不等于条款审核；缺少 AI 发布版本。 |
| `tab_insurance_plan` / `InsurancePlan` | 方案基础价格和 `priceUnit`，部分产品需要系数试算。 | 无币种和统一预算换算语义；不是报价 API。 |
| `recommend-insure-list` | 面向首页的展示推荐，使用 `showIndex`、推荐分类等展示字段。 | 不是客户偏好匹配；不得当作 AI 推荐引擎。 |

本地开发数据库只是快照（当时约 30 个产品、26 个分类、101 条条件、33 个方案），包含测试配置且不代表生产目录。实施前应在管理端核对产品负责人、实际筛选项和数据质量，不从快照复制硬编码枚举。

### 当前 Java 代码切片

已建立独立 Flyway 读模型、Repository/Mapper 查询边界，以及受 `product.catalog.read` scope 保护的条件目录与推荐接口。条件目录明确返回 `READY`、`NOT_PUBLISHED` 或 `NOT_READY`；推荐请求在无当前目录时返回 `CATALOG_NOT_READY`。快照引用的定义版本按快照读取；若定义或选项被修改为不再适用，服务安全拒绝执行并要求重新发布快照。比较结果对每个可比较属性显式返回 `KNOWN`、`NOT_PROVIDED` 或审核允许的 `NOT_APPLICABLE`。

迁移同时预建了用于记录定义、产品资料、规则集和快照状态变化的追加式审核日志表；它记录操作者、动作、状态迁移、理由、内容 hash 和关联 ID，不记录客户对话。它与推荐决策 trace 分开，决策 trace 首期仍使用结构化日志。

这仍是只读执行切片。属性草稿维护、产品事实录入、静态校验、审核、发布/回滚接口和已确认的业务数据尚未实现；因此当前没有可用的真实推荐目录。下一阶段应在同一 Repository 下增加独立管理服务，只有通过审核和发布事务的不可变快照才能成为线上读数据。管理 API 需使用独立管理员授权，状态变更和 current pointer 切换与审核日志在同一事务提交。

Python 侧已有 Java 推荐 `POST` 精确路径 allowlist、推荐契约模型、`ProductRecommendationService` 和 Agno 工具注册。Service 先读当前条件目录，再校验规则模式、操作符、枚举值和单位，最后调用 Java 并校验响应关联 ID、目录版本、候选和比较事实。工具结果收集及 Java 客户响应映射现已透传结构化候选、比较事实和版本元数据；模型生成的文字不会成为结构化事实。由于本地产品记录没有备案版本映射，且当前尚无审核发布的试点产品快照，真实产品比较仍不可用。具体候选核对见[产品比较试点资料核对](product-comparison-pilot-audit.md)。

## 通用规则框架：建议方案

建议从“每个险种手写一套 if/else”升级为**通用规则模型 + 有限操作符的 Java 执行器**。通用的是规则表达结构、校验/发布/审计流程和执行接口；不是允许任意表达式、脚本或模型生成业务规则。所有规则仍按类别和版本配置，所有产品候选与事实仍由 Java 负责。

### 规则与事实的概念模型

| 逻辑对象 | 核心字段/语义 | 对应职责 |
| --- | --- | --- |
| `RecommendationAttributeDefinition` 条件定义 | `attributeCode`、定义版本、类别、值类型、规范单位、允许用途/操作、敏感等级、AI 可用状态、缺值策略。 | 定义“能问什么、能比较什么”，现有 `tab_category_condition` 仅作为盘点/迁移来源。改变类型、单位或枚举语义时发布新定义版本；不兼容变化使用新代码。 |
| `ProductProfileVersion` 产品资料版本 | 产品 ID、版本号、冻结的展示名称/排序值、状态、生效区间、来源类型/引用/hash、审核人/时间。发布后不可原位修改。 | 冻结一次可用于推荐的产品事实集合。来源引用须定位条款/资料的具体版本、章节或附件，不只保存 hash。 |
| `ProductAttributeValue` 产品事实 | 产品版本、属性代码及定义版本、类型化值/规范单位、`KNOWN` / `NOT_PROVIDED` / `NOT_APPLICABLE` 状态、来源定位/适用期。 | 表示产品被审核确认的客观事实；不是运营标签或自由文本。未知、未提供和不适用不能互相当作 false。 |
| `RecommendationRuleSet` 规则集 | 类别、版本、状态、生效区间、排序策略、发布人/时间。 | 定义某类别使用哪组澄清、过滤和偏好规则。 |
| `RecommendationRule` 规则项 | 属性定义引用、规则用途（`HARD_FILTER` / `PREFERENCE_SCORE` / `COMPARE_ONLY`）、是否必需澄清、偏好分值/优先级、理由代码、缺值策略。 | 说明该条件在本类别中的业务用途；不复制客户本轮的比较值，也不存可执行代码。允许操作和类型来自属性定义，比较值来自经过校验的请求条件。 |
| `RecommendationCatalogSnapshot` 目录快照 | 类别范围内不可变快照 ID、纳入的产品资料版本及属性定义版本、规则集版本、创建时间；每个类别单一当前快照指针。 | 让一次请求绑定一组确定的产品事实、条件定义和规则，而非各自读取“当前版本”。 |
| `RecommendationDecisionTrace` 决策摘要 | request/trace ID、条件代码、命中的规则/候选 ID、状态、快照/产品/规则/执行器版本、耗时。 | 排查与审计；首期可通过现有结构化日志/审计设施承载，不强制单独建持久化表；不保存原始对话和不必要的个人信息。 |

首版物理模型已通过 Flyway 建立在独立 AI 推荐表中；不要把这些实体直接塞进现有 `tab_insurance_rule`。该表目前只有产品与规则 ID/类型的关联，不具备上述规则集和规则项语义。产品事实版本、属性定义版本、目录快照、规则配置版本和执行器代码版本分开管理：响应返回 `catalogVersion`、`ruleVersion` 和 `engineVersion`；候选事实包含对应的属性定义版本，trace 保留实际使用的各版本，以便复现一次决策。当前只完成了只读条件目录与推荐执行链路；草稿维护、试算、审核发布/撤回管理接口和已审核的产品事实数据尚未建立，因此空库/未发布目录不会返回实际候选。

属性按类型使用固定值列或等价的类型化存储（例如 `value_code`、`value_decimal`、`value_min/value_max`）；数值精度和规范单位由属性定义控制。多选值使用关联行，不存逗号拼接字符串或自由 JSON。V1 的 `IN` 明确定义为任一请求代码命中即可；需要“全部命中”时应新增明确操作符，不能改变 `IN` 的含义。`NUMBER` 的 `RANGE` 表示单个产品数值落入客户给出的闭区间；`NUMBER_RANGE` 的 `RANGE` 表示产品覆盖整个客户要求的区间（产品下界不高于请求下界，产品上界不低于请求上界）。类型化值、产品版本和属性定义版本之间建立唯一键/外键；业务范围、范围边界、单位转换及版本时间冲突由 Java 服务校验和发布事务保证。MySQL 5.7 会解析但忽略 `CHECK` 约束，不能依赖它实施业务不变量。[MySQL 5.7 ALTER TABLE 约束说明](https://dev.mysql.com/doc/refman/5.7/en/alter-table.html)

请求中的客户意图和规则的执行用途是两个概念，协议不复用同一枚举：请求使用 `customerIntent=MUST_HAVE|PREFERENCE`；属性目录用 `allowedUses` 声明可筛选/可排序/仅比较；规则项用 `ruleMode=HARD_FILTER|PREFERENCE_SCORE|COMPARE_ONLY`。规则项的 `missingPolicy` 首期只允许 `INCOMPLETE`（缺事实导致部分评估）或 `ALLOW_NOT_APPLICABLE`（仅对审核确认“不适用”的事实不计分/不命中硬条件）。客户说“必须”不能把只允许排序的属性升级为硬筛选；若 Java 不能按其 MUST_HAVE 意图执行，返回 `UNSUPPORTED_CRITERIA`，Agent 可询问客户是否接受按偏好处理，只有客户明确同意后才重新请求。偏好不得转成硬排除。

例如 `insured_group_type`（枚举）、`covered_activity`（多选代码）、`waiting_period_days`（天数）可共用一套校验和执行器。增加已注册属性值主要通过数据维护完成；新增类型/操作/含义仍需 Java 代码和契约变更。Agent 只能读取只读条件目录（例如 `GET /internal/agent-tools/v1/products/recommendation-criteria?categoryId=...`），提交代码和值；Java 再做服务端校验，不信任模型输出或 Python 缓存。条件目录响应包含 `status=READY|NOT_PUBLISHED|NOT_READY`、`categoryId`、`catalogVersion`、`ruleVersion` 和 `criteria[]`，让 Agent 能区分“暂时没有已发布目录”和“目录不支持某条件”。每项同时返回定义允许用途和当前 `activeRuleMode`；只有当前模式支持的 `allowedOperators` 可提交。

### 管理与发布流程

规则和产品事实按同一治理原则维护，但分开发布：**草稿 → 静态校验 → 样例目录试算 → 业务审核 → 不可变版本发布 → 可回滚**。审核校验属性定义版本是否兼容、操作/值类型是否兼容、必需条件是否矛盾、排序参数是否有效、规则是否覆盖缺值情况；试算报告应展示候选变化和未命中原因。发布权限与普通产品编辑权限分开，记录操作者和审核动作。V1 先实现版本、校验、审核/发布/撤回和回滚所需的最小管理能力；批量历史回测 UI 和通用规则编辑器可后置。若规则由工程师维护，可先用受控管理 API/运维流程维护草稿，不能绕过服务校验直接改发布数据。

发布产品或规则时，在事务中创建完整不可变的类别快照（含该类别产品资料版本、属性定义版本和规则集版本），全部校验通过后再原子切换该类别唯一的 `current_snapshot_id`。推荐请求开始时只解析一次该 ID，后续都按快照 ID 查询，避免产品、定义和规则分别读取“当前版本”而拼出不一致组合。普通撤回通过发布新快照影响新请求；已开始请求按其固定快照完成。紧急合规撤回需额外检查即时 denylist，响应前确认候选未被紧急撤回。若事务使用 InnoDB `REPEATABLE READ`，同一事务的一致性读可使用同一数据库快照，但不能替代应用层定义的目录快照版本；并发发布仍需事务/锁和唯一约束。[MySQL 5.7 InnoDB 一致性读](https://dev.mysql.com/doc/refman/5.7/en/innodb-consistent-read.html)

### Java 执行管线

推荐服务采用固定、可测试的管线：解析一次当前目录快照 ID → 加载该快照中的条件定义/规则集/产品资料版本 → 校验输入与版本 → 数据完整性检查 → 处理澄清项 → 执行硬筛选 → 按已发布偏好规则稳定排序 → 生成命中理由/限制 → 返回快照/版本和摘要 trace。规则数据只能影响白名单属性、操作与数值；不允许规则动作调用外部服务、修改业务记录或触发投保。

V1 可实现自己的小型 Java 执行器，采用“多条适用规则可同时命中、硬筛选全部满足、偏好分值累加、稳定字段打破并列”的明确策略，并在规则集定义中固定该策略。必需条件冲突、互斥规则、未知规则版本或规则数据自相矛盾时，发布校验失败或请求返回明确业务状态，不能依赖数据库行顺序挑选结果。若未来需要业务人员直接使用标准决策表编辑规则，可评估把规则表达迁移为 DMN，并验证当前规则是否可无损映射；当规则包含复杂事件/动作、跨流程联动或大规模依赖关系时，再评估 Drools 等完整规则引擎。

规则集至少需按产品类别明确：可执行条件及操作符、必需澄清项、硬筛选与偏好项的边界、偏好排序参数、数据完整性要求、命中策略、配置版本和生效时间。V1 的评分参数存于经审核发布的规则集；代码执行器版本单独管理。模型不得生成或调整权重。不要把 `tab_insurance_rule` 的关联记录解释为这些规则配置。

### 方案取舍

| 方案 | 优点 | 代价/风险 | 本项目判断 |
| --- | --- | --- | --- |
| 按类别分别手写 Java 判断 | 首期实现快、执行行为直观。 | 类别和条件增多后重复逻辑多；变更需发版，规则与代码耦合。 | 仅适合作为已有能力盘点或极小试点，不作为目标架构。 |
| 通用规则数据模型 + 有限 Java 执行器 | 数据结构复用、结果确定、Java 易做强校验；规则和产品事实可独立版本化、审核与回滚；不引入表达式执行面。 | 必须自行建设规则静态校验、管理发布、冲突检测与试算工具；规则复杂度仍需限制。 | **推荐 V1**：满足当前产品筛选/偏好排序，同时保留未来演进空间。 |
| DMN 决策表/外部规则平台 | 标准决策模型更利于业务与技术共同审阅；成熟引擎可管理复杂命中策略。 | 引入运行时/建模/部署链路；需处理服务集成、规则和产品版本一致性、权限及故障治理。 | 当业务人员需要自主配置复杂决策表时再做试点，先验证模型映射及运行依赖。 |
| Drools 等完整规则引擎 | 支持更丰富的规则组合和执行能力，适合复杂规则编排。 | 学习与运维成本较高；扩展的执行能力也扩大审查和安全面。 | 当前推荐排序需求不足以支撑引入。 |

## Java 服务与 API 方案

### 调用链

```text
客户自然语言 → Java 客服接口 → Python 澄清并提取已发布条件代码
              → Java 内部推荐接口 → Java 校验、筛选、排序
              → Java 返回事实 → Python 组织解释 → Java 返回客户
```

### 分层

| Java 层 | 建议职责 |
| --- | --- |
| 管理 API/后台 | 在现有保险产品管理模块中维护 AI 条件定义、产品属性和审核发布；不能开放给 Agent。记录操作者与审核动作。 |
| 内部 Agent API | `POST /internal/agent-tools/v1/products/recommendations` 执行规则推荐；`POST /internal/agent-tools/v1/products/compare` 对同一类别下 2–10 个已发布产品逐项比较。两者沿用服务令牌、短时 grant 和 `product.catalog.read`。只接受类型化条件/产品 ID，不接收用户 ID、自由表达式或 URL。 |
| 应用服务 | 参数、版本、授权和结果上限校验；将业务结果统一为 `MATCHED`、`NEEDS_CLARIFICATION`、`UNSUPPORTED_CRITERIA`、`CATALOG_INCOMPLETE`、`CATALOG_NOT_READY`、`NO_MATCH`。 |
| 领域/业务服务 | 通过固定 Java 操作符执行器解释已发布的硬筛选和偏好排序规则，产生理由及可比较字段；不调用投保、核保或佣金规则。 |
| Mapper/Repository | 查询有效上架产品及当前有效发布版本；只投影 AI 候选 DTO 所需列，不暴露 `InsuranceVO` 或原始产品 JSON。 |

推荐请求采用类型化条件列表；比较操作和类型必须由 Java 条件目录授权，不接收任意字段 map：

```json
{
  "requestId": "uuid",
  "turnId": "uuid",
  "criteria": {
    "categoryId": "java-returned-category-id",
    "items": [
      {"code": "insured_group_type", "operator": "EQ", "valueCode": "GROUP", "customerIntent": "MUST_HAVE"},
      {"code": "covered_activity", "operator": "IN", "valueCodes": ["SPORTS"], "customerIntent": "MUST_HAVE"},
      {"code": "waiting_period_days", "operator": "LTE", "numberValue": 0, "unit": "DAY", "customerIntent": "PREFERENCE"}
    ]
  },
  "limit": 3
}
```

上述代码只是说明协议形状，不代表当前数据库已经存在这些条件。`customerIntent` 只表达客户本轮意图，不决定 Java 的规则用途。属性目录和规则集共同决定能否执行：`MUST_HAVE` 只能由获准的硬筛选规则实现；`PREFERENCE` 只能参与偏好排序，不能硬排除。模型不能自行把偏好升格为硬筛选或承保资格条件。V1 仅接受审核过的非敏感条件；年龄、地区、预算以及任何健康条件默认不作为可执行条件，除非以后有经批准的业务契约。若客户提到未发布或暂不支持的条件，Python 要澄清或如实说明无法判断，不能静默省略或转换成其他代码。同一代码重复提交、非法操作符/值类型和超出请求上限应在 Java DTO 校验中拒绝；`requestId` 用于关联单次只读评估，重试必须使用相同请求体及 ID。

逐项比较接口接收 `requestId`、Java 返回的 `categoryId` 和 2–10 个去重后的 `productIds`。Java 只比较当前该类别已发布快照中的产品，并验证产品主表分类与 AI 档案分类一致；不在当前快照中的任一产品会导致整个请求失败，不会静默少比较。响应按请求顺序返回产品及每个 `allowCompare` 条件的类型化事实，缺失事实使用 `NOT_PROVIDED` 并汇总到 `unresolvedCriteria`；`status` 为 `COMPLETE` 或 `PARTIAL`，同时带上目录与规则版本。比较接口不负责推荐、报价或条款解释。

结果状态区分：`NEEDS_CLARIFICATION` 表示条件缺失或表达含糊；`UNSUPPORTED_CRITERIA` 表示属性/操作/客户意图组合没有已审核的执行规则；`CATALOG_NOT_READY` 表示类别当前没有可执行的已发布目录；`CATALOG_INCOMPLETE` 表示规则支持但部分产品事实不足以完整评估；`NO_MATCH` 只表示所有有效产品的相关事实均已评估且没有候选；`MATCHED` 返回符合硬条件的候选。六种是正常业务响应（HTTP 200）。这避免把“资料没维护”误报为“没有合适产品”。

响应包含 `status`、`evaluationCompleteness`（`COMPLETE` / `PARTIAL`）、`missingCriteria[]`、`unsupportedCriteria[]`、`unresolvedCriteria[]`、`excludedProductCount`、`candidates[]`、`evaluatedAt`、`catalogVersion`、`ruleVersion` 和 `engineVersion`。`unresolvedCriteria` 每项包含条件 `code`、受影响产品数和安全的缺数原因；`excludedProductCount` 只统计因硬筛选事实缺失而不能确认是否符合的去重产品数，不返回内部产品主键。候选附 `productVersion`、按条件代码列出的 `attributeDefinitionVersions[]` 和 `verificationStatus=VERIFIED_MATCH`。`CATALOG_INCOMPLETE` 可以返回已验证子集，但必须标为 `PARTIAL`，列出未能评估的条件和排除数量；Agent/前端需明确展示“部分目录结果”，不得称为全目录最佳推荐。比较事实为每个 `allowCompare=true` 的条件返回一项；资料缺失时 `valueStatus=NOT_PROVIDED` 且值字段为空，审核确认不适用时返回 `NOT_APPLICABLE`。`NOT_PROVIDED` 计入 `unresolvedCriteria` 并使结果为 `CATALOG_INCOMPLETE`，但比较资料缺失不排除已验证候选。推荐候选最多 3 个；缺条件返回 `NEEDS_CLARIFICATION`，执行规则不支持返回 `UNSUPPORTED_CRITERIA`，没有有效发布目录返回 `CATALOG_NOT_READY`，事实不完整返回 `CATALOG_INCOMPLETE`，仅完整评估且无候选返回 `NO_MATCH`，均为 HTTP 200 业务状态。没有已核实 H5 路由就不返回 `detailUrl`。

V1 的候选粒度是 `tab_insurance` 中的保险产品；`tab_insurance_plan` 的档位、价格和方案差异不作为独立候选，也不混入产品匹配分。若以后要推荐具体方案，需先确定方案级属性、报价口径和可比较语义，再扩展候选类型。响应应同时返回 `evaluatedAt`、目录快照版本及候选的 `productVersion`、按条件代码列出的 `attributeDefinitionVersions[]`，使解释对应到实际评估的资料快照。

`NO_MATCH` 只有在该快照中所有上架且已发布的产品，对本次规则所需属性均有可判定数据时才成立。完整性分母是快照中符合销售/发布状态的候选产品；产品资料缺失或 `NOT_PROVIDED` 使评估不完整，`NOT_APPLICABLE` 只有在属性定义允许且经审核时才可视为已判定。空值不得当作“不符合”。硬筛选事实缺失的产品不能确认是否符合，应排除并计入 `excludedProductCount`；偏好事实缺失不加分、不扣分，候选仍可返回，但整体状态为 `CATALOG_INCOMPLETE`，避免把不完整排序说成最佳结果。返回其他已验证候选时，Agent 也不得将其描述为完整目录最佳结果。条件本身不受支持仍用 `UNSUPPORTED_CRITERIA`，与产品事实不完整分开。

## 确定性匹配与比较规则

1. **准入过滤**：只选 `data_state=0`、`insurance_state=0`，且有当前有效 AI 发布版本、对应事实通过审核的产品。
2. **硬筛选**：只执行产品负责人确认可公开用于筛选的条件。条件非法、停用或类别不适用时拒绝请求；缺少必填条件则请求澄清。适用性/核保规则不混入推荐过滤。
3. **偏好排序**：客户明确表达的偏好只对通过硬筛选的候选排序。V1 采用规则集声明的固定评分表：每项已发布偏好命中对应固定分值，缺数据不加分也不扣分；候选仍可返回，但结果标为 `CATALOG_INCOMPLETE`，不可声称排序已覆盖完整信息。返回命中理由而不必向客户暴露内部数值。分值须经产品/业务负责人审阅，不可使用运营费率/佣金，也不可由 Agent 调整。
4. **冲突与并列**：相互冲突的必需条件、含糊的阈值或语义不一致的条件返回 `NEEDS_CLARIFICATION`；不得替客户放宽或重新解释。评分相同时按已批准的业务排序字段，再按产品主键排序，避免结果随机变化。
5. **逐项比较**：只比较候选共同拥有、同一数据类型和同一发布语义的事实；缺失值显示“资料未提供”，不可由 Agent 补齐。匹配理由由 Java 根据命中规则产生。类别与 `attributeCode` 的映射在 Java 侧解析并校验；请求只传 Java 返回的类别 ID 和代码，不把原始表名/字段名、SQL 操作或 JSON 查询表达式交给模型。

不自动放宽条件，不把推荐写成适合购买/能够承保。保费只有未来接通专用报价服务并确认币种、周期和时点后才进入比较；条款责任和免责只有审核版本及来源链路建成后才展示。

## 数据库迁移与索引

所有新增表/字段使用 `insurance-app/src/main/resources/db/migration` 的 Flyway 迁移，并兼容当前 MySQL 5.7。实施前在目标库检查现有索引，迁移时至少覆盖：

- 当前目录指针：每个类别仅一个 `current_snapshot_id`；每个快照项唯一绑定产品资料版本、属性定义版本和规则集版本。
- 快照内产品事实：按 `snapshot_id + product_version_id + attribute_code + definition_version` 查询；多值使用子表并建唯一/查找索引。
- 属性定义：按 `category_id + attribute_code + definition_version` 唯一；每个规则版本固定引用定义版本。
- 规则集及规则项：按不可变 `rule_set_id + version` 读取，规则项按 `rule_set_id + priority` 查询。
- 通过事务/行锁保护快照构建和 current pointer 切换；版本生效区间重叠、值范围和跨行完整性由 Java 发布校验。MySQL 5.7 的唯一键/外键可维护结构性约束；`CHECK` 不执行，不能依赖它做范围校验。

索引必须依据最终 SQL 和 `EXPLAIN` 计划验证，避免盲目加索引。现有产品库只有少量样例数据，不能代表生产规模。

## 实施阶段与验收门槛

1. **数据盘点**：在产品管理端核对分类条件的真实含义、值格式和敏感性；挑选少量有可靠资料的产品做试点。无法确认来源的字段禁用。
2. **最小模型与规则执行器**：Flyway 建属性定义、产品资料版本/类型化属性、规则集/规则项及不可变目录快照；决策 trace 首期优先复用结构化日志/现有审计，不预设单独 trace 表。Java 实现类型校验、操作符白名单、静态规则校验和纯函数执行器。
3. **最小审核发布治理**：实现草稿、审核、发布、撤回、回滚和样例试算；迁移既有值仅作 DRAFT。发布版本不可原地改写，发布前验证定义版本兼容、冲突、缺值策略和结果样例。完整历史回测工具与通用业务编辑器后置。
4. **Java 推荐服务**：实现类型化 DTO、规则/目录快照解析、硬筛选/软排序/比较和 `/recommendations` 内部接口；复用现有强制 scope 拦截器。
5. **Java 单边验收后**：用有效/未发布/已撤回/含敏感条件/重叠规则/缺失事实/并发发布的样例验证准入；确认越权请求和自由表达式不能穿透 Mapper，排序可复现、快照一致、部分结果有明确标识、规则版本可回滚。
6. **Python 接入**：新增受限 POST Java Client、工具参数 schema 和结果 collector；Agent 只能把 Java 返回的候选透传给前端，不能生成候选或理由事实。

Java 验收还需覆盖：客户 MUST_HAVE 与属性允许用途不兼容时不降级执行；必需条件冲突时要求澄清；偏好得分和并列顺序可重复；候选数据缺失时返回部分状态而不误报 `NO_MATCH`；并发发布期间单次请求仍固定快照；普通撤回从新请求生效，紧急撤回应阻止响应；定义、目录、产品、规则和执行器版本能定位本次判断。审计只记录 request/trace ID、条件代码、候选 ID、结果状态、版本和耗时；不记录客户原始对话或不必要的个人信息。

### 例子：团体运动保险，希望尽快生效

1. Agent 先从 Java 条件目录确认当前产品类别支持哪些条件。`tab_insurance.multiple` 的代码注释为团险 `0` / 个险 `1`，但实施时仍需校验数据字典和管理端实际值，再映射成稳定代码 `GROUP`。
2. “运动保障”需要一个审核后的 `covered_activity`/保障类别事实及其正式来源；不能仅凭产品名、分类是意外险或自由标签判断覆盖运动风险。若涉及免责/高风险运动限制，必须等对应条款版本可追溯后再作为回答事实。
3. Agent 澄清“很快生效”是指**没有等待期**，还是要在**某个日期前开始保障**。`Insurance.waits` 表示等待期候选字段，不能代表从付款到出单的耗时；`Warranty.safeguardStartTime` 是已生成保单的保障开始时间，也不能直接充当售前产品承诺。若要判断指定日期前生效，Java 必须有经过业务确认的生效规则（例如起保触发点、是否需要核保/支付及有效时间）；缺少该来源时返回 `UNSUPPORTED_CRITERIA`。
4. Java 仅当团体、运动保障和客户确认的生效条件都在已发布资料中时才筛选/比较。若只确认团体但无法确认运动覆盖或起保时间，必须明确缺口，不给“有合适产品/能很快生效”的承诺。若三项均可验证，则候选说明逐条指出命中的来源事实和限制。

该例成为端到端验收场景：团体类型代码映射正确；Agent 对“快”追问而不猜阈值；Python 只发已发布代码；Java 按已审核数据返回候选、澄清、无匹配或不支持状态；候选、理由和限制不由模型编造。

生产开放的门槛是：产品负责人确认字段和审核责任；最小产品集已发布；迁移与索引验证通过；Java/Python 跨服务工具调用和结构化候选返回通过验收。若没有满足这些门槛的产品资料，功能应返回澄清/不支持，而不是回退到未经审核的标签。
