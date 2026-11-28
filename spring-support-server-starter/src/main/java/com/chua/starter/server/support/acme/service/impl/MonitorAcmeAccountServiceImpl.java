package com.chua.starter.server.support.acme.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.server.support.acme.entity.MonitorAcmeAccount;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeAccountMapper;
import com.chua.starter.server.support.acme.service.MonitorAcmeAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import com.chua.common.support.utils.StringUtils;

/**
 * ACME 账户服务实现，提供账户按邮箱/状态/服务器查询、保存、状态更新与删除能力。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class MonitorAcmeAccountServiceImpl
        extends ServiceImpl<MonitorAcmeAccountMapper, MonitorAcmeAccount>
        implements MonitorAcmeAccountService {

    /**
     * 账户默认状态：有效
     */
    private static final String STATUS_VALID = "valid";

    /**
     * 按邮箱查询账户。
     *
     * @param email 邮箱
     * @return 账户列表，邮箱为空时返回空列表
     */
    @Override
    public List<MonitorAcmeAccount> listByEmail(String email) {
        if (StringUtils.isEmpty(email)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<MonitorAcmeAccount>lambdaQuery()
                .eq(MonitorAcmeAccount::getAcmeAccountEmail, email));
    }

    /**
     * 按状态查询账户。
     *
     * @param status 状态
     * @return 账户列表，状态为空时返回空列表
     */
    @Override
    public List<MonitorAcmeAccount> listByStatus(String status) {
        if (StringUtils.isEmpty(status)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<MonitorAcmeAccount>lambdaQuery()
                .eq(MonitorAcmeAccount::getAcmeAccountStatus, status));
    }

    /**
     * 按 ACME 服务器地址查询账户。
     *
     * @param server ACME 服务 URL
     * @return 账户列表，服务器为空时返回空列表
     */
    @Override
    public List<MonitorAcmeAccount> listByServer(String server) {
        if (StringUtils.isEmpty(server)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<MonitorAcmeAccount>lambdaQuery()
                .eq(MonitorAcmeAccount::getAcmeAccountServer, server));
    }

    /**
     * 保存账户：无主键时新增（默认状态 valid），有主键时更新。
     *
     * @param account 账户
     * @return 保存后的对象，参数为空或更新失败时返回 空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MonitorAcmeAccount saveOrUpdateAccount(MonitorAcmeAccount account) {
        if (account == null) {
            return null;
        }
        if (account.getAcmeAccountId() == null) {
            account.setCreateTime(LocalDateTime.now());
            account.setUpdateTime(LocalDateTime.now());
            if (account.getAcmeAccountStatus() == null) {
                account.setAcmeAccountStatus(STATUS_VALID);
            }
            save(account);
            return account;
        }
        account.setUpdateTime(LocalDateTime.now());
        boolean ok = updateById(account);
        return ok ? account : null;
    }

    /**
     * 更新账户状态。
     *
     * @param accountId 账户主键
     * @param newStatus 新状态
     * @return 是否成功，参数为空时返回 false
     */
    @Override
    public boolean updateStatus(Integer accountId, String newStatus) {
        if (accountId == null || newStatus == null) {
            return false;
        }
        MonitorAcmeAccount update = new MonitorAcmeAccount();
        update.setAcmeAccountId(accountId);
        update.setAcmeAccountStatus(newStatus);
        update.setUpdateTime(LocalDateTime.now());
        return updateById(update);
    }

    /**
     * 删除账户（关联证书和订单的级联删除由调用方负责）。
     *
     * @param accountId 账户主键
     * @return 是否成功，参数为空时返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeAccount(Integer accountId) {
        if (accountId == null) {
            return false;
        }
        return removeById(accountId);
    }

    /**
     * 统计账户总数。
     *
     * @return 账户数量
     */
    @Override
    public long countAccounts() {
        return count();
    }
}
