package com.chua.starter.account.manage.service;

import com.chua.starter.account.manage.model.AccountBatchResult;
import com.chua.starter.account.manage.store.SysAccount;
import com.chua.starter.account.manage.store.SysAccountSecret;

import java.util.List;
import java.util.Map;

/**
 * 统一账号服务（Spring Bean）。
 *
 * <p>设备、模板等<b>必选依赖</b>账号模块的模块直接注入本接口，
 * 按 {@code accountId} / {@code code} 取数并调用 {@link #resolveSecret(String)} 解密；
 * 不需要绕 SPI，也不允许自己再存一份密钥。</p>
 *
 * <p>AI 等<b>非必选</b>模块不依赖本接口：它们只从 utils 的 {@code ChatClient} SPI
 * 获得渠道能力，与本模块零代码耦合。</p>
 *
 * <p><b>命名说明</b>：接口名带 {@code Manage} 前缀，是为了与
 * {@code spring-api-support-common-system-starter} 里已有的系统登录账号服务
 * （{@code com.chua.starter.system.service.SysAccountService}）区分。Spring 按
 * 简单类名生成 Bean 名，重名会导致应用启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface ManageAccountService {

    /**
     * 查询当前用户可见的账号。
     *
     * @param kind       账号种类（可空）
     * @param keyword    名称 / 编码 / 用户名模糊词（可空）
     * @param channelKey 所属渠道标识（可空；传入时只回该渠道下的账号）
     * @return 账号列表（敏感字段已清空，仅保留 {@code hasSecret}）
     */
    List<SysAccount> list(String kind, String keyword, String channelKey);

    /**
     * 按标识查询账号。
     *
     * @param accountId 账号标识
     * @return 账号（敏感字段已清空）；不存在返回 {@code null}
     */
    SysAccount get(String accountId);

    /**
     * 按编码查询账号。
     *
     * @param code 账号编码
     * @return 账号（敏感字段已清空）；不存在返回 {@code null}
     */
    SysAccount getByCode(String code);

    /**
     * 新增账号。
     *
     * @param input 账号内容（{@code secret} 为明文，服务层负责加密）
     * @return 落库后的账号（敏感字段已清空）
     */
    SysAccount create(SysAccount input);

    /**
     * 更新账号。
     *
     * <p>{@code secret} 为空或掩码时保留原值，只有明文才覆盖。</p>
     *
     * @param input 账号内容（必须带 {@code accountId}）
     * @return 更新后的账号（敏感字段已清空）
     */
    SysAccount update(SysAccount input);

    /**
     * 逻辑删除账号。
     *
     * @param accountId 账号标识
     * @return 是否删除成功
     */
    boolean delete(String accountId);

    /**
     * 校验账号凭据（通过 {@code AccountVerifier} SPI，没有实现时保持 UNKNOWN）。
     *
     * @param accountId 账号标识
     * @return {@code {valid, status, message}}
     */
    Map<String, Object> verify(String accountId);

    /**
     * 批量更新账号状态（启用 / 停用）。
     *
     * <p>逐条校验归属与存在性：能改的照改，越权或已删除的记入失败明细，
     * 不因一条失败把整批回滚。</p>
     *
     * @param accountIds 账号标识清单
     * @param status     目标状态：1 启用 / 0 停用
     * @return 批量结果
     */
    AccountBatchResult batchStatus(List<String> accountIds, int status);

    /**
     * 批量校验账号凭据。
     *
     * @param accountIds 账号标识清单
     * @return 批量结果
     */
    AccountBatchResult batchVerify(List<String> accountIds);

    /**
     * 批量逻辑删除账号。
     *
     * @param accountIds 账号标识清单
     * @return 批量结果
     */
    AccountBatchResult batchDelete(List<String> accountIds);

    /**
     * 解密账号凭据，供必选依赖模块内部调用。
     *
     * @param accountId 账号标识
     * @return 明文凭据；账号不存在或未配置时返回 {@code null}
     */
    String resolveSecret(String accountId);

    /**
     * 按标识取账号，<b>不校验当前用户归属</b>。
     *
     * <p>供验证码、通知、调度告警等<b>系统级发送</b>使用：这些场景要么没有登录态
     * （注册 / 登录验证码），要么根本不在用户会话里（定时任务），按归属做可见性断言
     * 会把管理员配置的公共账号一起挡掉。普通业务读取账号请用 {@link #get(String)}，
     * 它会按归属断言可见性。</p>
     *
     * @param accountId 账号标识
     * @return 账号（敏感字段未清空）；不存在返回 {@code null}
     */
    SysAccount getInternal(String accountId);

    /**
     * 解析指定种类的默认账号，<b>不校验当前用户归属</b>。
     *
     * <p>解析顺序：</p>
     * <ol>
     *   <li>部署级（{@code owner} 为空）的启用账号，其中标记「默认」的优先、其次取最近更新的；</li>
     *   <li>部署级没有可用账号时，退到任意归属下标记「默认」的启用账号；</li>
     *   <li>都没有返回 {@code null}。</li>
     * </ol>
     *
     * <p>先看部署级是因为系统级发送没有用户上下文，公共账号才是正确来源；个人账号只在
     * 确实没有公共账号时兜底，避免「管理员建好账号却发不出去」。</p>
     *
     * @param kind 账号种类（{@code API_KEY} / {@code EMAIL} / {@code SMS}）
     * @return 默认账号（敏感字段已清空）；没有可用账号返回 {@code null}
     */
    SysAccount resolveDefault(String kind);

    /**
     * 把账号内联凭据收录到密钥库，并让账号改为引用该密钥。
     *
     * <p>账号里直接填写的 AccessKey/Secret（未引用密钥库）可一键收录：按账号种类映射
     * 密钥类型（API_KEY→api / EMAIL→mail / SMS→sms），凭据加密落到
     * {@code sys_account_secret}，账号 {@code config.secretId} 指向新密钥。
     * 已引用密钥的账号直接返回现有密钥，不重复收录。</p>
     *
     * @param accountId 账号标识
     * @return 收录后的密钥（不含明文 Secret）
     */
    SysAccountSecret collectSecret(String accountId);
}
