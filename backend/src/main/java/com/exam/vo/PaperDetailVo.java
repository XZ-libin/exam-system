package com.exam.vo;

import com.exam.entity.ExPaper;
import com.exam.entity.ExPaperQuestion;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 试卷详情（教师端预览）：题目快照按 sort_no 排好序返回，answer 字段保留，判分与学生端脱敏由其他模块处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaperDetailVo extends ExPaper {

    private List<ExPaperQuestion> questions;
}
