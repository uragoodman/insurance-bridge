# AI 客服数据与业务能力盘点

## 结论口径

本盘点区分三类证据：仓库 Java/Python 代码表示已实现能力；当前本机 Docker MySQL 是开发快照，只用于确认结构和样例字典，不代表生产配置；业务规则、产品资料审核和隐私期限若代码没有明确依据，一律列为待确认。盘点只读取结构、枚举和汇总值，没有读取或记录客户个人数据。

## 已从仓库代码确认

### 产品目录与推荐

- `ProductController` 已提供产品分类、分类筛选条件、产品列表和详情查询；产品列表按 `insuranceState=0` 查询。分类和条件来自数据库字典，不能把本地库当前代码写死为长期产品枚举。
- 现有产品数据包含投保期限、等待期、犹豫期、续保/复效、标签、计划、系数和筛选条件等字段。`InsurancePlanVO.price` 注释为“默认定价”，`priceUnit` 注释为 `y/d,y/m,y/y`；笔记 `SFBX day5.md` 将其描述为试算起步价，最终保费由起步价、产品系数（累加/乘积）和部分产品的投保人数等计算得出。实际代码也由 `doPremium` 路由到不同险种 handler 计算。
- 因此价格字段语义已能确认到“方案基础价/单位 + 依产品规则试算最终保费”，但代码中未发现币种字段，也没有适用于所有产品的标准“年预算”换算/比较契约。年预算不能直接拿 `price` 比；需由 Java 新增类型化、按产品规则校验的报价适配，并明确币种与报价时点。现有 `doPremium` 是投保流程入口，涉及被保险人关系及部分产品所需的健康/年龄/系数信息，不能直接开放给模型调用。
- 产品条件中含健康状况（例如健康、慢性病等）。第一阶段 Agent 不应询问或传递健康信息；需要此类筛选的产品应转到官方详情/人工渠道。
- 通用 `InsuranceServiceImpl.findList` 会把筛选值构造成 `JSON_CONTAINS` 条件。不要将模型原文直接交给此查询；应另建枚举化、校验过的 AI 推荐请求和安全查询层。
- 代码中的 `checkRule` 注释列出 0–7，但处理器映射目前只覆盖 0–5；不可据注释宣称 6、7 已有可用业务实现。

### 产品资料

产品管理端可以维护产品信息，`insuranceState` 表示上架 0 / 下架 1；这是销售状态，不是条款审签状态。产品可关联 `bolee-file` 附件。通用 `FileVO` 有文件名、路径、存储标识、MD5、上传状态等字段，但未发现产品条款审批状态、条款版本、生效/失效日期、审批人/时间及发布撤回流程。个人 SFBX 笔记 `SFBX day1-4.md`、`SFBX day5.md` 只说明产品发布/配置和投保试算背景，没有补足条款审签版本工作流。因此目前能确认的是“产品可上/下架并挂附件”；正式条款审核与版本流程在本项目没有实现依据，不能把附件直接作为客服权威资料。

### 本人保单

- `WarrantyVO` 混有保费、保障起止、投保人身份信息、核保/批保状态、关系人、订单等字段；必须映射到白名单摘要 DTO，不能原样返回。
- `WarrantyConstant` 定义状态 0–10，包括待付款、待生效、保障中、逾期/复效/理赔/满期终止及拒保、退保等状态。以常量为准；部分 VO 注释与常量映射不一致。
- `myWarrantyVOs(user, relation)` 默认按当前登录人的身份证号查询其作为投保人的保单，状态限于待付款、待生效、保障中；不含历史/终止保单。传入关系人 ID 时，还会按该关系人的身份证号找其作为被保险人的保单。但实现通过无归属校验的 `customerRelationService.findById(id)` 解析关系 ID，调用前不能信任用户/模型提供的 ID，存在跨用户访问风险。
- 因此 Agent 第二阶段的安全初始范围定为：只查当前登录客户作为投保人的上述三种状态；不含他人为其投保的保单、家庭关系人保单和历史/终止保单。若以后要包含本人作为被保险人，Java 必须先验证该关系人确属当前用户，再做 owner-scoped 查询。`findById(warrantyId)` 也不按当前用户校验，不能直接作为 Agent 工具。
- 现有保费试算 `doPremium` 会走具体投保处理器，并使用投保关系、系数及部分健康信息等输入；不能未经评估直接当作面向 Agent 的通用报价 API。

