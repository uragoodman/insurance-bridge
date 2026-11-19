# 后端服务

后端由 Java 微服务和独立的 Python AI Agent 服务组成。Java 工程使用 Java 11 和 Maven 多模块结构；Python Agent 服务独立于 Maven Reactor。

## Java 微服务模块

| 模块 | 主要职责 |
| --- | --- |
| `bolee-gateway` | 客户端 API 网关与管理端网关 |
| `bolee-insurance` | 保险产品、投保、保单及 AI 业务接口 |
| `bolee-security` | 登录认证、OAuth 授权与安全能力 |
| `bolee-trade` | 交易及支付相关业务 |
| `bolee-file` | 文件服务 |
| `bolee-dict` | 数据字典服务 |
| `bolee-rule` | 规则引擎服务 |
| `bolee-task` | 定时任务与消息处理 |
| `bolee-points`、`bolee-sms` | 积分及短信相关能力 |
| `bolee-framework` | 多个业务模块共享的基础组件与接口 |
| `bolee-apache-httpclient` | HTTP 客户端封装 |

## Python AI Agent

`bolee-agent-service/` 基于 Agno / AgentOS 和 FastAPI，负责 AI 对话编排及工具调用。它通过受保护的服务接口与 Java 后端协作，业务数据和用户身份校验仍由 Java 服务负责。AgentOS 的会话和运行记录存储在 PostgreSQL，并使用 pgvector 扩展提供向量数据支持。

当前 Agent 能力以保险产品目录、需求驱动的产品推荐、本人保单查询和客服对话为主。模型推理依赖外部模型服务；未配置模型凭据时，AI 对话不可用。

## 运行依赖

- **Java 服务：** JDK 11、Maven、Spring Boot 2.7、Spring Cloud 2021、Spring Cloud Alibaba。
- **服务发现与配置：** Nacos。
- **业务数据与缓存：** MySQL 8、Redis。
- **消息与分布式事务：** RabbitMQ；相关事务链路使用 Seata。
- **AI Agent：** Python 3.11+、Agno / AgentOS、FastAPI、PostgreSQL / pgvector，以及模型服务 API。
- **可选基础设施：** XXL-JOB、Elasticsearch、Kibana、Logstash、InfluxDB，是否需要取决于启用的业务模块和观测能力。

各微服务按业务职责独立部署，并通过 Nacos 发现服务。客户端请求通常经 Gateway 进入业务服务；AI 请求由 Java 完成用户鉴权后交给 Python Agent，Agent 再通过授权的 Java API 获取业务数据。
