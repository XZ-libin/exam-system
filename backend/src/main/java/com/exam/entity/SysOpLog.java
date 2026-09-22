package com.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

// 表：sys_op_log
@Data
@TableName("sys_op_log")
public class SysOpLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String action;

    private String target;

    private String detail;

    private String clientIp;

    private LocalDateTime createTime;
}
