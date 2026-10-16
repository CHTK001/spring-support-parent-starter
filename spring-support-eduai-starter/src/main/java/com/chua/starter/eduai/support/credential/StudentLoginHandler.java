package com.chua.starter.eduai.support.credential;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.oauth.client.support.enums.AuthType;

/**
 * 学生登录处理器：让「学号（或家长手机号）+ 口令」能走通 {@code POST /v2/user/login}。
 *
 * <p>与 {@link TeacherLoginHandler} 同构。请求里带 {@code accountType=student} 时，登录口组合出的
 * 查找名是 {@code student}；本实现注册为大写的 {@code STUDENT}（SPI 大小写不敏感）。校验逻辑全部在
 * 基类，真正的账号反查与口令比对委托给 {@link StudentCredentialProvider}（注册名 {@code student}）。</p>
 *
 * <p><b>此前的表现</b>：没有任何名为 {@code student} 的 {@code LoginHandler}（框架自带
 * {@code DefaultLoginHandler} 的 {@code @Spi("WEB")} 与 {@code WeixinMiniLoginHandler} 的
 * {@code @Spi("WECHAT")}，另有外部件的 {@code StudentLoginService} 实现的是 {@code UserInfoService}
 * 而非 {@code LoginHandler}），于是 {@code getNewExtension("student")} 返回 {@code null}，
 * 登录口在其上抛空指针，对外固定 500 {@code S0512C0000}——凭据实现再正确也走不到校验那一步。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("STUDENT")
public class StudentLoginHandler extends AccountTypeLoginHandler {

    @Override
    protected String accountType() {
        return "student";
    }

    @Override
    protected String unavailableMessage() {
        return "学生登录暂不可用，请联系管理员";
    }

    @Override
    protected AuthType authType() {
        return AuthType.STUDENT;
    }
}
