package com.exam.dto;

import lombok.Data;

import java.util.List;

/**
 * 答题相关的请求体。
 */
public final class AttemptForm {

    private AttemptForm() {
    }

    /** 批量保存答案，前端每答一题或每 30 秒调用一次 */
    @Data
    public static class Save {
        private List<Item> answers;
    }

    @Data
    public static class Item {
        private Long paperQuestionId;
        /** 单选/判断 1 个，多选 N 个，填空按空顺序 N 个，简答 1 个 */
        private List<String> answer;
    }
}
