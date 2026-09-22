package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

// 表：ex_exam_class
@Data
@TableName("ex_exam_class")
public class ExExamClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private String className;

    private LocalDateTime createTime;
}
