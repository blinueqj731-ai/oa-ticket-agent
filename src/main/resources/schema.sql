-- OA 智能工单助手 建表脚本
-- 全部用 IF NOT EXISTS：每次启动都会执行，已存在的表不会被重复创建

-- 员工表：工单处理时要查申请人属于哪个部门、什么职级、上级是谁
CREATE TABLE IF NOT EXISTS employees (
    employee_no TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    department  TEXT NOT NULL,
    level       TEXT NOT NULL,
    manager_no  TEXT
);

-- 工单表
-- amount 允许为空：请假、权限申请这类工单没有金额
CREATE TABLE IF NOT EXISTS tickets (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_no    TEXT NOT NULL UNIQUE,
    type         TEXT NOT NULL,
    title        TEXT NOT NULL,
    content      TEXT NOT NULL,
    applicant_no TEXT NOT NULL,
    amount       REAL,
    status       TEXT NOT NULL DEFAULT 'PENDING',
    created_at   TEXT NOT NULL
);

-- 审批规则表
-- max_amount 为空表示这类工单不限额
-- 加 (ticket_type, description) 唯一约束，配合 data.sql 里的 INSERT OR IGNORE，
-- 保证重复启动不会把规则插成两份
CREATE TABLE IF NOT EXISTS approval_rules (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_type    TEXT NOT NULL,
    max_amount     REAL,
    required_level TEXT NOT NULL,
    description    TEXT NOT NULL,
    UNIQUE (ticket_type, description)
);

-- 操作日志表：Agent 每次调工具、每次给出建议都往这里写，方便回溯
CREATE TABLE IF NOT EXISTS operation_logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_no  TEXT NOT NULL,
    action     TEXT NOT NULL,
    operator   TEXT,
    detail     TEXT,
    created_at TEXT NOT NULL
);

-- 待确认提案表
-- 模型想修改工单状态时不直接动 tickets，而是往这张表写一条提案，
-- 等人工调用确认接口后才真正落地。写操作与只读查询由此彻底分开
CREATE TABLE IF NOT EXISTS ticket_proposals (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    proposal_id TEXT NOT NULL UNIQUE,
    ticket_no   TEXT NOT NULL,
    from_status TEXT NOT NULL,
    to_status   TEXT NOT NULL,
    reason      TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'PENDING',
    created_at  TEXT NOT NULL,
    decided_at  TEXT,
    operator    TEXT
);

-- 历史工单向量表
-- 单独建表而不往 tickets 加列，是为了让向量与业务数据解耦：
-- 换 embedding 模型时只需清空这张表重建，不碰工单数据
CREATE TABLE IF NOT EXISTS ticket_embedding (
    ticket_no TEXT PRIMARY KEY,
    text      TEXT NOT NULL,
    embedding BLOB NOT NULL
);

-- 常用查询加索引：按单号查、按状态查是最高频的两个
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);
CREATE INDEX IF NOT EXISTS idx_logs_ticket_no ON operation_logs(ticket_no);
CREATE INDEX IF NOT EXISTS idx_proposals_status ON ticket_proposals(status);
CREATE INDEX IF NOT EXISTS idx_proposals_ticket ON ticket_proposals(ticket_no);
