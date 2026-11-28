package com.chua.starter.server.support.acme.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.server.support.acme.entity.MonitorAcmeCertificate;

import java.util.List;

/**
 * ACME 证书服务
 *
 * <p>管理由 ACME 协议签发的证书记录,跟踪状态、SAN、有效期等元数据,
 * 配合 {@link MonitorAcmeAccountService} 与 {@link MonitorAcmeOrderService} 使用。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface MonitorAcmeCertificateService extends IService<MonitorAcmeCertificate> {

    /**
     * 查询某账户下的所有证书。
     *
     * @param accountId 账户主键
     * @return 证书列表
     */
    List<MonitorAcmeCertificate> listByAccountId(Integer accountId);

    /**
     * 查询某主域名的所有证书(含 SAN)。
     *
     * @param domain 主域名
     * @return 证书列表
     */
    List<MonitorAcmeCertificate> listByDomain(String domain);

    /**
     * 查询即将过期(< 30 天)的证书。
     *
     * @param now 当前时间(毫秒)
     * @param expireWindowMs 过期窗口(毫秒)
     * @return 即将过期证书列表
     */
    List<MonitorAcmeCertificate> listExpiring(long now, long expireWindowMs);

    /**
     * 保存证书(新建或更新)。
     *
     * @param cert 证书
     * @return 保存后的对象
     */
    MonitorAcmeCertificate saveOrUpdateCert(MonitorAcmeCertificate cert);

    /**
     * 更新证书状态(issued/expired/revoked/...).
     *
     * @param certId   证书主键
     * @param newStatus 新状态
     * @return 是否成功
     */
    boolean updateStatus(Integer certId, String newStatus);

    /**
     * 删除证书。
     *
     * @param certId 证书主键
     * @return 是否成功
     */
    boolean removeCert(Integer certId);

    /**
     * 统计证书总数。
     *
     * @return 数量
     */
    long countCerts();
}
