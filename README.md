# 在线考试系统（SpringBoot + MySQL + Vue3）

课程作业 · 课题 6：用户中心、题库管理、试卷管理、考试管理、在线答题、成绩管理、统计分析。
全部功能在 Docker 容器内运行，本机不需要安装 JDK、Maven、Node 或 MySQL。

## 1. 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | SpringBoot 3.2.5 + JDK 17 + MyBatis-Plus 3.5.5 + JWT(jjwt) + springdoc-openapi |
| 数据库 | MySQL 8.0，12 张表 + 2 个视图，utf8mb4 |
| 前端 | Vue 3.4 + Vite 5 + vue-router 4 + Pinia + Element Plus + ECharts 5 |
| 运行 | Docker Compose 三容器（mysql / backend / web） |

## 2. 目录

```
exam-system/
├── docker-compose.yml        生产：三镜像一把起
├── docker-compose.dev.yml    开发：源码挂载 + 热更新
├── docker/                   backend.Dockerfile、web.Dockerfile、nginx.conf
│   ├── demo-run.mjs          端到端链路 + 演示数据生成
│   ├── smoke-check.mjs       22 个读接口自检
│   ├── write-path-check.mjs  50 项写接口断言
│   ├── functional-test.mjs   按 7 大模块的功能测试
│   ├── paper-flow-check.mjs  试卷专项（抽题/分值/快照/锁定）
│   ├── bug-probe.mjs         历史缺陷复验探针
│   └── verify-findings.mjs   高危问题复核探针
├── db/01_schema.sql          建表（12 表 + 2 视图）
├── db/02_seed.sql            演示数据（3 角色 / 16 用户 / 9 分类 / 40 题 / 2 套卷 / 3 场考试）
├── backend/                  SpringBoot 工程（controller-service-mapper-entity）
└── web/                      Vue3 工程
```

## 3. 启动

```bash
cd exam-system

# 方式一：生产模式（nginx 托管打包后的前端，最接近交付形态）
docker compose up -d --build      # 前端 http://localhost:8083  后端 http://localhost:8082

# 方式二：开发模式（改代码即热更新）
docker compose -f docker-compose.dev.yml up -d
# 前端 http://localhost:5174  后端 http://localhost:8082  MySQL 127.0.0.1:13306
```

第一次启动时 MySQL 容器会自动执行 `db/*.sql` 建表并灌入演示数据。重置数据：

```bash
docker compose rm -sf mysql && docker volume rm exam-system_exam-mysql-data && docker compose up -d
```

接口文档：`http://localhost:8082/swagger-ui.html`，只在开发模式下开放（`EXAM_API_DOCS=true`）；生产模式默认关掉，避免把接口清单暴露给外部。

## 4. 演示账号（初始密码统一 `123456`）

| 角色 | 账号 | 姓名 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `admin` | 系统管理员 | 用户与全量数据 |
| 教师 | `T2025001` | 李思远 | 数据结构题库、组卷、阅卷 |
| 教师 | `T2025002` | 王敏 | 计算机网络题库 |
| 学生 | `20230101` … `20230107` | 计算机2101 | 7 人，「数据结构期末考试」已有 7 份已判分成绩 |
| 学生 | `20230111` … `20230116` | 计算机2102 | 6 人，明天那场考试；`20230116` 处于禁用状态 |

## 5. 十分钟演示路径

1. `T2025001` 登录 → 题库管理：左树筛分类、按题型/难度检索、新增一题（试多种题型）。
2. 试卷管理 → 规则组卷：指定「分类 + 题型 × 数量 × 难度」抽题 → 进编辑页调分值 → 预览 → 发布。
3. 考试管理 → 新建考试：选试卷、时间窗、时长、班级范围、切屏上限、乱序开关 → 发布。
4. 换 `20230111` 登录 → 我的考试 → 进入考试：答题卡、自动保存、刷新页面后续考（倒计时不重置）。
5. 交卷 → 客观题立刻出分；简答题显示「待阅卷」。
6. 回教师 → 主观题阅卷：给分 + 评语 → 提交后自动合分。
7. 考试管理 → 成绩发布（有未阅完的答卷会被拒绝）→ 学生端查看成绩单与逐题解析，学习分析出图。
8. 统计分析：分数段分布、每题正确率与区分度、班级对比。

一次生成整场考试的真实作答数据（7 名学生答题 + 教师阅卷 + 成绩发布）：

