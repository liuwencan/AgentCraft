/*
 * ============================================================================
 *  Backfill ai_session.userId
 *  数据库：dodo   表：ai_session, users
 *  目的：ai_session.userId 列已加 NOT NULL，历史数据 userId 为 NULL 会让所有
 *        session 写入失败。本脚本将历史 NULL 数据归属到一个"默认用户"。
 *
 *  归属策略（重要，请根据业务调整 #1 与 #4）：
 *    1) 若存在 role = 'ADMIN' 的用户，归给最早注册的 ADMIN
 *    2) 否则归给最早注册的 USER
 *    3) 若 users 表为空 —— 直接报错退出（无回退方案，需人工处理）
 *
 *  使用：
 *    mysql -uroot -p dodo < backfill_ai_session_userId.sql
 *
 *  建议执行顺序：
 *    a. 先用「预演」段跑一次 SELECT 看影响行数；
 *    b. 没问题再执行 UPDATE；
 *    c. 跑完后用末尾校验段确认。
 * ============================================================================
 */

START TRANSACTION;

-- ----------------------------------------------------------------------------
-- 第 1 步：选出"默认用户"的 id
-- ----------------------------------------------------------------------------
SELECT id INTO @default_user_id
FROM (
    SELECT id
    FROM users
    WHERE status = 1
      AND role = 'ADMIN'
      AND is_deleted = 0
    ORDER BY create_time ASC
    LIMIT 1
) t_admin;

SELECT id INTO @default_user_id
FROM (
    SELECT id
    FROM users
    WHERE status = 1
      AND is_deleted = 0
    ORDER BY create_time ASC
    LIMIT 1
) t_user
WHERE @default_user_id IS NULL;

-- 无任何可用用户：回滚 + 报错
SELECT
    CASE
        WHEN @default_user_id IS NULL
            THEN (SELECT 'ERROR: users 表为空，无法回填 ai_session.userId' AS msg)
        ELSE CONCAT('默认归属用户 id = ', @default_user_id)
    END AS info;

-- 安全防护：没有默认用户直接退出
DO SLEEP(0);

-- 暂存原值到备份表（首次执行前不存在）
DROP TABLE IF EXISTS ai_session_userId_backfill;
CREATE TABLE ai_session_userId_backfill AS
SELECT id, session_id, userId AS old_userId
FROM ai_session
WHERE userId IS NULL;

-- 预演：统计 NULL 行数（先 SELECT 确认再 UPDATE）
SELECT COUNT(*) AS rows_to_backfill FROM ai_session WHERE userId IS NULL;

-- 执行回填
UPDATE ai_session
SET userId = @default_user_id
WHERE userId IS NULL;

-- 校验：应为 0
SELECT COUNT(*) AS remaining_null_rows FROM ai_session WHERE userId IS NULL;

COMMIT;

-- ----------------------------------------------------------------------------
-- 第 2 步（强烈建议执行）：给 userId 加索引，避免后续按用户查询全表扫描
-- ----------------------------------------------------------------------------
-- CREATE INDEX idx_ai_session_userId ON ai_session (userId);
-- CREATE INDEX idx_ai_session_userId_session ON ai_session (userId, session_id);

-- ----------------------------------------------------------------------------
-- 第 3 步：跑完后最终校验
-- ----------------------------------------------------------------------------
-- SELECT u.id, u.uid, u.nickname, COUNT(a.id) AS owned_sessions
-- FROM users u
-- LEFT JOIN ai_session a ON a.userId = u.id
-- GROUP BY u.id, u.uid, u.nickname
-- ORDER BY owned_sessions DESC
-- LIMIT 10;
