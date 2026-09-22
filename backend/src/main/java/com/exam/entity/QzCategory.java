package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

// 表：qz_category
@Data
@TableName("qz_category")
public class QzCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String name;

    private Integer sortNo;

    private String remark;

    private Long creatorId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}
