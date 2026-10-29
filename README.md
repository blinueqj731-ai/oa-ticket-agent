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

**工具方法签名保持极简**

三个工具的方法都只有一个字符串参数（`getTicket(String)`、`getEmployee(String)`、`getRules(String)`）。因为这些方法将来要给模型调用，参数类型一多它就容易传错。一个字符串进、一个结构化 JSON 出，是最不容易出错的形态。

**代码做确定的事，模型做需要理解的事**

以审批规则查询为例：报销类下面有差旅费、办公用品、招待费三条规则，具体适用哪一条需要读懂工单标题和内容的语义。这种判断写死在代码里会又丑又覆盖不全（标题写法千变万化），所以工具只负责把候选规则全部返回，由模型结合工单内容自己挑。

---

## 目录结构

```
oa-ticket-agent/
├── src/main/java/com/example/oaticket/
│   ├── controller/      对外 HTTP 接口
│   ├── repository/      数据访问（JdbcTemplate 手写 SQL）
│   ├── tool/            给 Agent 调用的数据查询工具
│   ├── model/           数据模型
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

启动后访问：

```bash
# 健康检查
curl http://localhost:8080/api/health

# 全部工单（20 条）
curl http://localhost:8080/api/tickets

# 按编号查单条
curl http://localhost:8080/api/tickets/T-20261001-001
```

数据库文件 `oa-ticket.db` 会在首次启动时自动生成，包含 10 个员工、8 条审批规则、20 张工单。

用 Navicat 或 DB Browser for SQLite 打开这个文件可以直接查看数据。

---

## 运行测试

```bash
mvn test
```

覆盖三个查询工具共 10 个用例：正常查询、不存在的编号、空参数、部门经理没有上级、以及"请假单金额必须是 null 而不是 0"这类边界情况。

---

## 功能进度

- [x] 工单数据模型与查询接口
- [x] 三个数据查询工具（含 10 个单元测试）
- [ ] 大模型接入
- [ ] Agent 循环与工具调用
- [ ] 循环有界控制（步数 / 超时 / token 预算）
- [ ] 危险操作人工确认
- [ ] 链路追踪与结构化日志
- [ ] 评测集与基线报告
- [ ] Docker 部署
