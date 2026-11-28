package com.chua.starter.server.support.acme.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.server.support.acme.entity.MonitorAcmeCertificate;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeCertificateMapper;
import com.chua.starter.server.support.acme.service.MonitorAcmeCertificateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import com.chua.common.support.utils.StringUtils;

/**
 * ACME 证书服务实现，提供证书按账户/域名查询、过期监测、保存、状态更新与删除能力。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class MonitorAcmeCertificateServiceImpl
        extends ServiceImpl<MonitorAcmeCertificateMapper, MonitorAcmeCertificate>
        implements MonitorAcmeCertificateService {

    /**
     * 证书状态：已签发
     */
    private static final String STATUS_ISSUED = "issued";

    /**
     * 查询某账户下的所有证书。
     *
     * @param accountId 账户主键
     * @return 证书列表，账户为空时返回空列表
     */
    @Override
    public List<MonitorAcmeCertificate> listByAccountId(Integer accountId) {
        if (accountId == null) {
            return Collections.emptyList();
        }
        return list(Wrappers.<MonitorAcmeCertificate>lambdaQuery()
                .eq(MonitorAcmeCertificate::getAcmeAccountId, accountId)
                .orderByDesc(MonitorAcmeCertificate::getCreateTime));
    }

    /**
     * 查询某主域名的所有证书（含 SAN 匹配）。
     *
     * @param domain 主域名
     * @return 证书列表，域名为空时返回空列表
     */
    @Override
    public List<MonitorAcmeCertificate> listByDomain(String domain) {
        if (StringUtils.isEmpty(domain)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<MonitorAcmeCertificate>lambdaQuery()
                .eq(MonitorAcmeCertificate::getAcmeCertPrimaryDomain, domain)
                .or().like(MonitorAcmeCertificate::getAcmeCertSan, domain));
    }

    /**
     * 查询在指定时间窗口内即将过期的证书。
     *
     * @param now             当前时间（毫秒）
     * @param expireWindowMs  过期窗口（毫秒）
     * @return 即将过期证书列表
     */
    @Override
    public List<MonitorAcmeCertificate> listExpiring(long now, long expireWindowMs) {
        long cutoff = now + expireWindowMs;
        return list(Wrappers.<MonitorAcmeCertificate>lambdaQuery()
                .isNotNull(MonitorAcmeCertificate::getAcmeCertNotAfter)
                .apply("UNIX_TIMESTAMP(STR_TO_DATE(acme_cert_not_after, '%Y-%m-%dT%H:%i:%sZ')) * 1000 < {0}", cutoff)
                .eq(MonitorAcmeCertificate::getAcmeCertStatus, STATUS_ISSUED));
    }

    /**
     * 保存证书：无主键时新增（回填创建/更新时间），有主键时更新。
     *
     * @param cert 证书
     * @return 保存后的对象，参数为空或更新失败时返回 空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MonitorAcmeCertificate saveOrUpdateCert(MonitorAcmeCertificate cert) {
        if (cert == null) {
            return null;
        }
        if (cert.getAcmeCertId() == null) {
            cert.setCreateTime(LocalDateTime.now());
            cert.setUpdateTime(LocalDateTime.now());
            save(cert);
            return cert;
        }
        cert.setUpdateTime(LocalDateTime.now());
        boolean ok = updateById(cert);
        return ok ? cert : null;
    }

    /**
     * 更新证书状态。
     *
     * @param certId    证书主键
     * @param newStatus 新状态
     * @return 是否成功，参数为空时返回 false
     */
    @Override
    public boolean updateStatus(Integer certId, String newStatus) {
        if (certId == null || newStatus == null) {
            return false;
        }
        MonitorAcmeCertificate update = new MonitorAcmeCertificate();
        update.setAcmeCertId(certId);
        update.setAcmeCertStatus(newStatus);
        update.setUpdateTime(LocalDateTime.now());
        return updateById(update);
    }

    /**
     * 删除证书。
     *
     * @param certId 证书主键
     * @return 是否成功，参数为空时返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCert(Integer certId) {
        if (certId == null) {
            return false;
        }
        return removeById(certId);
    }

    /**
     * 统计证书总数。
     *
     * @return 证书数量
     */
    @Override
    public long countCerts() {
        return count();
    }
}
