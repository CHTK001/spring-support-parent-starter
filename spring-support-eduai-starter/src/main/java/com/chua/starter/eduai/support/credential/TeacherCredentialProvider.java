// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.eduai.support.credential;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.eduai.dimension.entity.EduTeacher;
import com.chua.starter.eduai.dimension.mapper.EduTeacherMapper;
import com.chua.starter.eduai.support.util.StudentPasswordSupport;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.oauth.client.support.credential.CredentialIssuer;
import com.chua.starter.oauth.client.support.credential.CredentialProvider;
import com.chua.starter.oauth.client.support.credential.CredentialRequest;
import com.chua.starter.oauth.client.support.credential.StaticCredentialConfig;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教师端凭据校验：工号 + 口令，换发带教师角色的令牌。
 *
 * <p>它是 oauth {@link CredentialProvider} 扩展点的一个实现，注册名为 {@code teacher}
 * （见 {@link EduCredentialConfiguration}），与 {@link StudentCredentialProvider} 同形。
 * 教师登录因此完全走 oauth 的登录链路（限流、令牌、鉴权、登出一视同仁），
 * 而 oauth 模块不需要知道「教师」这张表的存在。</p>
 *
 * <p><b>为什么需要它</b>：此前老师端必须先用系统账号（sys_user）登录、再在老师端绑定工号。
 * 工号本身就是教师的身份标识，多造一层系统账号既增加管理成本，也让「工号」与「登录名」
 * 各说各话。这里让工号直接成为登录名，与「学生用学号登录」对齐。</p>
 *
 * <p><b>令牌里的 userId 取「已绑定的登录账号」，未绑定则取工号并顺手绑定</b>：
 * 教师名下数据（试卷、阅卷任务、布置记录等）的归属是写入时记录的登录账号，
 * 若令牌改用工号，历史数据会因为归属不一致而对本人不可见。因此：
 * <ul>
 *     <li>{@code teacher_account} 已有值 → 沿用该账号作为 userId，归属与历史数据连续；</li>
 *     <li>{@code teacher_account} 为空 → 取工号，并把 {@code teacher_account} 置为工号，
 *     让首次用工号登录的教师从此有稳定身份。</li>
 * </ul>
 * 老师端其它接口按 {@code teacher_account = 登录态 userId} 定位档案
 * （{@code EduTeacherCenterServiceImpl#findMyTeacher}），两种情形都能命中。</p>
 *
 * <p><b>角色只给 TEACHER</b>：不含 ADMIN / SUPER_ADMIN，管理端接口一律拒绝；
 * 也不含任何 permission，避免误挂到按权限名声明的注解上。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class TeacherCredentialProvider implements CredentialProvider {

    /**
     * 在职状态
     */
    private static final int STATUS_ACTIVE = 1;

    private final EduTeacherMapper teacherMapper;

    @Override
    public LoginAuthResult authenticate(CredentialRequest request) {
        var account = request.account();
        if (StringUtils.isBlank(account)) {
            return denied(403, "请输入工号");
        }
        var secret = request.secret();
        if (StringUtils.isBlank(secret)) {
            return denied(403, "请输入登录口令");
        }
        var candidates = findByTeacherNo(account.trim());
        if (candidates.isEmpty()) {
            return denied(403, "工号不存在");
        }
        if (candidates.size() > 1) {
            // teacher_no 上有唯一约束，走到这里说明库被绕过约束写脏了：不猜是哪一位
            log.warn("[EduAi] 工号 {} 命中多条教师档案，拒绝登录", account.trim());
            return denied(403, "工号重复，请联系管理员处理");
        }

        var teacher = candidates.get(0);
        if (teacher.getTeacherStatus() == null || teacher.getTeacherStatus() != STATUS_ACTIVE) {
            return denied(403, "该教师已离职，无法登录");
        }
        if (StringUtils.isBlank(teacher.getTeacherPassword())) {
            return denied(403, "尚未设置登录口令，请联系管理员在「学生管理 → 教师」重置口令");
        }
        // 复用学生的口令算法（HMAC-SHA256 + 每师独立随机盐）。不为同一套算法再写一份实现：
        // 两份实现意味着两处要同时演进，而安全相关的实现分叉最容易留下弱的一侧。
        if (!StudentPasswordSupport.matches(secret, teacher.getTeacherPwdSalt(), teacher.getTeacherPassword())) {
            log.warn("[EduAi] 教师登录口令不匹配 - 工号: {}", teacher.getTeacherNo());
            return denied(403, "口令错误");
        }
        return issue(teacher, request);
    }

    /**
     * 按工号查教师档案。
     *
     * <p>不过滤状态：离职教师要给「已离职」的准确提示，而不是含混的「工号不存在」。
     * 取 2 条足以判定是否重号，无需把同号档案全读出来。</p>
     *
     * @param teacherNo 工号
     * @return 命中的教师档案；无命中返回空列表
     */
    private List<EduTeacher> findByTeacherNo(String teacherNo) {
        return teacherMapper.selectList(new QueryWrapper<EduTeacher>()
                .eq("teacher_no", teacherNo)
                .last("LIMIT 2"));
    }

    /**
     * 按教师档案装配身份并签发令牌。
     *
     * @param teacher 教师档案
     * @param request 登录请求上下文，用于取令牌有效期
     * @return 含令牌的登录结果
     */
    private LoginAuthResult issue(EduTeacher teacher, CredentialRequest request) {
        String identity = StringUtils.trimToNull(teacher.getTeacherAccount());
        if (identity == null) {
            // 首次用工号登录：把工号写进绑定列，此后该教师有稳定身份
            identity = teacher.getTeacherNo();
            var bind = new EduTeacher();
            bind.setTeacherId(teacher.getTeacherId());
            bind.setTeacherAccount(identity);
            teacherMapper.updateById(bind);
            log.info("[EduAi] 教师 {} 首次用工号登录，已绑定登录账号 {}",
                    teacher.getTeacherNo(), identity);
        }

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("teacherId", teacher.getTeacherId());
        ext.put("teacherNo", teacher.getTeacherNo());
        ext.put("teacherName", teacher.getTeacherName());
        ext.put("teacherSubjectCode", teacher.getTeacherSubjectCode());
        ext.put("accountType", "teacher");

        var userResult = new UserResult();
        userResult.setUserId(identity);
        userResult.setUsername(teacher.getTeacherNo());
        userResult.setNickName(StringUtils.defaultString(teacher.getTeacherName(), teacher.getTeacherNo()));
        userResult.setLoginType("teacher");
        userResult.setRoles(Set.of(AuthConstant.TEACHER));
        userResult.setPermission(Collections.emptySet());
        userResult.setReadonly(false);
        userResult.setExt(ext);

        var ttl = request.tokenTtlSeconds() > 0
                ? request.tokenTtlSeconds()
                : StaticCredentialConfig.tokenTtlSeconds();
        return CredentialIssuer.issue(userResult, ttl);
    }

    /**
     * 构造拒绝结果。
     *
     * @param code    业务码
     * @param message 面向教师的提示
     * @return 拒绝结果
     */
    private LoginAuthResult denied(int code, String message) {
        var result = new LoginAuthResult();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}