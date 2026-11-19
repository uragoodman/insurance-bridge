# Java AI 实施切片：产品目录查询

## 已冻结的首期边界

- 本次 Java 只提供只读、按产品末级分类查询目录候选；不将它描述为个性化推荐结论。
- 分类从 Java 当前有效产品分类动态读取；模型传入分类主键必须经 Java 校验。
- 仅返回有效且上架的产品 ID、名称及分类信息；最多 3 项，按产品排序字段后主键升序。
- 不接受健康、地区、预算、SQL、任意排序或模型生成的 URL；不返回价格、条款解释、佣金、保单或客户数据。
- 现有产品详情地址是 POST API，不是已确认的 H5 页面链接。因此本切片不生成 `detailUrl`；前端路由由客户端工程确认后另行补契约。
- 每次客户聊天只签发 `product.catalog.read` scope。内部接口仍同时要求服务令牌和短时授权上下文；scope 由 Java 强制校验。
- 会话目录/轮次表、正式推荐规则、条款知识库、报价、保单查询、理赔/人工转接均不属于本切片。

## 接口

`GET /internal/agent-tools/v1/products/categories` 返回动态末级产品分类。

`GET /internal/agent-tools/v1/products/search?categoryId={id}&limit={1..3}` 返回该分类的上架产品候选。缺少分类返回 `NEEDS_CLARIFICATION` 和 `PRODUCT_CATEGORY`；有效分类无在售产品返回 `NO_MATCH`；匹配时返回 `MATCHED` 和候选。无效、非产品类或非末级分类返回参数错误。

两者均要求 OAuth2 Bearer token 与 `X-Agent-Authorization-Context`，并校验 `product.catalog.read`。它们是只读幂等查询，无会话/turn 写操作；后续推荐接口再定义完整的请求幂等、结构化卡片和来源契约。

Java 端点和安全校验已实现。Python Agent 现已注册分类列表与分类目录查询工具，工具使用 Java 返回的动态分类 ID，并在服务层校验 Java 响应和候选分类。聊天响应把经验证的候选产品作为结构化 `recommendations` 返回给 Java；自然语言回答仍由 Agent 生成。部署到目标环境前还应确认产品分类数据不含要隐藏的测试分类，当前分类表没有独立的 AI 发布/可见标志。

## 持久化与后续决策

本切片复用 `CategoryMapper` 与 `InsuranceMapper`，不新增表。MyBatis Plus 查询只选择白名单字段，并在数据库侧限制状态、分类和条数。隐私留存期限仍未确定；正式启用聊天历史或 trace 前必须另行批准并实现清理策略。
