package com.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 阅卷提交：一份答卷的若干道主观题得分与评语。
 */
@Data
public class GradingForm {

    @NotNull(message = "缺少答卷 id")
    private Long recordId;

    private List<Item> items;

    /** true 表示本次提交后完成阅卷并合分发布状态 */
    private Boolean finish = Boolean.TRUE;

    @Data
    public static class Item {
        @NotNull(message = "缺少答题明细 id")
        private Long answerItemId;
        @NotNull(message = "请填写得分")
        private BigDecimal score;
        private String comment;
    }
}
