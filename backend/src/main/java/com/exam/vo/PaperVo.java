package com.exam.vo;

import com.exam.entity.ExPaper;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷列表项：试卷自身字段（含 total_score / question_count）+ 分类名、创建人、被考试引用数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaperVo extends ExPaper {

    /** 主科目名称 */
    private String categoryName;

    /** 创建人姓名 */
    private String creatorName;

    /** 被多少场考试引用 */
    private Long examCount;
}
