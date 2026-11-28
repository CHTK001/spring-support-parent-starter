package com.chua.starter.server.support.acme.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.acme.entity.MonitorAcmeAccount;
import com.chua.starter.server.support.acme.entity.MonitorAcmeCertificate;
import com.chua.starter.server.support.acme.model.AcmeApplyCertRequest;
import com.chua.starter.server.support.acme.model.AcmeCertificateDetailVo;
import com.chua.starter.server.support.acme.model.CertStats;

/**
 * ACME 证书服务接口，提供证书生命周期管理功能。
 *
 * @since 2025-10-17
 * @author CH
 */
public interface AcmeCertificateService {

    /**
     * 保存/更新 ACME 账户 forcert
     *
     * @param account 账户
     * @return 结果（账户ID）
     */
    ReturnResult<Integer> saveAccountForCert(MonitorAcmeAccount account);

    /**
     * 申请证书 forcert
     *
     * @param request 请求
     * @return 证书ID
     */
    ReturnResult<Integer> applyCertificateForCert(AcmeApplyCertRequest request);

    /**
     * 续签证书 forcert
     *
     * @param certId 证书标识
     * @return 是否成功
     */
    ReturnResult<Boolean> renewCertificateForCert(Integer certId);

    /**
     * 吊销证书 forcert
     *
     * @param certId 证书标识
     * @return 是否成功
     */
    ReturnResult<Boolean> revokeCertificateForCert(Integer certId);

    /**
     * 获取证书详情 forcert（含账户和订单信息）
     *
     * @param certId 证书标识
     * @return 证书详情VO
     */
    ReturnResult<AcmeCertificateDetailVo> getCertificateDetailForCert(Integer certId);

    /**
     * 获取证书统计信息 forcert
     *
     * @return 统计信息
     */
    ReturnResult<CertStats> getCertStatsForCert();

    /**
     * 检查并续签即将到期的证书（定时任务调用）
     */
    void checkAndRenewSoonExpired();

    /**
     * 重新验证证书 forcert（用户手动部署完成后调用）
     *
     * @param certId 证书标识
     * @return 是否提交成功
     */
    ReturnResult<Boolean> retryValidationForCert(Integer certId);
}
