package com.exam.vo;

import com.exam.entity.ExExam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 考试场次列表项/详情：考试字段 + 试卷信息 + 由时间和答卷推导出的统计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExamVo extends ExExam {

    private String paperTitle;

    /** 试卷总分 */
    private BigDecimal paperTotalScore;

    /** audienceType=2 时的可见班级 */
    private List<String> classNames;

    /** 1未开始 2进行中 3已结束，由当前时间与 start_time / end_time 推导 */
    private Integer state;

    /** 应交人数 */
    private Long expectedCount;

    /** 已交人数 */
    private Long submittedCount;

    /** 平均分，仅成绩已发布时非 null */
    private Double avgScore;
}