### 理赔、人工客服与 Agent 存储

- 仓库只找到理赔表单项配置相关枚举（`ClaimEnum`、`CategoryClaimEnum`、`InsuranceClaimEnum`）；它们不是理赔案件/进度服务。未找到客户理赔案件状态查询或客服工单/转人工接口。因此当前项目没有可确认的理赔或客服权威系统，必须由项目方指定外部系统及负责人后再集成。
- Agno 使用 PostgreSQL 保存会话/运行数据；Agent 配置最多把最近 5 次运行加入上下文，AgentOS 开启 tracing。Compose 有持久卷，但仓库未配置会话/trace 的保留期限、自动清理或删除后的备份处理策略。
- Python 当前 Java 内部 API 客户端只有 GET JSON 能力；产品推荐契约需要 POST 时，应实现限制内部路径白名单的类型化 POST，而非开放任意 URL。

## 本地开发数据库快照

当前本地 `bolee-insurance` 数据库能查到产品、分类、分类筛选、筛选项、计划和保单等表。分类样例包括医疗、重疾、意外、养老金、储蓄、旅游及其子类；筛选项样例覆盖为谁投保、健康状况、保障特色/服务、交通工具和保障时长。快照包含测试分类，分类字典及产品数量会随数据变化；这些是开发环境观察，不应复制成代码常量或生产承诺。

## 确认结果与仍待外部确认

1. 推荐条件只使用 Java 实际发布的分类和非敏感筛选字典；健康状况从第一阶段条件中排除。
2. 价格的“方案基础价 + 产品规则试算”已确认；币种和统一年预算比较尚无字段/规则。先设计 Java 安全报价适配，再决定是否加入预算硬筛选。`regionCode` 未找到权威产品字段，继续禁用。
3. 产品上/下架已确认；条款审核、版本、有效日期与撤回工作流未在本项目实现。需要产品/文件管理方建设并定义后，才能同步给知识库。
4. Agent 保单初始范围定为当前登录客户作为投保人且状态为待付款/待生效/保障中；被保险人身份和历史/终止保单先排除。现有关系人查询要先补 owner 校验。
5. 仓库未发现理赔案件查询、客服工单或转人工集成；目前没有可指定的权威系统/接口。接入前须由项目方提供系统所有者、API 和状态字典。
6. 仓库和 SFBX 笔记未定义聊天正文、Agno session、trace、Java 业务审计的留存期限、删除 SLA、备份清理要求或访问审计规则；目前数据库持久化配置没有自动保留/清理期限。期限仍需由数据/隐私责任方确定。

## 查证位置

- 产品目录/状态/报价字段：`bolee-insurance/insurance-app/.../ProductController.java`、`bolee-insurance/insurance-mgt/.../InsuranceController.java`、`bolee-insurance/insurance-service/.../InsuranceServiceImpl.java`、`.../InsurancePlanVO.java`、`.../InsuranceConstant.java`。
- 产品附件字段：`bolee-framework/framework-commons/.../FileVO.java`；SFBX 笔记 `C:\ck\NoteNO2\developer\BackEnd\SFBX_Serial\SFBX day1-4.md`、`SFBX day5.md`。
- 试算公式：`bolee-insurance/insurance-service/.../InsuranceServiceImpl.java` 及 `handler/impl/*InsureHandler.java`；个人笔记 `SFBX day5.md`。
- 保单范围与关系人：`bolee-insurance/insurance-service/.../WarrantyServiceImpl.java`、`CustomerRelationServiceImpl.java`、`WarrantyInsuredServiceImpl.java`、`bolee-framework/framework-commons/.../WarrantyConstant.java`。
- 理赔/客服和 Agent 留存：`bolee-insurance/insurance-service/.../enums/*ClaimEnum.java`、`bolee-agent-service/app/agent.py`、`bolee-agent-service/db/__init__.py`、根目录 `docker-compose.yml`；SFBX 笔记中未找到 AI 会话/trace 留存规定。

## 实施顺序建议

先从 Java 发布稳定的产品类别/非敏感筛选字典和安全推荐 DTO；随后补齐条款资料审核、版本与索引同步；再实现 owner-scoped 保单摘要及状态映射。报价、健康筛选、理赔和转人工在业务规则与权威数据源明确前保持关闭。上线前另行冻结数据留存/删除策略和安全审计要求。
