package com.exam.service;

import com.exam.common.PageQuery;

import com.exam.common.LikeUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BatchLimit;
import com.exam.common.BizException;
import com.exam.common.Dicts;
import com.exam.common.ErrorCode;
import com.exam.common.ExamProperties;
import com.exam.common.LoginUser;
import com.exam.common.PageResult;
import com.exam.dto.UserForm;
import com.exam.entity.SysRole;
import com.exam.entity.SysUser;
import com.exam.entity.SysUserRole;
import com.exam.mapper.SysRoleMapper;
import com.exam.mapper.SysUserMapper;
import com.exam.mapper.SysUserRoleMapper;
import com.exam.vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final ExamProperties properties;

    public static String encode(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matches(String raw, String encoded) {
        return encoded != null && ENCODER.matches(raw, encoded);
    }

    public SysUser requireById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户");
        }
        return user;
    }

    public SysUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
    }

    public PageResult<UserVo> page(long page, long size, String keyword, String roleCode, String className, Integer status) {
        List<Long> userIdsOfRole = null;
        if (roleCode != null && !roleCode.isBlank()) {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, roleCode));
            if (role == null) {
                throw BizException.param("角色不存在：" + roleCode);
            }
            userIdsOfRole = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getRoleId, role.getId()))
                    .stream().map(SysUserRole::getUserId).toList();
            if (userIdsOfRole.isEmpty()) {
                return PageResult.of(List.of(), 0, page, size);
            }
        }
        final List<Long> filterIds = userIdsOfRole;
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUsername, LikeUtil.escape(keyword)).or()
                        .like(SysUser::getRealName, LikeUtil.escape(keyword)).or()
                        .like(SysUser::getPhone, LikeUtil.escape(keyword)))
                .eq(className != null && !className.isBlank(), SysUser::getClassName, className)
                .eq(status != null, SysUser::getStatus, status)
                .in(filterIds != null, SysUser::getId, filterIds == null ? List.of(-1L) : filterIds)
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(PageQuery.of(page, size), wrapper);
        List<UserVo> vos = toVoList(result.getRecords());
        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public List<UserVo> toVoList(List<SysUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> ids = users.stream().map(SysUser::getId).toList();
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, ids));
        Map<Long, SysRole> roleById = links.isEmpty() ? Map.of()
                : roleMapper.selectBatchIds(links.stream().map(SysUserRole::getRoleId).distinct().toList())
                .stream().collect(Collectors.toMap(SysRole::getId, Function.identity()));
        Map<Long, List<SysRole>> rolesByUser = links.stream()
                .filter(l -> roleById.containsKey(l.getRoleId()))
                .collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(l -> roleById.get(l.getRoleId()), Collectors.toList())));

        List<UserVo> list = new ArrayList<>();
        // 联系方式只有管理员需要（导出、通知）；教师按班级和姓名工作，不批量拿到学生手机号/邮箱
        boolean canSeeContact = LoginUser.hasRole(Dicts.Role.ADMIN);
        for (SysUser user : users) {
            UserVo vo = new UserVo();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            vo.setPhone(canSeeContact ? user.getPhone() : maskPhone(user.getPhone()));
            vo.setEmail(canSeeContact ? user.getEmail() : maskEmail(user.getEmail()));
            vo.setClassName(user.getClassName());
            vo.setAvatar(user.getAvatar());
            vo.setStatus(user.getStatus());
            vo.setLastLoginTime(user.getLastLoginTime());
            vo.setCreateTime(user.getCreateTime());
            vo.setRoles(rolesByUser.getOrDefault(user.getId(), List.of()).stream()
                    .map(SysRole::getCode).toList());
            vo.setRoleNames(rolesByUser.getOrDefault(user.getId(), List.of()).stream()
                    .map(SysRole::getName).toList());
            list.add(vo);
        }
        return list;
    }

    public UserVo detail(Long id) {
        return toVoList(List.of(requireById(id))).get(0);
    }

    public List<String> roleCodes(Long userId) {
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(links.stream().map(SysUserRole::getRoleId).toList())
                .stream().map(SysRole::getCode).toList();
    }

    @Transactional
    public Long create(UserForm form) {
        if (form.getUsername() == null || form.getUsername().isBlank()) {
            throw BizException.param("请填写登录账号（学号/工号）");
        }
        if (findByUsername(form.getUsername()) != null) {
            throw BizException.of(ErrorCode.USERNAME_EXISTS, "账号 " + form.getUsername() + " 已存在");
        }
        List<String> roles = normalizeRoles(form.getRoles());
        SysUser user = new SysUser();
        user.setUsername(form.getUsername().trim());
        user.setRealName(form.getRealName());
        user.setPhone(form.getPhone());
        user.setEmail(form.getEmail());
        user.setClassName(form.getClassName());
        user.setStatus(form.getStatus() == null ? Dicts.Status.ENABLED : form.getStatus());
        String raw = form.getPassword() == null || form.getPassword().isBlank()
                ? properties.getDefaultPassword() : form.getPassword();
        user.setPassword(encode(raw));
        userMapper.insert(user);
        saveRoles(user.getId(), roles);
        return user.getId();
    }

    @Transactional
    public void update(UserForm form) {
        SysUser exists = requireById(form.getId());
        // 用 UpdateWrapper 逐列 set：updateById 会跳过 null，导致「清空手机号/改班级」保存后没变化
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, exists.getId())
                .set(SysUser::getRealName, form.getRealName())
                .set(SysUser::getPhone, emptyToNull(form.getPhone()))
                .set(SysUser::getEmail, emptyToNull(form.getEmail()))
                .set(SysUser::getClassName, emptyToNull(form.getClassName()))
                .set(form.getStatus() != null, SysUser::getStatus, form.getStatus()));
        if (form.getRoles() != null && !form.getRoles().isEmpty()) {
            saveRoles(exists.getId(), normalizeRoles(form.getRoles()));
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "";
        }
        return phone.substring(0, 3) + "****";
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    @Transactional
    public void assignRoles(Long userId, List<String> roles) {
        requireById(userId);
        saveRoles(userId, normalizeRoles(roles));
    }

    @Transactional
    public void changeStatus(Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.param("状态只能是 0(禁用) 或 1(正常)");
        }
        requireById(userId);
        if (userId.equals(LoginUser.userId()) && status == 0) {
            throw BizException.param("不能禁用当前登录账号");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);
    }

    @Transactional
    public String resetPassword(Long userId) {
        requireById(userId);
        String raw = properties.getDefaultPassword();
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(encode(raw));
        userMapper.updateById(update);
        return raw;
    }

    @Transactional
    public void delete(Long userId) {
        requireById(userId);
        if (userId.equals(LoginUser.userId())) {
            throw BizException.param("不能删除当前登录账号");
        }
        if (roleCodes(userId).contains(Dicts.Role.ADMIN) && countByRole(Dicts.Role.ADMIN) <= 1) {
            throw BizException.param("至少要保留一个管理员账号");
        }
        // deleted 置为主键而不是 1：uk_username(username, deleted) 才不会被两个同名已删账号撞掉
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .setSql("deleted = id")
                .eq(SysUser::getId, userId));
    }

    public long countByRole(String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, roleCode));
        if (role == null) {
            return 0;
        }
        return userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, role.getId()));
    }

    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    public List<String> listClassNames() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getClassName)
                        .isNotNull(SysUser::getClassName)
                        .ne(SysUser::getClassName, ""))
                .stream().map(SysUser::getClassName).distinct().sorted().toList();
    }

    /** 按班级取启用中的学生，班级集合为空表示不限班级 */
    public List<SysUser> listStudents(Collection<String> classNames) {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, Dicts.Status.ENABLED)
                .in(classNames != null && !classNames.isEmpty(), SysUser::getClassName,
                        classNames == null ? List.of() : classNames));
    }

    public boolean isStudent(Long userId) {
        return roleCodes(userId).contains(Dicts.Role.STUDENT);
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(Dicts.Role.STUDENT);
        }
        BatchLimit.check(roles, "角色");
        List<String> distinct = roles.stream().filter(r -> r != null && !r.isBlank())
                .map(String::trim).distinct().toList();
        if (distinct.isEmpty()) {
            return List.of(Dicts.Role.STUDENT);
        }
        return distinct;
    }

    private void saveRoles(Long userId, List<String> roleCodes) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getCode, roleCodes));
        if (roles.size() != roleCodes.size()) {
            throw BizException.param("存在未知角色编码");
        }
        for (SysRole role : roles) {
            SysUserRole link = new SysUserRole();
            link.setUserId(userId);
            link.setRoleId(role.getId());
            userRoleMapper.insert(link);
        }
    }
}
