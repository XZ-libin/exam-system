-- ============================================================
-- 在线考试系统 · 数据库结构
-- MySQL 8.0 / utf8mb4 / 12 张表 + 2 个视图
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `sys_op_log`;
DROP TABLE IF EXISTS `an_answer_item`;
DROP TABLE IF EXISTS `an_exam_record`;
DROP TABLE IF EXISTS `ex_exam_class`;
DROP TABLE IF EXISTS `ex_exam`;
DROP TABLE IF EXISTS `ex_paper_question`;
DROP TABLE IF EXISTS `ex_paper`;
DROP TABLE IF EXISTS `qz_question`;
DROP TABLE IF EXISTS `qz_category`;
DROP TABLE IF EXISTS `sys_user_role`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_user`;

-- ------------------------------------------------------------
-- 1. sys_user 用户（管理员 / 教师 / 学生），学号即登录名
-- ------------------------------------------------------------
CREATE TABLE `sys_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `username`        VARCHAR(50)  NOT NULL COMMENT '登录名：学号/工号',
  `password`        VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
  `real_name`       VARCHAR(50)  NOT NULL,
  `avatar`          VARCHAR(255) DEFAULT NULL,
  `phone`           VARCHAR(20)  DEFAULT NULL,
  `email`           VARCHAR(100) DEFAULT NULL,
  `class_name`      VARCHAR(50)  DEFAULT NULL COMMENT '班级，学生用',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  `last_login_time` DATETIME     DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`, `deleted`),
  KEY `idx_user_class` (`class_name`),
  KEY `idx_user_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ------------------------------------------------------------