```bash
docker run --rm --network exam-system_exam-net -e TZ=Asia/Shanghai \
  -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api \
  node:24-alpine node /work/demo-run.mjs
```

## 6. 自检脚本

| 脚本 | 覆盖 | 当前结果 |
| --- | --- | --- |
| `smoke-check.mjs` | 22 个读接口连通性与返回结构 | 全部可用 |
| `write-path-check.mjs` | 53 项写接口断言（增删改、校验、越权、导出） | 53/53 |
| `functional-test.mjs` | 7 大模块功能测试（含强制收卷、自然超时、乱序、续考） | 77/77 |
| `paper-flow-check.mjs` | 规则抽题、分值校验、快照不可变、发布锁定 | 12/12 |
| `bug-probe.mjs` | 11 条历史缺陷复验 | 0 条成立 |
| `verify-findings.mjs` | 9 条高危问题复核 | 0 条成立 |
| `hardening-check.mjs` | 并发阅卷/组卷/切屏/交卷、跨教师授权、状态机、入参边界 | 16/16 |
| `security-check.mjs` | 52 项安全断言，分 6 组：认证与会话、越权、SQL 注入、存储型 XSS、配置与响应头、防作弊 | 52 通过 / 0 失败 / 1 风险项 |
| `demo-run.mjs` | 端到端业务链路 | 通过 |

统一跑法（在 `exam-system` 目录下）：

```bash
docker run --rm --network exam-system_exam-net -e TZ=Asia/Shanghai \
  -v "$PWD/docker:/work:ro" -e API=http://backend:8082/api \
  node:24-alpine node /work/functional-test.mjs
```

`security-check.mjs` 还要多给一个前端地址，用来检查 nginx 的响应头：`-e WEB=http://web`。
它会用 `application.yml` 里的默认密钥自行签发 HS256 令牌，以此验证「伪造令牌能否越权」，跑完会软删除自己造的测试题与分类。

判分规则另有 7 条 JUnit 单测：

```bash
docker run --rm -v "$PWD/backend":/src -v exam-system_exam-m2-repo:/root/.m2/repository \
  -w /src maven:3.9-eclipse-temurin-17 mvn -B test
```

## 7. 需求 → 表 → 接口 → 页面 对照

| 模块 | 主要数据表 | 关键接口 | 页面 |
| --- | --- | --- | --- |
| 用户中心 | `sys_user` `sys_role` `sys_user_role` `sys_op_log` | `/api/auth/*` `/api/users*` | `login.vue` `admin/accounts.vue` `profile.vue` |
| 题库管理 | `qz_category` `qz_question` | `/api/categories/tree` `/api/questions*` | `admin/questions.vue` |
| 试卷管理 | `ex_paper` `ex_paper_question` | `/api/papers*`（含 `auto-generate`、`preview`、`publish`） | `admin/papers.vue` `admin/paper-edit.vue` |
| 考试管理 | `ex_exam` `ex_exam_class` | `/api/exams*`（`status`、`publish-score`、`force-submit`） | `admin/exams.vue` |
| 在线答题 | `an_exam_record` `an_answer_item` | `/api/exam/{id}/enter` `.../answers` `.../switch` `.../submit` | `student/take.vue` |
| 成绩管理 | `an_exam_record` `an_answer_item` | `/api/grading/*` `/api/scores*` | `admin/grading.vue` `admin/scores.vue` `student/result.vue` |
| 统计分析 | 上述表 + `v_question_accuracy` `v_score_distribution` | `/api/stats/*` | `admin/statistics.vue` `admin/dashboard.vue` `student/analysis.vue` |

## 8. 判分规则（`JudgeService`，7 条单测覆盖）

| 题型 | 规则 |
| --- | --- |
| 单选 / 判断 | 答案唯一且相等（忽略大小写与首尾空格）得满分 |
| 多选 | 全对满分；漏选且无错选得半分（`exam.multiple-half-score` 可关）；含错选 0 分 |
| 填空 | 按空均分，同一空可用 `|` 写多个可接受答案，忽略大小写 |
| 简答 | 一律转「待阅卷」由教师给分，留空也不例外 |
| 未作答 | 客观题留空按 0 分计 |

## 9. 三处设计取舍

