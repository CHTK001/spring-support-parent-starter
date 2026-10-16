package com.chua.starter.eduai.support.credential;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.oauth.client.support.enums.AuthType;

/**
 * 教师登录处理器：让「工号 + 口令」能走通 {@code POST /v2/user/login}。
 *
 * <p>请求里带 {@code accountType=teacher} 时，登录口组合出的查找名是 {@code teacher}；
 * 本实现注册为大写的 {@code TEACHER}（SPI 大小写不敏感，两者等价命中）。校验逻辑全部在基类，
 * 真正的「工号反查 + 离职 / 未设口令 / 口令比对」委托给 {@link TeacherCredentialProvider}
 * （注册名 {@code teacher}），口径只有一份，不与按账号来源解析的链路分叉。</p>
 *
 * <p>注册由两处共同保证可靠：{@code META-INF/extensions/...LoginHandler} 索引文件（框架解析器在
 * provider 构造期读取，实例被回收重建后会重新发现）+ {@link EduCredentialConfiguration} 的显式注册。
 * 理由见 {@link AccountTypeLoginHandler} 类注释。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("TEACHER")
public class TeacherLoginHandler extends AccountTypeLoginHandler {

    @Override
    protected String accountType() {
        return "teacher";
    }

    @Override
    protected String unavailableMessage() {
        return "教师登录暂不可用，请联系管理员";
    }

    @Override
    protected AuthType authType() {
        return AuthType.TEACHER;
    }
}
