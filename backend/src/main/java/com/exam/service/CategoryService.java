package com.exam.service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exam.common.BizException;
import com.exam.common.ErrorCode;
import com.exam.common.LoginUser;
import com.exam.dto.CategoryForm;
import com.exam.entity.QzCategory;
import com.exam.entity.QzQuestion;
import com.exam.mapper.QzCategoryMapper;
import com.exam.mapper.QzQuestionMapper;
import com.exam.vo.CategoryNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 题库分类树。qz_category 用 parent_id 存邻接表，树在内存里组装（课程项目数据量小，一次全量查询）。
 * 逻辑删除条件 deleted = 0 由 MyBatis-Plus 自动追加，所以查询里不用手写。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    /** 根节点的 parent_id */
    private static final long ROOT = 0L;

    private final QzCategoryMapper categoryMapper;
    private final QzQuestionMapper questionMapper;

    /**
     * 分类树 + 每个分类的题目数。同一层的节点按 sortNo、id 升序，子节点带着自己的子树。
     *
     * @param keyword 可空；非空时只保留名字命中的分类，同时保留它们的祖先以便挂到树上
     */
    public List<CategoryNode> listTree(String keyword) {
        List<QzCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<QzCategory>()
                .orderByAsc(QzCategory::getSortNo)
                .orderByAsc(QzCategory::getId));
        Map<Long, Integer> counts = questionCountByCategory();
        Map<Long, CategoryNode> nodeMap = new LinkedHashMap<>();
        for (QzCategory category : filterByKeyword(all, keyword)) {
            nodeMap.put(category.getId(), toNode(category, counts.getOrDefault(category.getId(), 0)));
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode node : nodeMap.values()) {
            CategoryNode parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            // parent_id = 0 是根；父节点被 keyword 过滤掉时，子节点也提为根，避免整棵树看不见
            if (parent != null && !parent.getId().equals(node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CategoryForm form) {
        long parentId = form.getParentId() == null ? ROOT : form.getParentId();
        if (parentId != ROOT && categoryMapper.selectById(parentId) == null) {
            throw BizException.param("父分类不存在");
        }
        String name = requireName(form.getName());
        checkSiblingName(parentId, name, null);

        QzCategory entity = new QzCategory();
        entity.setParentId(parentId);
        entity.setName(name);
        entity.setSortNo(form.getSortNo() == null ? 0 : form.getSortNo());
        entity.setRemark(form.getRemark());
        entity.setCreatorId(LoginUser.userId());
        categoryMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CategoryForm form) {
        if (form.getId() == null) {
            throw BizException.param("分类 id 不能为空");
        }
        QzCategory current = categoryMapper.selectById(form.getId());
        if (current == null) {
            throw BizException.notFound("分类");
        }
        long parentId = form.getParentId() == null
                ? (current.getParentId() == null ? ROOT : current.getParentId())
                : form.getParentId();
        checkNotDescendant(current.getId(), parentId);
        if (parentId != ROOT && categoryMapper.selectById(parentId) == null) {
            throw BizException.param("父分类不存在");
        }
        String name = requireName(form.getName());
        checkSiblingName(parentId, name, current.getId());

        int sortNo = form.getSortNo() != null ? form.getSortNo()
                : (current.getSortNo() == null ? 0 : current.getSortNo());
        // 逐列 set，这样把备注清空时能真的写回 NULL（updateById 的 NOT_NULL 策略会跳过 null 列）
        categoryMapper.update(null, new LambdaUpdateWrapper<QzCategory>()
                .eq(QzCategory::getId, current.getId())
                .set(QzCategory::getParentId, parentId)
                .set(QzCategory::getName, name)
                .set(QzCategory::getSortNo, sortNo)
                .set(QzCategory::getRemark, form.getRemark()));
    }

    /** 有子分类或有题目都不允许删，避免题目变成孤儿数据 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw BizException.param("分类 id 不能为空");
        }
        if (categoryMapper.selectById(id) == null) {
            throw BizException.notFound("分类");
        }
        long children = categoryMapper.selectCount(new LambdaQueryWrapper<QzCategory>()
                .eq(QzCategory::getParentId, id));
        if (children > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_QUESTION, "该分类下还有子分类，不能删除");
        }
        long questions = questionMapper.selectCount(new LambdaQueryWrapper<QzQuestion>()
                .eq(QzQuestion::getCategoryId, id));
        if (questions > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_QUESTION, "该分类下还有 " + questions + " 道题目，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    /** selectMaps 的 key 就是列名/别名；这里一次 group by 拿到全部分类的题目数，不走 N+1 */
    private Map<Long, Integer> questionCountByCategory() {
        Map<Long, Integer> counts = new HashMap<>();
        List<Map<String, Object>> rows = questionMapper.selectMaps(new QueryWrapper<QzQuestion>()
                .select("category_id", "COUNT(*) AS cnt")
                .groupBy("category_id"));
        for (Map<String, Object> row : rows) {
            // key 就是 SQL 里的列名/别名；顺手兼容驼峰写法，避免不同 MyBatis 配置下取不到值
            Object categoryId = row.getOrDefault("category_id", row.get("categoryId"));
            Object count = row.getOrDefault("cnt", row.get("CNT"));
            if (categoryId instanceof Number id && count instanceof Number cnt) {
                counts.put(id.longValue(), cnt.intValue());
            }
        }
        return counts;
    }

    private List<QzCategory> filterByKeyword(List<QzCategory> all, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return all;
        }
        String text = keyword.trim();
        Map<Long, QzCategory> byId = new HashMap<>();
        for (QzCategory category : all) {
            byId.put(category.getId(), category);
        }
        Set<Long> keep = new HashSet<>();
        for (QzCategory category : all) {
            if (category.getName() == null || !category.getName().contains(text)) {
                continue;
            }
            Long cursor = category.getId();
            Set<Long> guard = new HashSet<>();
            while (cursor != null && cursor != ROOT && guard.add(cursor)) {
                keep.add(cursor);
                QzCategory up = byId.get(cursor);
                cursor = up == null ? null : up.getParentId();
            }
        }
        List<QzCategory> matched = new ArrayList<>();
        for (QzCategory category : all) {
            if (keep.contains(category.getId())) {
                matched.add(category);
            }
        }
        return matched;
    }

    /** 新增/改父级时禁止挂到自己或自己的子孙下面，否则树会成环 */
    private void checkNotDescendant(Long id, long parentId) {
        if (parentId == ROOT) {
            return;
        }
        if (parentId == id) {
            throw BizException.param("不能把分类挂到自己下面");
        }
        Map<Long, Long> parentOf = new HashMap<>();
        for (QzCategory category : categoryMapper.selectList(new LambdaQueryWrapper<QzCategory>())) {
            parentOf.put(category.getId(), category.getParentId() == null ? ROOT : category.getParentId());
        }
        Long cursor = parentId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && cursor != ROOT && visited.add(cursor)) {
            if (cursor.equals(id)) {
                throw BizException.param("不能把分类移动到它的子分类下，会形成环");
            }
            cursor = parentOf.get(cursor);
        }
    }

    /** 同名只限同一父节点下，不同章节重名是允许的 */
    private void checkSiblingName(long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<QzCategory> wrapper = new LambdaQueryWrapper<QzCategory>()
                .eq(QzCategory::getParentId, parentId)
                .eq(QzCategory::getName, name);
        if (excludeId != null) {
            wrapper.ne(QzCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw BizException.param("同级下已存在名为「" + name + "」的分类");
        }
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw BizException.param("分类名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 50) {
            throw BizException.param("分类名称不能超过 50 个字符");
        }
        return trimmed;
    }

    private CategoryNode toNode(QzCategory category, int questionCount) {
        CategoryNode node = new CategoryNode();
        node.setId(category.getId());
        node.setParentId(category.getParentId() == null ? ROOT : category.getParentId());
        node.setName(category.getName());
        node.setSortNo(category.getSortNo());
        node.setRemark(category.getRemark());
        node.setQuestionCount(questionCount);
        return node;
    }

    private void sortTree(List<CategoryNode> nodes) {
        nodes.sort(Comparator
                .comparingInt((CategoryNode node) -> node.getSortNo() == null ? 0 : node.getSortNo())
                .thenComparingLong(node -> node.getId() == null ? 0L : node.getId()));
        for (CategoryNode node : nodes) {
            sortTree(node.getChildren());
        }
    }
}
