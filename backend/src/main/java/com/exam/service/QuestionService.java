package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.ErrorCode;
import com.exam.common.JsonUtil;
import com.exam.common.LoginUser;
import com.exam.common.PageResult;
import com.exam.dto.QuestionForm;
import com.exam.entity.QzCategory;
import com.exam.entity.QzQuestion;
import com.exam.entity.SysUser;
import com.exam.mapper.QzCategoryMapper;
import com.exam.mapper.QzQuestionMapper;
import com.exam.mapper.SysUserMapper;
import com.exam.vo.QuestionVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 题库题目。options / answer 两列在库里是 JSON 字符串，写出走 JsonUtil，读回时
 * 答案是字符串数组（JsonUtil.readStringList），选项是对象数组（本地 ObjectMapper）。
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final ObjectMapper OPTIONS_MAPPER = new ObjectMapper();

    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("2.0");
    private static final int DEFAULT_DIFFICULTY = 3;
    private static final int MIN_DIFFICULTY = 1;
    private static final int MAX_DIFFICULTY = 5;
    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 6;
    private static final int BRIEF_LENGTH = 15;
    private static final String JUDGE_TRUE = "T";
    private static final String JUDGE_FALSE = "F";

    /** 2 个及以上连续下划线算一个空 */
    private static final Pattern BLANK_RUN = Pattern.compile("_{2,}");
    private static final Pattern TAG_LINE = Pattern.compile("^【\\s*([^】]+?)\\s*】\\s*(.*)$");
    private static final Pattern OPTION_LINE = Pattern.compile("^([A-Fa-f])\\s*[.、)）]\\s*(.+)$");
    private static final Pattern LABEL_LINE = Pattern.compile("^(答案|解析)\\s*[:：]\\s*(.*)$");

    private final QzQuestionMapper questionMapper;
    private final QzCategoryMapper categoryMapper;
    private final SysUserMapper userMapper;

    // ---------------------------------------------------------- 查询

    /**
     * 分页列表。选中某个分类时会自动带上它的全部子分类，因为题目允许挂在章节叶子上。
     */
    public PageResult<QuestionVo> page(long page, long size, Long categoryId, Integer qType,
                                       Integer difficulty, String keyword, Integer status, Long creatorId) {
        LambdaQueryWrapper<QzQuestion> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.in(QzQuestion::getCategoryId, categoryIdsWithDescendants(categoryId));
        }
        String text = keyword == null ? null : keyword.trim();
        wrapper.eq(qType != null, QzQuestion::getQType, qType)
                .eq(difficulty != null, QzQuestion::getDifficulty, difficulty)
                .eq(status != null, QzQuestion::getStatus, status)
                .eq(creatorId != null, QzQuestion::getCreatorId, creatorId)
                .like(text != null && !text.isEmpty(), QzQuestion::getContent, text)
                .orderByDesc(QzQuestion::getId);
        Page<QzQuestion> result = questionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(toVoList(result.getRecords()), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public QuestionVo detail(Long id) {
        return toVoList(List.of(load(id))).get(0);
    }

    // ---------------------------------------------------------- 写入

    @Transactional(rollbackFor = Exception.class)
    public Long create(QuestionForm form) {
        validate(form);
        QzQuestion entity = toEntity(form, LoginUser.userId());
        entity.setUseCount(0);
        questionMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(QuestionForm form) {
        if (form == null || form.getId() == null) {
            throw BizException.param("题目 id 不能为空");
        }
        QzQuestion current = load(form.getId());
        validate(form);
        QzQuestion entity = toEntity(form, current.getCreatorId());
        // 用 UpdateWrapper 逐列 set：改成判断/填空时 options 要真的写回 NULL，
        // updateById 的 NOT_NULL 策略会把 null 列跳过，留下脏选项；creator_id、use_count 不在列里，保持原值
        questionMapper.update(null, new LambdaUpdateWrapper<QzQuestion>()
                .eq(QzQuestion::getId, current.getId())
                .set(QzQuestion::getCategoryId, entity.getCategoryId())
                .set(QzQuestion::getQType, entity.getQType())
                .set(QzQuestion::getContent, entity.getContent())
                .set(QzQuestion::getOptions, entity.getOptions())
                .set(QzQuestion::getAnswer, entity.getAnswer())
                .set(QzQuestion::getAnalysis, entity.getAnalysis())
                .set(QzQuestion::getDifficulty, entity.getDifficulty())
                .set(QzQuestion::getScore, entity.getScore())
                .set(QzQuestion::getStatus, entity.getStatus()));
    }

    /** 被试卷引用过的题目禁止删除，否则已发布试卷的题目快照会和题库脱钩 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QzQuestion current = load(id);
        int useCount = current.getUseCount() == null ? 0 : current.getUseCount();
        if (useCount > 0) {
            throw new BizException(ErrorCode.QUESTION_USED_BY_PAPER, "该题已被 " + useCount + " 套试卷引用，不能删除");
        }
        questionMapper.deleteById(id);
    }

    /**
     * 批量新增：每条独立校验，坏数据只记一条 message，不影响其他条目入库。
     *
     * @return {saved, failed, messages}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchCreate(List<QuestionForm> forms) {
        List<QuestionForm> items = forms == null ? List.of() : forms;
        List<String> messages = new ArrayList<>();
        int saved = saveForms(items, sequenceLabels(items), messages);
        return result(items.size(), saved, messages);
    }

    /**
     * 文本导入，块之间空行分隔：
     * <pre>
     * 【单选】题干文本
     * A.选项一
     * B.选项二
     * 答案：A
     * 解析：可选文字
     * </pre>
     *
     * @param categoryId 导入目标分类，题目落不到无主的分类上，所以由接口传入
     * @return 与 batchCreate 相同的 {saved, failed, messages}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importFromText(String text, Long categoryId) {
        List<QuestionForm> items = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        int blocks = parseBlocks(text, categoryId, items, labels, messages);
        int saved = saveForms(items, labels, messages);
        return result(blocks, saved, messages);
    }

    /** 导入模板：与 importFromText 的解析格式严格一致 */
    public String importTemplate() {
        return """
                【单选】下列哪个属于 Java 的基本数据类型？
                A.String
                B.int
                C.Integer
                D.Object
                答案：B
                解析：int 是基本数据类型，其余都是引用类型。

                【多选】下列哪些是面向对象的核心特性？
                A.封装
                B.继承
                C.多态
                D.垃圾回收
                答案：A,B,C
                解析：封装、继承、多态是三大特性，垃圾回收属于运行时机制。

                【判断】MyBatis-Plus 的逻辑删除会把 deleteById 变成 UPDATE。
                答案：对
                解析：配置了 logic-delete-field 后，删除语句被改写为更新 deleted 字段。
                """;
    }

    /**
     * 试卷模块引用/取消引用题目时维护 use_count。
     * delta 是 int，直接拼进 setSql 没有注入风险；GREATEST 保证不会被减成负数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bumpUseCount(Collection<Long> ids, int delta) {
        if (ids == null || ids.isEmpty() || delta == 0) {
            return;
        }
        questionMapper.update(null, new LambdaUpdateWrapper<QzQuestion>()
                .setSql("use_count = GREATEST(use_count + (" + delta + "), 0)")
                .in(QzQuestion::getId, ids));
    }

    // ---------------------------------------------------------- 校验

    /**
     * 唯一的校验入口，create / update / batch / import 共用。
     * 顺带把 difficulty、score、status 的默认值填进 form，后面 toEntity 直接用。
     */
    private void validate(QuestionForm form) {
        if (form == null) {
            throw BizException.param("题目内容不能为空");
        }
        if (form.getQType() == null) {
            throw BizException.param("题型不能为空");
        }
        if (form.getContent() == null || form.getContent().isBlank()) {
            invalid(form, "题干不能为空");
        }
        if (form.getCategoryId() == null) {
            invalid(form, "所属分类不能为空");
        }
        if (categoryMapper.selectById(form.getCategoryId()) == null) {
            invalid(form, "所属分类不存在");
        }

        List<String> answers = trimList(form.getAnswer());
        int type = form.getQType();
        switch (type) {
            case Dicts.QType.SINGLE -> {
                List<String> keys = requireChoiceOptions(form);
                answers.replaceAll(String::toUpperCase);
                if (answers.size() != 1) {
                    invalid(form, "单选题答案只能有 1 个，当前 " + answers.size() + " 个");
                }
                if (!keys.contains(answers.get(0))) {
                    invalid(form, "答案「" + answers.get(0) + "」不是本题的选项编号");
                }
            }
            case Dicts.QType.MULTIPLE -> {
                List<String> keys = requireChoiceOptions(form);
                answers.replaceAll(String::toUpperCase);
                if (answers.size() < 2) {
                    invalid(form, "多选题答案至少 2 个，当前 " + answers.size() + " 个");
                }
                if (new HashSet<>(answers).size() != answers.size()) {
                    invalid(form, "多选题的答案有重复编号");
                }
                for (String answer : answers) {
                    if (!keys.contains(answer)) {
                        invalid(form, "答案「" + answer + "」不是本题的选项编号");
                    }
                }
            }
            case Dicts.QType.JUDGE -> {
                requireNoOptions(form, "判断题");
                if (answers.size() != 1) {
                    invalid(form, "判断题答案只能有 1 个（T 对 / F 错）");
                }
                String answer = answers.get(0).toUpperCase();
                if (!JUDGE_TRUE.equals(answer) && !JUDGE_FALSE.equals(answer)) {
                    invalid(form, "判断题答案只能是 T（对）或 F（错），当前是「" + answers.get(0) + "」");
                }
                answers = new ArrayList<>(List.of(answer));
            }
            case Dicts.QType.BLANK -> {
                requireNoOptions(form, "填空题");
                int blanks = countBlankRuns(form.getContent());
                if (blanks == 0) {
                    invalid(form, "题干里至少要有一个 ____ 空位");
                }
                if (answers.size() != blanks) {
                    invalid(form, "题干有 " + blanks + " 个空，答案要给 " + blanks + " 条，当前 " + answers.size() + " 条");
                }
                requireNoBlankAnswer(form, answers, "每条空的答案");
            }
            case Dicts.QType.ESSAY -> {
                requireNoOptions(form, "简答题");
                if (answers.size() != 1) {
                    invalid(form, "简答题只需要 1 条参考答案");
                }
                requireNoBlankAnswer(form, answers, "参考答案");
            }
            default -> throw BizException.param(
                    "题型「" + form.getQType() + "」不合法，只能是 1单选 2多选 3判断 4填空 5简答");
        }
        form.setAnswer(answers);

        if (form.getDifficulty() == null) {
            form.setDifficulty(DEFAULT_DIFFICULTY);
        } else if (form.getDifficulty() < MIN_DIFFICULTY || form.getDifficulty() > MAX_DIFFICULTY) {
            invalid(form, "难度只能是 " + MIN_DIFFICULTY + "-" + MAX_DIFFICULTY + "，当前 " + form.getDifficulty());
        }
        if (form.getScore() == null) {
            form.setScore(DEFAULT_SCORE);
        } else if (form.getScore().compareTo(BigDecimal.ZERO) <= 0) {
            invalid(form, "分值必须大于 0，当前 " + form.getScore().toPlainString());
        }
        if (form.getStatus() == null) {
            form.setStatus(Dicts.Status.ENABLED);
        } else if (form.getStatus() != Dicts.Status.DISABLED && form.getStatus() != Dicts.Status.ENABLED) {
            invalid(form, "状态只能是 0停用 或 1启用");
        }
    }

    /** 选择题选项：2-6 个，编号必须是从 A 开始的连续字母，内容不能为空 */
    private List<String> requireChoiceOptions(QuestionForm form) {
        List<QuestionForm.OptionItem> options = form.getOptions();
        int size = options == null ? 0 : options.size();
        if (size < MIN_OPTIONS || size > MAX_OPTIONS) {
            invalid(form, Dicts.QType.name(form.getQType()) + "需要 " + MIN_OPTIONS + "-" + MAX_OPTIONS
                    + " 个选项，当前 " + size + " 个");
        }
        List<String> keys = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            QuestionForm.OptionItem item = options.get(i);
            String expected = String.valueOf((char) ('A' + i));
            if (item == null) {
                invalid(form, "第 " + (i + 1) + " 个选项是空的");
            }
            String key = item.getKey() == null ? "" : item.getKey().trim().toUpperCase();
            if (key.isEmpty()) {
                invalid(form, "第 " + (i + 1) + " 个选项缺少编号，应为 " + expected);
            }
            if (!expected.equals(key)) {
                invalid(form, "选项编号要从 A 开始按顺序排列，第 " + (i + 1) + " 个应为 " + expected + "，实际是 " + key);
            }
            if (item.getText() == null || item.getText().isBlank()) {
                invalid(form, expected + " 选项的内容不能为空");
            }
            keys.add(key);
        }
        return keys;
    }

    private void requireNoOptions(QuestionForm form, String label) {
        if (form.getOptions() != null && !form.getOptions().isEmpty()) {
            invalid(form, label + "不需要选项，请把选项清空");
        }
    }

    private void requireNoBlankAnswer(QuestionForm form, List<String> answers, String label) {
        for (String answer : answers) {
            if (answer.isEmpty()) {
                invalid(form, label + "不能是空白");
            }
        }
    }

    private int countBlankRuns(String content) {
        Matcher matcher = BLANK_RUN.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private List<String> trimList(List<String> raw) {
        List<String> list = new ArrayList<>();
        if (raw == null) {
            return list;
        }
        for (String item : raw) {
            list.add(item == null ? "" : item.trim());
        }
        return list;
    }

    /** 校验失败统一走这里：报清楚是哪条规则、哪道题（取题干前 15 个字） */
    private void invalid(QuestionForm form, String reason) {
        throw BizException.param("题目「" + brief(form == null ? null : form.getContent()) + "」：" + reason);
    }

    private String brief(String content) {
        if (content == null || content.isBlank()) {
            return "未填题干";
        }
        String text = content.trim().replaceAll("\\s+", " ");
        return text.length() <= BRIEF_LENGTH ? text : text.substring(0, BRIEF_LENGTH) + "…";
    }

    // ---------------------------------------------------------- 实体 / VO 转换

    private QzQuestion toEntity(QuestionForm form, Long creatorId) {
        QzQuestion entity = new QzQuestion();
        entity.setCategoryId(form.getCategoryId());
        entity.setQType(form.getQType());
        entity.setContent(form.getContent().trim());
        entity.setOptions(writeOptions(form));
        entity.setAnswer(JsonUtil.write(form.getAnswer()));
        entity.setAnalysis(form.getAnalysis() == null || form.getAnalysis().isBlank()
                ? null : form.getAnalysis().trim());
        entity.setDifficulty(form.getDifficulty());
        entity.setScore(form.getScore());
        entity.setStatus(form.getStatus());
        entity.setCreatorId(creatorId);
        // use_count 留给调用方：新增时置 0，更新时保持 null 让 MyBatis-Plus 跳过该列
        return entity;
    }

    /** 判断、填空、简答的 options 列存 NULL，与 01_schema.sql 的注释一致 */
    private String writeOptions(QuestionForm form) {
        if (form.getOptions() == null || form.getOptions().isEmpty()) {
            return null;
        }
        List<QuestionForm.OptionItem> options = new ArrayList<>();
        for (QuestionForm.OptionItem item : form.getOptions()) {
            QuestionForm.OptionItem clean = new QuestionForm.OptionItem();
            clean.setKey(item.getKey().trim().toUpperCase());
            clean.setText(item.getText().trim());
            options.add(clean);
        }
        return JsonUtil.write(options);
    }

    private List<QuestionVo> toVoList(List<QzQuestion> rows) {
        Map<Long, String> categoryNames = categoryNames(collectIds(rows, QzQuestion::getCategoryId));
        Map<Long, String> creatorNames = creatorNames(collectIds(rows, QzQuestion::getCreatorId));
        List<QuestionVo> list = new ArrayList<>(rows.size());
        for (QzQuestion row : rows) {
            list.add(toVo(row, categoryNames, creatorNames));
        }
        return list;
    }

    private QuestionVo toVo(QzQuestion entity, Map<Long, String> categoryNames, Map<Long, String> creatorNames) {
        QuestionVo vo = new QuestionVo();
        vo.setId(entity.getId());
        vo.setCategoryId(entity.getCategoryId());
        vo.setQType(entity.getQType());
        vo.setQTypeName(Dicts.QType.name(entity.getQType()));
        vo.setContent(entity.getContent());
        vo.setOptions(parseOptions(entity.getOptions()));
        vo.setAnswer(JsonUtil.readStringList(entity.getAnswer()));
        vo.setAnalysis(entity.getAnalysis());
        vo.setDifficulty(entity.getDifficulty());
        vo.setScore(entity.getScore());
        vo.setStatus(entity.getStatus());
        vo.setCreatorId(entity.getCreatorId());
        vo.setUseCount(entity.getUseCount());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setCategoryName(entity.getCategoryId() == null ? null : categoryNames.get(entity.getCategoryId()));
        vo.setCreatorName(entity.getCreatorId() == null ? null : creatorNames.get(entity.getCreatorId()));
        return vo;
    }

    /** JsonUtil 只暴露字符串数组的读法，选项是对象数组，这里用一个本地 mapper 解析 */
    private List<QuestionForm.OptionItem> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return OPTIONS_MAPPER.readValue(json, new TypeReference<List<QuestionForm.OptionItem>>() {
            });
        } catch (Exception e) {
            throw BizException.param("该题的选项数据不是合法 JSON，请重新保存");
        }
    }

    /** 分类名、创建人名各查一次，避免列表每行再打一次库 */
    private Map<Long, String> categoryNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .filter(category -> category.getName() != null)
                .collect(Collectors.toMap(QzCategory::getId, QzCategory::getName, (a, b) -> a));
    }

    private Map<Long, String> creatorNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        user -> user.getRealName() == null || user.getRealName().isBlank()
                                ? user.getUsername() : user.getRealName(),
                        (a, b) -> a));
    }

    private Set<Long> collectIds(List<QzQuestion> rows, Function<QzQuestion, Long> getter) {
        return rows.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private QzQuestion load(Long id) {
        if (id == null) {
            throw BizException.param("题目 id 不能为空");
        }
        QzQuestion entity = questionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.QUESTION_NOT_FOUND, "题目不存在");
        }
        return entity;
    }

    /** 目标分类 + 它的全部子孙分类，一次全量查分类再 BFS，不在循环里查库 */
    private Set<Long> categoryIdsWithDescendants(Long categoryId) {
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (QzCategory category : categoryMapper.selectList(new LambdaQueryWrapper<QzCategory>())) {
            Long parentId = category.getParentId() == null ? 0L : category.getParentId();
            childrenOf.computeIfAbsent(parentId, key -> new ArrayList<>()).add(category.getId());
        }
        Set<Long> ids = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current != null && ids.add(current)) {
                queue.addAll(childrenOf.getOrDefault(current, List.of()));
            }
        }
        return ids;
    }

    // ---------------------------------------------------------- 批量 / 文本导入

    private int saveForms(List<QuestionForm> items, List<String> labels, List<String> messages) {
        if (items.isEmpty()) {
            return 0;
        }
        Long userId = LoginUser.userId();
        int saved = 0;
        for (int i = 0; i < items.size(); i++) {
            try {
                validate(items.get(i));
                QzQuestion entity = toEntity(items.get(i), userId);
                entity.setUseCount(0);
                questionMapper.insert(entity);
                saved++;
            } catch (BizException e) {
                messages.add(labels.get(i) + "：" + e.getMessage());
            } catch (Exception e) {
                messages.add(labels.get(i) + "：入库失败，请检查内容后重试");
            }
        }
        return saved;
    }

    private Map<String, Object> result(int total, int saved, List<String> messages) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("saved", saved);
        data.put("failed", total - saved);
        data.put("messages", messages);
        return data;
    }

    private List<String> sequenceLabels(List<QuestionForm> items) {
        List<String> labels = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            labels.add("第 " + (i + 1) + " 条");
        }
        return labels;
    }

    /**
     * @return 文本里实际的题块数量（含解析失败的），用于算 failed
     */
    private int parseBlocks(String text, Long categoryId, List<QuestionForm> items,
                            List<String> labels, List<String> messages) {
        List<String> blocks = splitBlocks(text);
        for (int i = 0; i < blocks.size(); i++) {
            String label = "第 " + (i + 1) + " 题";
            try {
                items.add(parseBlock(blocks.get(i), categoryId));
                labels.add(label);
            } catch (BizException e) {
                messages.add(label + "：" + e.getMessage());
            }
        }
        return blocks.size();
    }

    private List<String> splitBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return blocks;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.isEmpty() && normalized.charAt(0) == 0xFEFF) {
            // 记事本等编辑器保存的 UTF-8 会带 BOM
            normalized = normalized.substring(1);
        }
        StringBuilder current = new StringBuilder();
        for (String line : normalized.split("\n")) {
            if (line.isBlank()) {
                if (current.length() > 0) {
                    blocks.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(line).append('\n');
            }
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    private QuestionForm parseBlock(String block, Long categoryId) {
        List<String> lines = new ArrayList<>();
        for (String raw : block.split("\n")) {
            String line = raw.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                lines.add(line);
            }
        }
        Matcher tag = TAG_LINE.matcher(lines.isEmpty() ? "" : lines.get(0));
        if (!tag.matches()) {
            throw BizException.param("首行要写成【题型】题干，例如【单选】下列哪个…");
        }
        Integer qType = qTypeOfTag(tag.group(1));
        if (qType == null) {
            throw BizException.param("无法识别题型【" + tag.group(1) + "】，支持：单选 / 多选 / 判断 / 填空 / 简答");
        }
        StringBuilder content = new StringBuilder(tag.group(2).trim());
        List<QuestionForm.OptionItem> options = new ArrayList<>();
        String answerText = null;
        String analysis = null;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher option = OPTION_LINE.matcher(line);
            Matcher label = LABEL_LINE.matcher(line);
            if (option.matches()) {
                QuestionForm.OptionItem item = new QuestionForm.OptionItem();
                item.setKey(option.group(1).toUpperCase());
                item.setText(option.group(2).trim());
                options.add(item);
            } else if (label.matches() && "答案".equals(label.group(1))) {
                answerText = label.group(2).trim();
            } else if (label.matches()) {
                analysis = label.group(2).trim();
            } else {
                // 题干换行续写
                content.append('\n').append(line);
            }
        }
        if (content.isEmpty()) {
            throw BizException.param("题干不能为空");
        }
        if (answerText == null || answerText.isEmpty()) {
            throw BizException.param("缺少「答案：」这一行");
        }

        QuestionForm form = new QuestionForm();
        form.setCategoryId(categoryId);
        form.setQType(qType);
        form.setContent(content.toString());
        form.setOptions(options);
        form.setAnswer(splitImportAnswer(qType, answerText));
        form.setAnalysis(analysis);
        return form;
    }

    /** 多选按 , ，、 空格切；填空按 | 切；判断题把「对/错」翻成 T/F */
    private List<String> splitImportAnswer(int qType, String answerText) {
        String text = answerText.trim();
        switch (qType) {
            case Dicts.QType.MULTIPLE -> {
                List<String> list = new ArrayList<>();
                for (String part : text.split("[,，、;；\\s]+")) {
                    if (!part.isBlank()) {
                        list.add(part.trim().toUpperCase());
                    }
                }
                return list;
            }
            case Dicts.QType.JUDGE -> {
                return new ArrayList<>(List.of(judgeAnswer(text)));
            }
            case Dicts.QType.BLANK -> {
                List<String> list = new ArrayList<>();
                for (String part : text.split("[|｜]")) {
                    list.add(part.trim());
                }
                return list;
            }
            default -> {
                return new ArrayList<>(List.of(text.toUpperCase()));
            }
        }
    }

    private String judgeAnswer(String text) {
        String value = text.toUpperCase();
        if (value.startsWith(JUDGE_TRUE) || text.startsWith("对") || text.startsWith("正确") || text.startsWith("是")) {
            return JUDGE_TRUE;
        }
        if (value.startsWith(JUDGE_FALSE) || text.startsWith("错") || text.startsWith("不对") || text.startsWith("否")) {
            return JUDGE_FALSE;
        }
        return value;
    }

    private Integer qTypeOfTag(String tag) {
        return switch (tag == null ? "" : tag.trim()) {
            case "单选", "单选题", "选择", "选择题" -> Dicts.QType.SINGLE;
            case "多选", "多选题" -> Dicts.QType.MULTIPLE;
            case "判断", "判断题" -> Dicts.QType.JUDGE;
            case "填空", "填空题" -> Dicts.QType.BLANK;
            case "简答", "简答题", "问答", "问答题" -> Dicts.QType.ESSAY;
            default -> null;
        };
    }
}
