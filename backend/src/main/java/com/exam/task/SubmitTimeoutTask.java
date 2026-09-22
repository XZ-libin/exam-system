package com.exam.task;

import com.exam.service.AttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 到点强制交卷。计时以数据库里的 deadline_time 为准，前端倒计时只用于展示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitTimeoutTask {

    private final AttemptService attemptService;

    @Scheduled(fixedDelay = 20000, initialDelay = 15000)
    public void submitOverdue() {
        int count = attemptService.submitTimedOut();
        if (count > 0) {
            log.info("超时强制交卷 {} 份答卷", count);
        }
    }
}