-- 2. sys_role 角色
-- ------------------------------------------------------------
CREATE TABLE `sys_role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(30) NOT NULL COMMENT 'ADMIN / TEACHER / STUDENT',
  `name`        VARCHAR(50) NOT NULL,
  `remark`      VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ------------------------------------------------------------
-- 3. sys_user_role 用户-角色关联
-- ------------------------------------------------------------
CREATE TABLE `sys_user_role` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `role_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_user_role_role` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关联表';

-- ------------------------------------------------------------
-- 4. qz_category 题库分类树（科目 -> 章节）
-- ------------------------------------------------------------
CREATE TABLE `qz_category` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '0 为根节点',
  `name`        VARCHAR(50) NOT NULL,
  `sort_no`     INT         NOT NULL DEFAULT 0,
  `remark`      VARCHAR(255) DEFAULT NULL,
  `creator_id`  BIGINT      DEFAULT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题库分类表';

-- ------------------------------------------------------------
-- 5. qz_question 题目：选项与标准答案用 JSON 存储
-- ------------------------------------------------------------
CREATE TABLE `qz_question` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `category_id` BIGINT      NOT NULL,
  `q_type`      TINYINT     NOT NULL COMMENT '1单选 2多选 3判断 4填空 5简答',
  `content`     TEXT        NOT NULL COMMENT '题干；填空题用 ____ 占位',
  `options`     JSON        DEFAULT NULL COMMENT '[{"key":"A","text":"..."}]，判断/简答为 NULL',
  `answer`      JSON        NOT NULL COMMENT '单选["A"] 多选["A","C"] 判断["T"] 填空["答1","答2"] 简答["参考答案"]',
  `analysis`    TEXT        DEFAULT NULL COMMENT '解析',
  `difficulty`  TINYINT     NOT NULL DEFAULT 3 COMMENT '1最易 - 5最难',
  `score`       DECIMAL(5,1) NOT NULL DEFAULT 2.0 COMMENT '默认分值',
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  `creator_id`  BIGINT      NOT NULL,
  `use_count`   INT         NOT NULL DEFAULT 0 COMMENT '被试卷引用次数',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cat_type_diff` (`category_id`, `q_type`, `difficulty`),
  KEY `idx_question_creator` (`creator_id`),
  KEY `idx_question_content` (`q_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '题目表';

-- ------------------------------------------------------------
-- 6. ex_paper 试卷
-- ------------------------------------------------------------
CREATE TABLE `ex_paper` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `title`           VARCHAR(100) NOT NULL,
  `description`     VARCHAR(500) DEFAULT NULL,
  `category_id`     BIGINT       DEFAULT NULL COMMENT '主科目，用于筛选',
  `total_score`     DECIMAL(6,1) NOT NULL DEFAULT 0,
  `pass_score`      DECIMAL(6,1) NOT NULL DEFAULT 60,
  `suggest_minutes` INT          NOT NULL DEFAULT 60 COMMENT '建议时长',
  `question_count`  INT          NOT NULL DEFAULT 0,
  `build_type`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1人工组卷 2规则抽题',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已归档',
  `creator_id`      BIGINT       NOT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_creator_status` (`creator_id`, `status`),
  KEY `idx_paper_category` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '试卷表';

-- ------------------------------------------------------------
-- 7. ex_paper_question 试卷题目（入卷时写入题目内容快照）
-- ------------------------------------------------------------
CREATE TABLE `ex_paper_question` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `paper_id`    BIGINT       NOT NULL,
  `question_id` BIGINT       NOT NULL,
  `q_type`      TINYINT      NOT NULL COMMENT '题型快照',
  `content`     TEXT         NOT NULL COMMENT '题干快照',
  `options`     JSON         DEFAULT NULL COMMENT '选项快照',
  `answer`      JSON         NOT NULL COMMENT '答案快照，判分用',
  `analysis`    TEXT         DEFAULT NULL,
  `difficulty`  TINYINT      NOT NULL DEFAULT 3,
  `score`       DECIMAL(5,1) NOT NULL COMMENT '本卷分值，可与题库默认分不同',
  `sort_no`     INT          NOT NULL DEFAULT 0,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_question` (`paper_id`, `question_id`),
  KEY `idx_paper_sort` (`paper_id`, `sort_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '试卷题目表(含内容快照)';

-- ------------------------------------------------------------
-- 8. ex_exam 考试场次
-- ------------------------------------------------------------
CREATE TABLE `ex_exam` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `paper_id`         BIGINT       NOT NULL,
  `title`            VARCHAR(100) NOT NULL,
  `description`      VARCHAR(500) DEFAULT NULL,
  `start_time`       DATETIME     NOT NULL,
  `end_time`         DATETIME     NOT NULL,
  `duration_minutes` INT          NOT NULL DEFAULT 60 COMMENT '答题时长',
  `late_minutes`     INT          NOT NULL DEFAULT 15 COMMENT '开考后允许进入的分钟数',
  `max_attempts`     INT          NOT NULL DEFAULT 1 COMMENT '允许作答次数',
  `audience_type`    TINYINT      NOT NULL DEFAULT 1 COMMENT '1全部学生 2指定班级 3指定名单',
  `switch_limit`     INT          NOT NULL DEFAULT 0 COMMENT '0 表示不限制切屏次数',
  `shuffle_question` TINYINT      NOT NULL DEFAULT 0 COMMENT '1 题目乱序',
  `shuffle_option`   TINYINT      NOT NULL DEFAULT 0 COMMENT '1 选项乱序',
  `score_published`  TINYINT      NOT NULL DEFAULT 0 COMMENT '0未发布 1已发布成绩',
  `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '0未发布 1已发布(时间未到) 2进行中 3已结束',
  `creator_id`       BIGINT       NOT NULL,
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_paper` (`paper_id`),
  KEY `idx_time_range` (`start_time`, `end_time`),
  KEY `idx_exam_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试场次表';

-- ------------------------------------------------------------
-- 9. ex_exam_class 考试可见班级
-- ------------------------------------------------------------
CREATE TABLE `ex_exam_class` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `exam_id`     BIGINT      NOT NULL,
  `class_name`  VARCHAR(50) NOT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_class` (`exam_id`, `class_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试可见班级表';

-- ------------------------------------------------------------
-- 10. an_exam_record 答卷主表（一次作答）：计时与判分状态的服务端记录
-- ------------------------------------------------------------
CREATE TABLE `an_exam_record` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `exam_id`         BIGINT       NOT NULL,
  `user_id`         BIGINT       NOT NULL,
  `attempt_no`      INT          NOT NULL DEFAULT 1 COMMENT '第几次作答',
  `start_time`      DATETIME     NOT NULL,
  `deadline_time`   DATETIME     NOT NULL COMMENT 'start_time + duration，强制交卷依据',
  `submit_time`     DATETIME     DEFAULT NULL,
  `question_order`  JSON         DEFAULT NULL COMMENT '乱序后的题目顺序，续考时还原',
  `objective_score` DECIMAL(6,1) DEFAULT NULL,
  `subjective_score` DECIMAL(6,1) DEFAULT NULL,
  `total_score`     DECIMAL(6,1) DEFAULT NULL,
  `pass_flag`       TINYINT      DEFAULT NULL COMMENT '0未及格 1及格',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0作答中 1待阅卷 2已完成 3超时强制交卷',
  `switch_count`    INT          NOT NULL DEFAULT 0 COMMENT '切屏次数',
  `client_ip`       VARCHAR(50)  DEFAULT NULL,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_user_attempt` (`exam_id`, `user_id`, `attempt_no`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_record_exam` (`exam_id`),
  KEY `idx_record_deadline` (`status`, `deadline_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '答卷表';

-- ------------------------------------------------------------
-- 11. an_answer_item 每题作答明细
-- ------------------------------------------------------------
CREATE TABLE `an_answer_item` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `record_id`         BIGINT       NOT NULL,
  `paper_question_id` BIGINT       NOT NULL,
  `question_id`       BIGINT       NOT NULL,
  `user_answer`       JSON         DEFAULT NULL COMMENT '学生答案，统一存数组',
  `correct_flag`      TINYINT      DEFAULT NULL COMMENT '0错误 1正确 2部分正确 NULL未判',
  `score`             DECIMAL(5,1) DEFAULT NULL COMMENT '得分',
  `full_score`        DECIMAL(5,1) NOT NULL COMMENT '本题满分(快照)',
  `review_status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0已自动判分 1待人工阅卷 2已人工阅卷',
  `reviewer_id`       BIGINT       DEFAULT NULL,
  `review_comment`    VARCHAR(500) DEFAULT NULL,
  `sort_no`           INT          NOT NULL DEFAULT 0,
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_paper_question` (`record_id`, `paper_question_id`),
  KEY `idx_record` (`record_id`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_item_question` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '答题明细表';

-- ------------------------------------------------------------
-- 12. sys_op_log 登录与关键操作日志
-- ------------------------------------------------------------
CREATE TABLE `sys_op_log` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT      DEFAULT NULL,
  `username`    VARCHAR(50) DEFAULT NULL,
  `action`      VARCHAR(50) NOT NULL COMMENT 'LOGIN / LOGOUT / SUBMIT / GRADING ...',
  `target`      VARCHAR(100) DEFAULT NULL,
  `detail`      VARCHAR(500) DEFAULT NULL,
  `client_ip`   VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

-- ============================================================
-- 视图
-- ============================================================

-- 每题正确率与难度：只统计已完成（status 2/3）的答卷
CREATE OR REPLACE VIEW `v_question_accuracy` AS
SELECT
  pq.`id`                                                    AS paper_question_id,
  pq.`paper_id`,
  pq.`question_id`,
  pq.`q_type`,
  pq.`difficulty`,
  pq.`score`                                                 AS full_score,
  COUNT(r.`id`)                                              AS answer_total,
  SUM(CASE WHEN r.`status` IN (2, 3) AND ai.`correct_flag` = 1 THEN 1 ELSE 0 END) AS answer_correct,
  ROUND(SUM(CASE WHEN r.`status` IN (2, 3) AND ai.`correct_flag` = 1 THEN 1 ELSE 0 END) * 100
        / NULLIF(COUNT(r.`id`), 0), 2)                      AS accuracy
FROM `ex_paper_question` pq
       LEFT JOIN `an_answer_item` ai ON ai.`paper_question_id` = pq.`id`
       LEFT JOIN `an_exam_record` r ON r.`id` = ai.`record_id`
GROUP BY pq.`id`, pq.`paper_id`, pq.`question_id`, pq.`q_type`, pq.`difficulty`, pq.`score`;

-- 分数段分布：按 10 分一档
CREATE OR REPLACE VIEW `v_score_distribution` AS
SELECT
  r.`exam_id`,
  FLOOR(r.`total_score` / 10) * 10 AS score_section,
  COUNT(*)                          AS person_num
FROM `an_exam_record` r
WHERE r.`status` IN (2, 3) AND r.`total_score` IS NOT NULL
GROUP BY r.`exam_id`, FLOOR(r.`total_score` / 10) * 10;

SET FOREIGN_KEY_CHECKS = 1;
