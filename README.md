# OA 智能工单助手

把 OA 系统里重复的审批判断交给 AI：自动查员工信息、匹配审批规则、参考历史同类工单，给出带证据链的处理建议。

**所有改变数据的操作都必须人工确认后才执行。**

---

## 解决什么问题

OA 审批里有一类活儿特别耗时间：一张报销单进来，审批人要先确认申请人部门、查差旅标准、看金额有没有超线、回忆上次类似单子怎么批的，最后才敢点通过。

这些动作有固定套路，但每次都要重复。这个项目要做的就是把这套查证过程自动化，把结论和依据一起摆到审批人面前——由人做最后决定，而不是让 AI 直接批。

---

## 技术栈

| 项 | 选择 | 版本 |
|---|---|---|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.5.16 |
| 数据库 | SQLite + JdbcTemplate | sqlite-jdbc 3.54.1.0 |
| 大模型 | 阿里云百炼 DeepSeek | `deepseek-v4.1-flash` |
| 向量模型 | 阿里云百炼 | `text-embedding-v3`（1024 维） |
| 构建 | Maven | 3.6.3+ |

没有引入 LangChain4j 之类的 Agent 框架。Agent 循环、工具注册、参数校验全部手写，代码量可控（约 600 行），每个环节都能讲清楚，也避免框架升级带来的不确定性。

---

## 几个技术选择的原因

**为什么用 Spring Boot 3.5 而不是最新的 4.x**

4.x 是 2026 年的新大版本，生态适配和中文资料都还在追赶期。这个项目只需要 Web + JDBC，3.5 完全够用，而且 3.5 的基线就是 Java 17，不用升级 JDK。选版本看的是够用且稳，不是最新。

**为什么用 SQLite 而不是 MySQL**

项目定位是验证 Agent 逻辑，不是高并发生产系统。SQLite 单文件、零配置，任何人 clone 下来 `mvn spring-boot:run` 就有完整数据，不用先装数据库、建库、改连接串。数据访问层用的是 JdbcTemplate，要换 MySQL 只需改驱动依赖和连接串，业务代码一行不动。

**为什么用 JdbcTemplate 而不是 MyBatis / JPA**

只有 4 张表、不到 10 条固定查询，上 ORM 的收益很小。手写 SQL 每条都在明面上，走没走索引一眼能看出来。另外 SQLite 在 MyBatis-Plus 上要额外配数据库方言，属于给自己找麻烦。

**数据库初始化怎么保证重复执行不出错**

建表用 `CREATE TABLE IF NOT EXISTS`，插数据用 `INSERT OR IGNORE` 依赖唯一键去重。规则表原本没有唯一键，专门加了 `UNIQUE(ticket_type, description)` 复合约束。项目反复重启行数不变。

---

## 设计要点

**工具返回统一结构，必须能表达失败**

```java
public record ToolResult<T>(boolean success, T data, String message) { ... }
```

工具结果最终要序列化成 JSON 交给大模型。如果查不到直接返回 `null`，模型看到的是一片空白，它分不清"数据不存在"还是"系统出错"，很容易开始编造。

加上 `success` 和 `message` 之后，模型能明确知道失败原因，甚至能根据错误信息里的可选值自我纠正——比如规则查询失败时会提示"可选值：报销 / 请假 / 权限申请 / 设备报修"，模型看到后能换成正确参数重试。

**工具方法签名尽量简单**

四个只读工具都只有一个字符串参数（`getTicket`、`getEmployee`、`getRules`、`findSimilarTickets`）。因为这些方法要给模型调用，参数类型一多它就容易传错——一个字符串进、一个结构化 JSON 出，是最不容易出错的形态。

唯一的例外是写操作 `proposeDecision(ticketNo, decision, reason)`：变更工单必须同时知道改哪张、改成什么、为什么改，三个信息缺一不可，只能接受参数多一点。

**代码做确定的事，模型做需要理解的事**

以审批规则查询为例：报销类下面有差旅费、办公用品、招待费三条规则，具体适用哪一条需要读懂工单标题和内容的语义。这种判断写死在代码里会又丑又覆盖不全（标题写法千变万化），所以工具只负责把候选规则全部返回，由模型结合工单内容自己挑。

**历史工单检索用语义而不是关键词**

审批人想问的是"以前类似单子怎么批的"，但他不会用和工单标题一样的措辞——说"住宿报销"和工单里"差旅费-福州出差住宿"字面并不重合，`LIKE` 匹配不到。

所以用 embedding + 余弦相似度：20 条工单在启动时批量向量化，存进 `ticket_embedding` 表（`float[]` 按二进制 BLOB 存，1024 维占 4096 字节，比存 JSON 数组省五倍空间）。检索时全量读进内存暴力比对，20 条数据是毫秒级。

这个量级上向量数据库属于过度设计，真到十万条再换 HNSW 或 pgvector。