- **计时以服务端为准**：开考时算出 `deadline_time`，前端倒计时只用于展示；刷新、换设备、改本机时间都改不了死线，超时由 `SubmitTimeoutTask` 每 20 秒兜底强制交卷。
- **入卷即快照**：题目进试卷时把题干、选项、答案、分值一起复制进 `ex_paper_question`，之后题库怎么改都不影响已发布考卷与历史成绩；题库 `use_count` 由快照表反算，被引用的题目不允许删除。
- **令牌只当身份声明**：JWT 只认用户 id，角色、班级、姓名每次请求回库取最新值。改角色/换班/禁用账号立即生效，也避免拿到签名密钥就能伪造 `roles:["ADMIN"]` 提权。

## 10. 安全测试与已知边界（答辩时可直接说明）

`security-check.mjs` 按 OWASP 常见六类打的结论，已经落地的措施：

- **认证**：无令牌/过期/畸形/`alg=none`/错误密钥签发的令牌全部被拒；用默认密钥伪造 `roles:["ADMIN"]` 也提不了权，因为角色每次回库读（`AuthInterceptor`）。
- **越权**：学生调 12 个管理端接口全部 `1002`；教师调管理员专属接口被拒；读别人的答卷、账号详情按资源归属拦。
- **注入**：MyBatis 映射文件里没有一处 `${}` 拼接，8 种注入与通配符载荷（`' OR '1'='1`、`1'--`、`%`、`_`、`SLEEP(3)`）都不改变查询语义，`keyword` 走 `LikeUtil` 转义。
- **XSS**：题目/用户名等内容全部以文本插值渲染（前端无 `v-html`），`avatar` 用 `@Pattern` 限制成 `http(s)://` 或站内路径，`javascript:` 协议直接拒。
- **配置与传输**：nginx 关掉 `Server` 版本并加 `CSP / X-Frame-Options / X-Content-Type-Options / Referrer-Policy`；生产模式不提供 `/v3/api-docs`、`/swagger-ui.html`、actuator；异常响应不回显 SQL 与堆栈；`/api/health` 只报 `ok`。
- **写入边界**：列表分页 `size` 夹到 200；收列表的接口（批量录题、文本导入、组卷、抽题规则、考试班级、阅卷明细、作答提交、角色分配）统一过 `BatchLimit`，一次 600 题会被拒绝且一条都不落库。
- **防作弊与成绩可见性**：未发布时成绩单不含总分、对错与标准答案；学生取不到题库列表与试卷答案键；切屏次数由服务端累加。

仍是风险、没有做成代码的地方：

- 演示账号统一 `123456`，且未做登录失败次数限制/验证码。部署时要换 `JWT_SECRET`（`application.yml` 里留了默认值），初始密码改 `exam.default-password`。
- MySQL 端口 13306 映射到了宿主机，方便答辩演示，生产要去掉 `docker-compose.yml` 里的 `ports`。
- 手机号/邮箱对非管理员已脱敏（`139****`、`z***@exam.edu`），但教师仍需按班级名单联系学生，`className` 未做遮蔽。
- 密码用 BCrypt 存储，没有做修改密码后的令牌吊销；令牌有效期 12 小时，未做 refresh token。
- 考试可见范围支持「全部学生」与「指定班级」；第三种「指定学号」复用 `ex_exam_class` 存学号，界面暂未开放。
- 批量导入按纯文本格式（页面内可下载模板），未做 Excel 解析。
- 主观题只支持简答一种题型，评分按要点人工判断。
- 题库与试卷的授权口径是「仅创建者与管理员可改删」，阅卷限自己创建的考试；同科目教师协作需由管理员代为修改。

## 11. 常见问题

- 端口被占：本机若已有 MySQL/Nginx 占用，改 `docker-compose*.yml` 里的宿主端口即可（当前用 13306 / 8082 / 8083 / 5174）。
- 拉镜像慢：给 Docker Desktop 配置国内 registry mirror；`maven:3.9-eclipse-temurin-17`、`mysql:8.0`、`node:24-alpine`、`nginx` 是本项目需要的镜像。
- npm 装不动：容器里已统一走 `registry.npmmirror.com`。
- 手工进容器执行 SQL 一定要带字符集：`docker exec exam-mysql mysql --default-character-set=utf8mb4 -uroot -pexam123456 -D exam_db -e "..."`，否则中文会写成乱码，学生就会「不在参考范围内」。
- 忘记密码：管理员在用户中心点「重置密码」，会返回新的初始密码。
