package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.PageResult;
import com.exam.entity.SysOpLog;
import com.exam.mapper.SysOpLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final SysOpLogMapper opLogMapper;

    /** 记录一条操作日志。写日志失败不能影响主流程。 */
    public void record(String action, String target, String detail) {
        try {
            SysOpLog entity = new SysOpLog();
            entity.setAction(action);
            entity.setTarget(target);
            entity.setDetail(detail == null ? null : detail.substring(0, Math.min(detail.length(), 480)));
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Real-IP");
                entity.setClientIp(ip == null || ip.isBlank() ? request.getRemoteAddr() : ip);
            }
            var user = com.exam.common.LoginUser.get();
            if (user != null) {
                entity.setUserId(user.getUserId());
                entity.setUsername(user.getUsername());
            }
            opLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写操作日志失败: {}", e.getMessage());
        }
    }

    public PageResult<SysOpLog> page(long page, long size, String action, String keyword) {
        LambdaQueryWrapper<SysOpLog> wrapper = new LambdaQueryWrapper<SysOpLog>()
                .eq(action != null && !action.isBlank(), SysOpLog::getAction, action)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysOpLog::getUsername, keyword).or()
                        .like(SysOpLog::getTarget, keyword))
                .orderByDesc(SysOpLog::getId);
        return PageResult.of(opLogMapper.selectPage(new Page<>(page, size), wrapper));
    }
}
