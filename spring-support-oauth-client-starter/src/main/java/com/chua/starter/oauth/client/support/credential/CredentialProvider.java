package com.chua.starter.oauth.client.support.credential;

import com.chua.starter.oauth.client.support.user.LoginAuthResult;

/**
 * 凭据校验扩展点：决定「一个登录名 + 一个口令（或已校验的三方凭证）是否成立，
 * 以及成立时签发什么身份」。
 *
 * <p><b>与协议层 SPI 的分工</b>：{@code ProtocolExecutor}（{@code @Spi("static")} 等）
 * 解决的是「令牌怎么传输、怎么校验会话」，即传输层；本接口解决的是「账号口令从哪来、
 * 怎么比对」，即凭据层。两者正交，改凭据来源不需要动协议层。</p>
 *
 * <p><b>内置实现与回落顺序</b>：{@link CredentialResolver#resolve(CredentialRequest)}
 * 先找同名自定义实现，找不到再找 {@code builtin-} 前缀的内置实现，都没有则返回
 * {@code null}，由调用方执行既有逻辑。因此本接口新增一个账号来源时，
 * 业务模块只注册实现即可，不必修改 oauth 模块任何代码。</p>
 *
 * <p><b>注册方式</b>：实现类加 {@code @Spi("web")} 之类的注解，
 * 由 {@code ServiceLoaderServiceResolver} 扫描；同名实现中先注册者胜出，
 * 故内置实现统一使用 {@code builtin-} 前缀给自定义实现让位。</p>
 *
 * @since 4.0.0.42
 * @author CH
 * @see CredentialResolver
 * @see CredentialRequest
 */
@FunctionalInterface
public interface CredentialProvider {

    /**
     * 校验凭据并在通过时签发登录结果。
     *
     * <p>实现必须遵守：</p>
     * <ul>
     *     <li>口令错误、账号不存在、账号停用等一律返回 {@code null} 之外的
     *     {@link LoginAuthResult}（设置 403 与人话提示），<b>不要抛异常</b>——
     *     抛异常会被上层当成系统故障返回 500，把「密码错了」暴露成服务不可用。</li>
     *     <li>账号来源与本实现不匹配时返回 {@code null}，交回调用方回落到其它实现。</li>
     *     <li>不要在此处修改请求线程状态（会话、请求属性），那属于协议层职责。</li>
     * </ul>
     *
     * @param request 本次登录的入参，已由 {@link CredentialRequest} 完成规范化与不可变持有
     * @return 校验通过时返回含令牌的登录结果；无法处理或校验不通过时按上文约定返回
     */
    LoginAuthResult authenticate(CredentialRequest request);
}
