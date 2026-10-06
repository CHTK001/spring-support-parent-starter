package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.EtcdKeyDetailVO;
import com.chua.starter.datasourcehub.support.model.EtcdKeyVO;
import com.chua.starter.datasourcehub.support.model.EtcdLeaseVO;
import com.chua.starter.datasourcehub.support.model.EtcdMemberVO;
import com.chua.starter.datasourcehub.support.model.EtcdStatusVO;
import com.chua.starter.datasourcehub.support.model.EtcdTestVO;
import com.chua.starter.datasourcehub.support.model.dto.EtcdKeyDTO;
import com.chua.starter.datasourcehub.support.model.dto.EtcdKeyQueryDTO;
import com.chua.starter.datasourcehub.support.model.dto.EtcdKeySaveDTO;
import com.chua.starter.datasourcehub.support.model.dto.EtcdLeaseDTO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import java.util.List;

/**
 * etcd 管理服务。
 *
 * <p>能力对齐 ZooKeeper 控制台：键按前缀分层浏览、键详情（值 + revision + 租约 TTL）、
 * 键的新增/修改/删除、租约（TTL）管理与集群成员健康。所有 {@code datasourceId} 均可缺省，
 * 由服务端退化为第一个 etcd 数据源。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface EtcdManageService {

    /**
     * 测试连通性。
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    EtcdTestVO test(MiddlewareTestDTO request);

    /**
     * 读取服务端状态。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 服务端状态
     */
    EtcdStatusVO status(Long datasourceId);

    /**
     * 按前缀列举键。
     *
     * @param query 查询条件
     * @return 键列表
     */
    List<EtcdKeyVO> keys(EtcdKeyQueryDTO query);

    /**
     * 读取键详情（含值）。
     *
     * @param key 键名
     * @param dto 请求（含 datasourceId）
     * @return 键详情
     */
    EtcdKeyDetailVO key(String key, EtcdKeyDTO dto);

    /**
     * 写入键（新增或覆盖）。
     *
     * @param request 写入请求
     * @return 是否成功
     */
    boolean save(EtcdKeySaveDTO request);

    /**
     * 删除键。
     *
     * @param key 键名
     * @param dto 请求（含 datasourceId 与前缀删除开关）
     * @return 实际删除的键数量
     */
    long delete(String key, EtcdKeyDTO dto);

    /**
     * 列举租约。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 租约列表
     */
    List<EtcdLeaseVO> leases(Long datasourceId);

    /**
     * 授予租约。
     *
     * @param request 授予请求（TTL 必填）
     * @return 新建的租约
     */
    EtcdLeaseVO grantLease(EtcdLeaseDTO request);

    /**
     * 撤销租约。
     *
     * @param request 撤销请求（leaseId 必填）
     * @return 是否成功
     */
    boolean revokeLease(EtcdLeaseDTO request);

    /**
     * 列举集群成员及其健康状态。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 成员列表
     */
    List<EtcdMemberVO> members(Long datasourceId);
}