相似度阈值 0.6 是实测出来的：相关查询的 top3 落在 0.65~0.84，无关查询（"今天食堂吃什么"）最高只到 0.51。取两者中间的值既能挡住误召，又不会漏掉真命中。

**请求链路用 trace_id 串起来**

每个请求进第一个过滤器时分配一个 16 位 trace_id 放进 MDC，日志格式里带出来。同一次请求的模型调用、工具执行、异常，全部共享同一个号。

错误响应也会带上它——用户报障时只需要给一个字符串，不用描述"我什么时候点的""参数是什么"。

**重试只针对可重试的失败**

网络异常和 5xx / 429 会自动重试，指数退避 800ms、1600ms 再加随机抖动，最多三次。4xx 直接抛出——参数错了重试一百次结果也一样，只会浪费时间。

加抖动的原因：多个请求同时失败时，避免它们在同一毫秒集体重试，把刚缓过来的服务端再打崩一次。

---

## 目录结构

```
oa-ticket-agent/
├── src/main/java/com/example/oaticket/
│   ├── controller/      对外 HTTP 接口
│   ├── service/         业务编排（Agent 循环、确认事务）
│   ├── tool/            给模型调用的工具，只读查询与写操作提案
│   ├── rag/             向量化与相似工单检索
│   ├── trace/           请求链路标识
│   ├── repository/      数据访问，JdbcTemplate 手写 SQL
│   ├── client/          大模型调用与协议对象
│   ├── config/          配置绑定
│   ├── model/           数据模型
│   ├── utils/           通用工具
│   └── OaTicketAgentApplication.java
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql       建表脚本
│   └── data.sql         演示数据
└── src/test/            单元测试
```

---

## 快速开始

需要 JDK 17 和 Maven 3.6.3+。

```bash
git clone https://github.com/blinueqj731-ai/oa-ticket-agent.git
cd oa-ticket-agent
mvn spring-boot:run
```

数据查询接口不需要额外配置，启动即可用：

```bash
# 健康检查
curl http://localhost:8080/api/health

# 全部工单（20 条）
curl http://localhost:8080/api/tickets

# 按编号查单条
curl http://localhost:8080/api/tickets/T-20261001-001
```

调用大模型前需要先设置两个环境变量：

```bash
export DASHSCOPE_API_KEY="百炼 API Key"
export BAILIAN_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```

```bash
# 单轮对话
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"用一句话说明什么是OA审批流程"}'

# 工单处理，模型会自行调用工具查数据
curl -X POST http://localhost:8080/api/agent/ticket \
  -H "Content-Type: application/json" \
  -d '{"question":"T-20261001-001 现在是什么状态？"}'
```

模型没有修改工单状态的权限。它想通过或驳回某张单时，只能生成一条待确认提案：

```bash
# 查看待确认提案
curl http://localhost:8080/api/approvals/pending

# 人工确认，工单状态此时才真正变更
curl -X POST http://localhost:8080/api/approvals/P-1A2B3C4D/confirm \
  -H "Content-Type: application/json" \
  -d '{"operator":"张三"}'

# 或者驳回，工单状态保持不变
curl -X POST http://localhost:8080/api/approvals/P-1A2B3C4D/reject \
  -H "Content-Type: application/json" \
  -d '{"operator":"张三"}'

# 运行指标：成功率、P95 延迟、token 消耗、工具失败率（重启清零）
curl http://localhost:8080/api/metrics
```

数据库文件 `oa-ticket.db` 会在首次启动时自动生成，包含 10 个员工、8 条审批规则、20 张工单。

用 Navicat 或 DB Browser for SQLite 打开这个文件可以直接查看数据。

---

## 运行测试

```bash
mvn test
```

30 个用例，分四组：

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `ToolTest` | 10 | 三个只读工具：正常查询、空参数、编号不存在、金额为 null |
| `TicketActionToolTest` | 7 | 写操作：待处理工单可提案、已处理工单拒绝、缺少理由拒绝、提案不改状态 |
| `TicketApprovalServiceTest` | 8 | 确认与驳回：状态变更、审计留痕、重复确认防护、提案失效检测 |
| `AgentServiceTest` | 5 | Agent 循环：正常收敛、步数超限降级、token 预算降级 |

`AgentServiceTest` 用 Mockito 替掉模型客户端，否则降级分支无法稳定复现——真实模型不会配合演出。

---

## 功能进度

- [x] 工单数据模型与查询接口
- [x] 只读查询工具与写操作提案工具（含 30 个单元测试）
- [x] 大模型接入
- [x] Agent 循环与工具调用
- [x] 循环有界控制（步数 / 超时 / token 预算）
- [x] 危险操作人工确认
- [x] 历史工单语义检索（RAG）
- [x] 链路追踪与异常重试
- [ ] 链路追踪与结构化日志
- [ ] 评测集与基线报告
- [ ] Docker 部署
