package com.chua.starter.server.support.acme.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.server.support.acme.entity.MonitorAcmeAccount;

import java.util.List;

/**
 * ACME 账户服务
 *
 * <p>管理 Let's Encrypt / ZeroSSL 等 ACME 提供方账户。
 * 账户私钥(EAB)保存在 {@code acmeAccountPrivateKeyPem},业务调用方不应回显该字段。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface MonitorAcmeAccountService extends IService<MonitorAcmeAccount> {

    /**
     * 按邮箱查找账户。
     *
     * @param email 邮箱
     * @return 账户列表
     */
    List<MonitorAcmeAccount> listByEmail(String email);

    /**
     * 按状态查找账户(valid/pending/deactivated)。
     *
     * @param status 状态
     * @return 账户列表
     */
    List<MonitorAcmeAccount> listByStatus(String status);

    /**
     * 按服务器地址查找账户(例 acme-v02.api.letsencrypt.org/目录)。
     *
     * @param server ACME 服务 URL
     * @return 账户列表
     */
    List<MonitorAcmeAccount> listByServer(String server);

    /**
     * 保存账户(新建或更新)。
     *
     * @param account 账户
     * @return 保存后的对象
     */
    MonitorAcmeAccount saveOrUpdateAccount(MonitorAcmeAccount account);

    /**
     * 更新账户状态。
     *
     * @param accountId 账户主键
     * @param newStatus 新状态
     * @return 是否成功
     */
    boolean updateStatus(Integer accountId, String newStatus);

    /**
     * 删除账户(级联删除关联证书和订单由调用方负责)。
     *
     * @param accountId 账户主键
     * @return 是否成功
     */
    boolean removeAccount(Integer accountId);

    /**
     * 统计账户总数。
     *
     * @return 数量
     */
    long countAccounts();
}
