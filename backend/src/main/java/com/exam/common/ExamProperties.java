package com.exam.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "exam")
public class ExamProperties {

    private Jwt jwt = new Jwt();

    /** 管理员重置密码时使用的初始密码 */
    private String defaultPassword = "123456";

    /** 多选题漏选且无错选是否得半分 */
    private boolean multipleHalfScore = true;

    @Data
    public static class Jwt {
        private String secret = "exam-system-default-secret-key-change-me-32bytes";
        private long expireHours = 12;
    }
}
