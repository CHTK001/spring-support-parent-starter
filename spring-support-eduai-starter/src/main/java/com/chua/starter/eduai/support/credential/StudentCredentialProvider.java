// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.eduai.support.credential;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
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
 * 学生端凭据校验：学号或家长手机号 + 口令，换发只带学生角色的令牌。
 *
 * <p>它是 oauth {@link CredentialProvider} 扩展点的一个实现，注册名为 {@code student}。
 * 这样做的意义是：学生登录完全走 oauth 的登录链路（限流、令牌、鉴权、登出一视同仁），
 * 而 oauth 模块本身不需要知道「学生」这张表的存在。</p>
 *
 * <p><b>令牌里的 userId 就是学号</b>，与 {@code edu_student.student_account} 对齐。
 * 学生端其它接口按 {@code student_account = 登录态 userId} 定位档案，
 * 因此这里无需额外映射，也不必为每个学生造一个系统用户。</p>
 *
 * <p><b>角色只给 STUDENT</b>：不含 ADMIN/SUPER_ADMIN，管理员接口一律拒绝；
 * 也不含任何 permission，避免误挂到按权限名声明的注解上。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class StudentCredentialProvider implements CredentialProvider {

    /**
     * 在读状态
     */
    private static final int STATUS_ENROLLED = 1;

    private final EduStudentMapper studentMapper;

    @Override
    public LoginAuthResult authenticate(CredentialRequest request) {
        var account = request.account();
        if (StringUtils.isBlank(account)) {
            return denied(403, "请输入学号或家长手机号");
        }
        var secret = request.secret();
        if (StringUtils.isBlank(secret)) {
            return denied(403, "请输入登录密码");
        }
        var trimmed = account.trim();

        var candidates = findByStudentNo(trimmed);
        if (candidates.isEmpty()) {
            // 学号没命中才按家长手机号反查：一个号码可能带多个孩子
            candidates = findByParentPhone(trimmed);
        }
        if (candidates.isEmpty()) {
            return denied(403, "学号或家长手机号不存在");
        }
        if (candidates.size() > 1) {
            // 不猜：同一个号码下有多个孩子时，任选其一都是错的学生
            return denied(403, "该手机号绑定了多个学生，请改用学号登录");
        }

        var student = candidates.get(0);
        if (student.getStudentStatus() == null || student.getStudentStatus() != STATUS_ENROLLED) {
            return denied(403, "该学生已离校，无法登录");
        }
        if (StringUtils.isBlank(student.getStudentPassword())) {
            return denied(403, "尚未设置登录密码，请联系班主任或管理员设置");
        }
        if (!StudentPasswordSupport.matches(secret, student.getStudentPwdSalt(), student.getStudentPassword())) {
            log.warn("学生登录口令不匹配 - 学号: {}", student.getStudentNo());
            return denied(403, "密码错误");
        }
        return issue(student, request);
    }

    /**
     * 按学号查在读学生。
     *
     * <p>不过滤状态：离校学生要给出「已离校」的准确提示，
     * 而不是含混的「不存在」。</p>
     *
     * @param studentNo 学号
     * @return 命中的学生档案；无命中返回空列表
     */
    private List<EduStudent> findByStudentNo(String studentNo) {
        return studentMapper.selectList(new QueryWrapper<EduStudent>()
                .eq("student_no", studentNo)
                .last("LIMIT 2"));
    }

    /**
     * 按家长手机号反查学生。
     *
     * <p>同样不过滤状态，理由见 {@link #findByStudentNo(String)}。
     * 取 2 条足以判定「是否重号」，无需把全部同号学生读出来。</p>
     *
     * @param parentPhone 家长手机号
     * @return 命中的学生档案；无命中返回空列表
     */
    private List<EduStudent> findByParentPhone(String parentPhone) {
        return studentMapper.selectList(new QueryWrapper<EduStudent>()
                .eq("student_parent_phone", parentPhone)
                .last("LIMIT 2"));
    }

    /**
     * 按学生档案装配身份并签发令牌。
     *
     * @param student 学生档案
     * @param request 登录请求上下文，用于取令牌有效期
     * @return 含令牌的登录结果
     */
    private LoginAuthResult issue(EduStudent student, CredentialRequest request) {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("studentId", student.getStudentId());
        ext.put("studentNo", student.getStudentNo());
        ext.put("studentName", student.getStudentName());
        ext.put("studentClassId", student.getStudentClassId());
        ext.put("studentGradeLevel", student.getStudentGradeLevel());
        ext.put("studentGrade", student.getStudentGrade());
        ext.put("accountType", "student");

        var userResult = new UserResult();
        // userId 用学号：与 student_account 对齐，学生端其它接口按它定位档案
        userResult.setUserId(student.getStudentNo());
        userResult.setUsername(student.getStudentNo());
        userResult.setNickName(StringUtils.defaultString(student.getStudentName(), student.getStudentNo()));
        userResult.setPhone(student.getStudentParentPhone());
        userResult.setLoginType("student");
        userResult.setRoles(Set.of(AuthConstant.STUDENT));
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
     * @param message 面向学生的提示
     * @return 拒绝结果
     */
    private LoginAuthResult denied(int code, String message) {
        var result = new LoginAuthResult();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}