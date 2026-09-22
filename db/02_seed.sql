-- ============================================================
-- 在线考试系统 · 演示数据
-- 所有账号初始密码：123456（BCrypt 密文，与后端 BCryptPasswordEncoder 一致）
-- 角色：admin(管理员) / T2025001 T2025002(教师) / 20230101..20230112(学生)
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------ 角色
INSERT INTO `sys_role` (`id`, `code`, `name`, `remark`) VALUES
  (1, 'ADMIN', '系统管理员', '用户与全量数据管理'),
  (2, 'TEACHER', '教师', '题库、试卷、考试、阅卷、统计'),
  (3, 'STUDENT', '学生', '在线答题与成绩查询');

-- ------------------------------------------------------------ 用户
-- 密码明文统一为 123456
INSERT INTO `sys_user`
  (`id`, `username`, `password`, `real_name`, `phone`, `email`, `class_name`, `status`) VALUES
  (1, 'admin',    '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '系统管理员', '13900000001', 'admin@exam.edu', NULL, 1),
  (2, 'T2025001', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '李思远', '13900000002', 'lisy@exam.edu', NULL, 1),
  (3, 'T2025002', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '王敏', '13900000003', 'wangmin@exam.edu', NULL, 1);

INSERT INTO `sys_user`
  (`id`, `username`, `password`, `real_name`, `phone`, `email`, `class_name`, `status`) VALUES
  (4,  '20230101', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '张子涵', NULL, NULL, '计算机2101', 1),
  (5,  '20230102', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '刘雨欣', NULL, NULL, '计算机2101', 1),
  (6,  '20230103', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '陈浩然', NULL, NULL, '计算机2101', 1),
  (7,  '20230104', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '赵欣怡', NULL, NULL, '计算机2101', 1),
  (8,  '20230105', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '孙嘉豪', NULL, NULL, '计算机2101', 1),
  (9,  '20230106', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '周诗涵', NULL, NULL, '计算机2101', 1),
  (10, '20230107', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '吴俊杰', NULL, NULL, '计算机2101', 1),
  (11, '20230111', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '郑雅琳', NULL, NULL, '计算机2102', 1),
  (12, '20230112', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '冯天宇', NULL, NULL, '计算机2102', 1),
  (13, '20230113', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '蒋若彤', NULL, NULL, '计算机2102', 1),
  (14, '20230114', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '韩明轩', NULL, NULL, '计算机2102', 1),
  (15, '20230115', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '曹佳怡', NULL, NULL, '计算机2102', 1),
  (16, '20230116', '$2a$10$7VxsaIJ0mTJ/kvNqvOIVRupaqvFWXubYm0LF5J4x33RLNmCN6vLCW', '彭子墨', NULL, NULL, '计算机2102', 0);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
  (1, 1), (2, 2), (3, 2);
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
  SELECT `id`, 3 FROM `sys_user` WHERE `id` >= 4;

-- ------------------------------------------------------------ 题库分类
INSERT INTO `qz_category` (`id`, `parent_id`, `name`, `sort_no`, `remark`, `creator_id`) VALUES
  (1, 0, '数据结构', 1, '计算机专业核心课', 2),
  (2, 0, '计算机网络', 2, '计算机专业核心课', 3),
  (3, 1, '线性表与栈队列', 1, NULL, 2),
  (4, 1, '树与二叉树', 2, NULL, 2),
  (5, 1, '图论基础', 3, NULL, 2),
  (6, 1, '查找与排序', 4, NULL, 2),
  (7, 2, '传输层', 1, NULL, 3),
  (8, 2, '网络层与IP', 2, NULL, 3),
  (9, 2, '应用层', 3, NULL, 3);

-- ------------------------------------------------------------ 题目：数据结构
-- 题型：1单选 2多选 3判断 4填空 5简答；难度 1-5
INSERT INTO `qz_question`
  (`id`, `category_id`, `q_type`, `content`, `options`, `answer`, `analysis`, `difficulty`, `score`, `status`, `creator_id`) VALUES
  (1, 3, 1, '在长度为 n 的顺序表中间插入一个元素，平均需要移动约多少个元素？',
   '[{"key":"A","text":"n/2"},{"key":"B","text":"n"},{"key":"C","text":"log n"},{"key":"D","text":"1"}]',
   '["A"]', '插入位置等概率时平均移动 n/2 个元素。', 2, 4.0, 1, 2),
  (2, 3, 1, '链表与顺序表相比，最主要的优势是？',
   '[{"key":"A","text":"支持随机访问"},{"key":"B","text":"插入删除不必移动大量元素"},{"key":"C","text":"存储密度更高"},{"key":"D","text":"一定更省内存"}]',
   '["B"]', '链表靠改指针完成插入删除，代价是不支持随机访问。', 1, 4.0, 1, 2),
  (3, 3, 1, '元素 a、b、c、d 依次入栈，出栈序列不可能是？',
   '[{"key":"A","text":"abcd"},{"key":"B","text":"dcba"},{"key":"C","text":"cadb"},{"key":"D","text":"badc"}]',
   '["C"]', 'c 先出栈时 a、b 仍在栈中，b 不可能在 a 之前出栈。', 4, 4.0, 1, 2),
  (4, 3, 1, '循环队列用容量为 n 的数组实现，通常最多存放多少个元素？',
   '[{"key":"A","text":"n"},{"key":"B","text":"n-1"},{"key":"C","text":"n/2"},{"key":"D","text":"不确定"}]',
   '["B"]', '牺牲一个单元区分队空与队满（不引入 size 标记时）。', 3, 4.0, 1, 2),
  (5, 4, 1, '含有 n 个结点的完全二叉树，其深度约为？',
   '[{"key":"A","text":"log2 n + 1"},{"key":"B","text":"n/2"},{"key":"C","text":"2n"},{"key":"D","text":"n²"}]',
   '["A"]', '完全二叉树深度与结点数呈对数关系。', 2, 4.0, 1, 2),
  (6, 4, 1, '二叉排序树查找的平均时间复杂度在树平衡时是？',
   '[{"key":"A","text":"O(1)"},{"key":"B","text":"O(log n)"},{"key":"C","text":"O(n)"},{"key":"D","text":"O(n log n)"}]',
   '["B"]', '平衡时树高约 log n。', 2, 4.0, 1, 2),
  (7, 5, 1, 'n 个顶点的无向完全图共有多少条边？',
   '[{"key":"A","text":"n(n-1)/2"},{"key":"B","text":"n(n-1)"},{"key":"C","text":"n²"},{"key":"D","text":"n-1"}]',
   '["A"]', '每对顶点一条边，去掉重复计数。', 2, 4.0, 1, 2),
  (8, 6, 1, '快速排序在最坏情况下的时间复杂度是？',
   '[{"key":"A","text":"O(n log n)"},{"key":"B","text":"O(n²)"},{"key":"C","text":"O(n)"},{"key":"D","text":"O(log n)"}]',
   '["B"]', '每次划分极度不均（如已有序且取首元素为枢轴）时退化。', 2, 4.0, 1, 2),
  (9, 6, 1, '下列排序算法中，不稳定的是？',
   '[{"key":"A","text":"冒泡排序"},{"key":"B","text":"插入排序"},{"key":"C","text":"简单选择排序"},{"key":"D","text":"归并排序"}]',
   '["C"]', '选择排序交换会打乱相同关键字的相对次序。', 3, 4.0, 1, 2),
  (10, 3, 2, '关于栈和队列，下列说法正确的有？',
   '[{"key":"A","text":"栈是后进先出"},{"key":"B","text":"队列是先进先出"},{"key":"C","text":"栈只能顺序存储"},{"key":"D","text":"递归可以用栈模拟"},{"key":"E","text":"双端队列只能一端进另一端出"}]',
   '["A","B","D"]', '栈可链式存储；双端队列两端都可进出。', 3, 6.0, 1, 2),
  (11, 4, 2, '二叉树的遍历方式包括？',
   '[{"key":"A","text":"前序遍历"},{"key":"B","text":"中序遍历"},{"key":"C","text":"后序遍历"},{"key":"D","text":"层次遍历"},{"key":"E","text":"折半遍历"}]',
   '["A","B","C","D"]', '没有“折半遍历”这种二叉树遍历。', 2, 6.0, 1, 2),
  (12, 5, 2, '关于图的存储结构，正确的有？',
   '[{"key":"A","text":"邻接矩阵便于判断两点是否有边"},{"key":"B","text":"邻接表更省稀疏图空间"},{"key":"C","text":"邻接矩阵一定有向图专用"},{"key":"D","text":"逆邻接表用于有向图求入度"}]',
   '["A","B","D"]', '邻接矩阵对有向图和无向图都适用。', 3, 6.0, 1, 2),
  (13, 6, 2, '哪些排序算法平均时间复杂度为 O(n log n)？',
   '[{"key":"A","text":"归并排序"},{"key":"B","text":"堆排序"},{"key":"C","text":"快速排序"},{"key":"D","text":"希尔排序"},{"key":"E","text":"基数排序"}]',
   '["A","B","C"]', '希尔与基数的复杂度依赖增量/基数，通常不写成 n log n。', 4, 6.0, 1, 2),
  (14, 4, 2, '关于哈夫曼树，正确的有？',
   '[{"key":"A","text":"带权路径长度最短的二叉树"},{"key":"B","text":"权值大的结点离根更近"},{"key":"C","text":"一定是完全二叉树"},{"key":"D","text":"前缀编码可用它构造"}]',
   '["A","B","D"]', '哈夫曼树形态由权值决定，一般不是完全二叉树。', 4, 6.0, 1, 2),
  (15, 3, 3, '在单链表中删除结点的时间复杂度一定是 O(1)。', NULL, '["F"]',
   '查找该结点仍需 O(n)，只有已持有前驱指针时才是 O(1)。', 2, 4.0, 1, 2),
  (16, 4, 3, '中序遍历二叉排序树可以得到一个递增序列。', NULL, '["T"]',
   '二叉排序树的定义保证中序有序。', 1, 4.0, 1, 2),
  (17, 6, 3, '冒泡排序是稳定排序。', NULL, '["T"]',
   '只在相邻逆序时交换，相等元素不交换。', 2, 4.0, 1, 2),
  (18, 3, 4, '在长度为 n 的单链表中按位查找的时间复杂度是 ____，插入删除的时间复杂度是 ____（已定位到前驱）。',
   NULL, '["O(n)","O(1)"]', '按位查找需遍历；已定位前驱后改指针是常数时间。', 2, 8.0, 1, 2),
  (19, 4, 4, '一棵有 n 个结点的二叉树，最多有 ____ 层；深度为 d 的二叉树最多有 ____ 个结点。',
   NULL, '["n","2^d-1"]', '退化成单链时最多 n 层；满二叉树结点数 2^d-1。', 3, 8.0, 1, 2),
  (20, 6, 5, '简述快速排序的基本思想，并说明为什么最坏情况会退化为 O(n²)，给出一种改进办法。',
   NULL, '["分治：选枢轴将序列划分成两部分，左边不大于枢轴、右边不小于枢轴，再递归处理两侧。若每次划分极度不均（例如序列已基本有序且固定取首元素作枢轴），递归树退化为链状，比较次数达 n(n-1)/2，即 O(n²)。改进：采用随机选取枢轴或三数取中法，也可在小规模子表切换为插入排序。"]',
   '考察分治思想与退化条件。', 4, 20.0, 1, 2);

-- ------------------------------------------------------------ 题目：计算机网络
INSERT INTO `qz_question`
  (`id`, `category_id`, `q_type`, `content`, `options`, `answer`, `analysis`, `difficulty`, `score`, `status`, `creator_id`) VALUES
  (21, 7, 1, 'TCP 建立连接需要几次握手？',
   '[{"key":"A","text":"2 次"},{"key":"B","text":"3 次"},{"key":"C","text":"4 次"},{"key":"D","text":"1 次"}]',
   '["B"]', 'SYN、SYN+ACK、ACK 三次握手。', 1, 3.0, 1, 3),
  (22, 7, 1, 'UDP 最主要的特征是？',
   '[{"key":"A","text":"面向连接、可靠传输"},{"key":"B","text":"无连接、不保证顺序与可靠"},{"key":"C","text":"有拥塞控制"},{"key":"D","text":"必须重传"}]',
   '["B"]', 'UDP 是无连接不可靠但低开销的传输协议。', 1, 3.0, 1, 3),
  (23, 8, 1, 'IP 地址 192.168.1.10/24 的网络前缀长度是？',
   '[{"key":"A","text":"16 位"},{"key":"B","text":"24 位"},{"key":"C","text":"32 位"},{"key":"D","text":"8 位"}]',
   '["B"]', '/24 表示前 24 位是网络号。', 2, 3.0, 1, 3),
  (24, 8, 1, '路由器工作的主要层次是？',
   '[{"key":"A","text":"物理层"},{"key":"B","text":"数据链路层"},{"key":"C","text":"网络层"},{"key":"D","text":"应用层"}]',
   '["C"]', '路由器按 IP 前缀转发，属于网络层设备。', 1, 3.0, 1, 3),
  (25, 9, 1, 'HTTP 默认使用的传输层协议与端口是？',
   '[{"key":"A","text":"TCP 80"},{"key":"B","text":"UDP 53"},{"key":"C","text":"TCP 443"},{"key":"D","text":"UDP 67"}]',
   '["A"]', 'HTTP/TCP 80，HTTPS 才是 TCP 443。', 2, 3.0, 1, 3),
  (26, 9, 1, 'DNS 的主要作用是？',
   '[{"key":"A","text":"分配 IP 地址"},{"key":"B","text":"域名到 IP 地址的解析"},{"key":"C","text":"数据链路层编址"},{"key":"D","text":"流量控制"}]',
   '["B"]', '分配地址是 DHCP 的工作。', 1, 3.0, 1, 3),
  (27, 7, 2, 'TCP 提供的机制包括？',
   '[{"key":"A","text":"序号与确认"},{"key":"B","text":"超时重传"},{"key":"C","text":"滑动窗口流量控制"},{"key":"D","text":"拥塞避免"},{"key":"E","text":"广播发送"}]',
   '["A","B","C","D"]', 'TCP 是点对点可靠协议，不广播。', 3, 4.0, 1, 3),
  (28, 8, 2, '关于 ARP，正确的有？',
   '[{"key":"A","text":"由 IP 地址查询 MAC 地址"},{"key":"B","text":"结果缓存在 ARP 表中"},{"key":"C","text":"工作在网络层之下"},{"key":"D","text":"用于域名解析"}]',
   '["A","B","C"]', '域名解析由 DNS 完成。', 4, 4.0, 1, 3),
  (29, 9, 2, '属于应用层协议的有？',
   '[{"key":"A","text":"FTP"},{"key":"B","text":"SMTP"},{"key":"C","text":"HTTP"},{"key":"D","text":"ICMP"}]',
   '["A","B","C"]', 'ICMP 属于网络层。', 3, 4.0, 1, 3),
  (30, 7, 3, 'TCP 是全双工通信。', NULL, '["T"]',
   '双方都设有发送与接收缓冲，可同时收发。', 2, 3.0, 1, 3),
  (31, 8, 3, '公网 IP 地址在 IPv4 中已经耗尽，因此 IPv6 不再需要 NAT。', NULL, '["F"]',
   'IPv6 地址充足，设计目标是不依赖 NAT，但前半句与结论无因果关系。', 4, 3.0, 1, 3),
  (32, 8, 4, 'IPv4 地址由 ____ 位二进制组成；/26 子网的可用主机数是 ____ 个。',
   NULL, '["32","62"]', '2^6-2=62，扣除网络地址与广播地址。', 4, 6.0, 1, 3),
  (33, 9, 5, '简述浏览器输入网址到页面显示经过的主要网络过程，至少提到 DNS、TCP、HTTP 三个环节。',
   NULL, '["先 DNS 解析域名得到 IP；再与服务器 TCP 三次握手（HTTPS 还要做 TLS 握手）；然后发送 HTTP 请求，服务器返回响应报文；浏览器解析 HTML 构建 DOM 并加载资源完成渲染；最后连接可复用或关闭。"]',
   '考查分层流程的整体理解。', 4, 8.0, 1, 3);

-- 停用中的题目（用于演示 status 筛选与逻辑删除保护）
INSERT INTO `qz_question`
  (`id`, `category_id`, `q_type`, `content`, `options`, `answer`, `analysis`, `difficulty`, `score`, `status`, `creator_id`) VALUES
  (34, 3, 1, '（已停用）顺序表相比链表的缺点是？',
   '[{"key":"A","text":"插入删除需要移动元素"},{"key":"B","text":"不支持随机访问"},{"key":"C","text":"需要额外指针空间"},{"key":"D","text":"一定更慢"}]',
   '["A"]', '备用于筛选演示。', 2, 4.0, 0, 2),
  (35, 5, 1, 'DFS 通常使用的辅助数据结构是？',
   '[{"key":"A","text":"栈"},{"key":"B","text":"队列"},{"key":"C","text":"堆"},{"key":"D","text":"并查集"}]',
   '["A"]', '深度优先用栈（递归即隐式栈）。', 2, 4.0, 1, 2),
  (36, 6, 1, 'n 个元素进栈，可能的出栈序列种数是？',
   '[{"key":"A","text":"卡特兰数 C(2n,n)/(n+1)"},{"key":"B","text":"n!"},{"key":"C","text":"2^n"},{"key":"D","text":"C(n,2)"}]',
   '["A"]', '经典出栈序列计数为卡特兰数。', 5, 4.0, 1, 2);

-- 补充题（未入卷，用于分类/难度筛选演示）
INSERT INTO `qz_question`
  (`id`, `category_id`, `q_type`, `content`, `options`, `answer`, `analysis`, `difficulty`, `score`, `status`, `creator_id`) VALUES
  (37, 6, 1, '对长度为 n 的有序顺序表做折半查找，最坏比较次数约为？',
   '[{"key":"A","text":"log2 n"},{"key":"B","text":"n"},{"key":"C","text":"n/2"},{"key":"D","text":"1"}]',
   '["A"]', '折半查找每次将区间减半。', 2, 4.0, 1, 2),
  (38, 5, 2, '关于最小生成树，下列说法正确的有？',
   '[{"key":"A","text":"Prim 算法从顶点出发逐步长大"},{"key":"B","text":"Kruskal 算法按边权从小到大选边"},{"key":"C","text":"任意连通网的最小生成树唯一"},{"key":"D","text":"最小生成树的边数等于顶点数减一"}]',
   '["A","B","D"]', '边权相等时最小生成树可能有多棵。', 4, 6.0, 1, 2),
  (39, 5, 3, '图的广度优先搜索用栈作为辅助数据结构。', NULL, '["F"]',
   'BFS 用队列，DFS 才用栈。', 2, 4.0, 1, 2),
  (40, 7, 1, 'TCP 连接主动关闭方最后等待 2MSL 后进入的状态是？',
   '[{"key":"A","text":"CLOSE_WAIT"},{"key":"B","text":"TIME_WAIT"},{"key":"C","text":"LAST_ACK"},{"key":"D","text":"FIN_WAIT_2"}]',
   '["B"]', 'TIME_WAIT 保证迟到报文消散后再复用连接。', 4, 4.0, 1, 3);

-- ------------------------------------------------------------ 试卷
INSERT INTO `ex_paper`
  (`id`, `title`, `description`, `category_id`, `pass_score`, `suggest_minutes`, `build_type`, `status`, `creator_id`) VALUES
  (1, '数据结构期末模拟卷', '覆盖线性表、树、图、查找与排序，17 题', 1, 60, 60, 1, 0, 2),
  (2, '计算机网络单元测试卷', '传输层、网络层与应用层基础，13 题', 2, 30, 30, 2, 0, 3);

-- 人工组卷：把题库内容快照进试卷（分值沿用题目默认分）
INSERT INTO `ex_paper_question`
  (`paper_id`, `question_id`, `q_type`, `content`, `options`, `answer`, `analysis`, `difficulty`, `score`, `sort_no`)
SELECT 1, `id`, `q_type`, `content`, `options`, `answer`, `analysis`, `difficulty`, `score`, `id`
FROM `qz_question`
WHERE `category_id` IN (1, 3, 4, 5, 6) AND `deleted` = 0 AND `status` = 1 AND `id` NOT IN (34, 35, 36)
ORDER BY `q_type`, `id`;

INSERT INTO `ex_paper_question`
  (`paper_id`, `question_id`, `q_type`, `content`, `options`, `answer`, `analysis`, `difficulty`, `score`, `sort_no`)
SELECT 2, `id`, `q_type`, `content`, `options`, `answer`, `analysis`, `difficulty`, `score`, `id`
FROM `qz_question`
WHERE `category_id` IN (2, 7, 8, 9) AND `deleted` = 0 AND `status` = 1
ORDER BY `q_type`, `id`;

-- 汇总分值与题量、题目引用次数；及格线按总分 60% 计算
UPDATE `ex_paper` p
SET `total_score` = (SELECT IFNULL(SUM(`score`), 0) FROM `ex_paper_question` q WHERE q.`paper_id` = p.`id`),
    `question_count` = (SELECT COUNT(*) FROM `ex_paper_question` q WHERE q.`paper_id` = p.`id`),
    `pass_score` = ROUND((SELECT IFNULL(SUM(`score`), 0) FROM `ex_paper_question` q
                          WHERE q.`paper_id` = p.`id`) * 0.6, 1),
    `status` = 1;

UPDATE `qz_question` q
SET q.`use_count` = (SELECT COUNT(*) FROM `ex_paper_question` pq WHERE pq.`question_id` = q.`id`);

-- ------------------------------------------------------------ 考试场次
-- 1 进行中：计算机2101，答题 60 分钟，切屏 3 次强制交卷
INSERT INTO `ex_exam`
  (`id`, `paper_id`, `title`, `description`, `start_time`, `end_time`, `duration_minutes`, `late_minutes`,
   `max_attempts`, `audience_type`, `switch_limit`, `shuffle_question`, `shuffle_option`,
   `score_published`, `status`, `creator_id`)
VALUES
  (1, 1, '数据结构期末考试', '闭卷，限时 60 分钟，切屏 3 次自动交卷',
   NOW() - INTERVAL 20 MINUTE, NOW() + INTERVAL 4 HOUR, 60, 30, 1, 2, 3, 0, 0, 0, 1, 2),
  (2, 2, '计算机网络单元测', '限时 30 分钟，明日下午开考',
   NOW() + INTERVAL 1 DAY, NOW() + INTERVAL 1 DAY + INTERVAL 90 MINUTE, 30, 15, 1, 2, 0, 0, 0, 0, 1, 3),
  (3, 2, '计算机网络补测（已结束）', '用于演示已结束状态',
   NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY + INTERVAL 40 MINUTE, 30, 10, 1, 1, 0, 0, 0, 1, 1, 3);

INSERT INTO `ex_exam_class` (`exam_id`, `class_name`) VALUES
  (1, '计算机2101'),
  (2, '计算机2102');
