package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;

import java.util.List;

/**
* 服务器主机服务
* <p>
* 提供主机台账 CRUD、连通性测试、身份绑定管理，
* 以及供远控/SFTP/批量执行共用的凭据解析能力。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
public interface ServerHostService {

    /**
    * 新增主机（自动生成探针标识 Agent键）
    *
    * @param host 主机
    * @return 结果
    */
    ReturnResult<MsServerHost> create(MsServerHost host);

    /**
    * 更新主机
    *
    * @param id   主键
    * @param host 主机
    * @return 结果
    */
    ReturnResult<MsServerHost> update(Long id, MsServerHost host);

    /**
    * 删除主机（同时解除全部身份绑定）
    *
    * @param id 主键
    * @return 结果
    */
    ReturnResult<Void> delete(Long id);

    /**
    * 查询单个主机
    *
    * @param id 主键
    * @return 结果
    */
    ReturnResult<MsServerHost> get(Long id);

    /**
    * 条件查询主机列表
    *
    * @param keyword 关键词，匹配名称/编码/地址，可为空
    * @param groupId 分组 标识，可为空表示不过滤
    * @param status  启用状态 1/0，可为空表示不过滤
    * @return 主机列表
    */
    ReturnResult<List<MsServerHost>> query(String keyword, Long groupId, Integer status);

    /**
    * 启用/停用主机
    *
    * @param id      主键
    * @param enabled 是否启用
    * @return 结果
    */
    ReturnResult<Void> updateStatus(Long id, boolean enabled);

    /**
    * 连通性测试：按已绑定身份尝试 SSH 登录并回显内核信息
    *
    * @param id 主键
    * @return 测试结果（message 含 uname 输出或失败原因）
    */
    ReturnResult<String> testConnect(Long id);

    /**
    * 绑定身份到主机
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识
    * @param sort       优先级，越小越优先；为空默认取当前最大值加一
    * @return 结果
    */
    ReturnResult<Void> bindIdentity(Long hostId, Long identityId, Integer sort);

    /**
    * 解除主机上的身份绑定
    *
    * @param hostId     主机 标识
    * @param identityId 身份 标识
    * @return 结果
    */
    ReturnResult<Void> unbindIdentity(Long hostId, Long identityId);

    /**
    * 查询主机已绑定的身份 标识 列表（按优先级升序）
    *
    * @param hostId 主机 标识
    * @return 身份 标识 列表
    */
    ReturnResult<List<Long>> listBoundIdentityIds(Long hostId);

    /**
    * 解析主机登录凭据
    * <p>
    * 按 identityid 精确匹配绑定关系；未指定时按优先级取第一个绑定身份。
    * 返回对象含解密后的明文凭据，仅限运行时使用，禁止落库或输出日志。
    * </p>
    *
    * @param hostId     主机 标识
    * @param identityId 指定身份 标识，可为空
    * @return 凭据结果，data 为解密后的凭据
    */
    ReturnResult<SshCredential> resolveCredential(Long hostId, Long identityId);

    /**
    * 硬件探测：SSH 连上目标主机，自动采集 CPU/内存/磁盘/系统版本/IP 并回写
    *
    * @param hostId 主机 标识
    * @return 更新后的主机（含硬件信息）
    */
    ReturnResult<MsServerHost> detectHardware(Long hostId);

    /**
    * 刷新公网 IP：通过 ifconfig.me/ipinfo.io 探测目标主机出口公网 IP 并回写
    *
    * @param hostId 主机 标识
    * @return 刷新后的公网 IP
    */
    ReturnResult<String> refreshPublicIp(Long hostId);

    /**
    * 端口扫描：依赖本机 nmap 对目标主机做 TCP 连接扫描，解析开放端口并回写
    * 主机资产表的 {@code serverHostPorts}（JSON 数组）与扫描时间。
    *
    * @param hostId 主机 标识
    * @return 更新后的主机（含端口扫描结果）
    */
    ReturnResult<MsServerHost> scanPorts(Long hostId);

    /**
    * 新增主机时自动创建登录身份并绑定
    * <p>
    * 优先使用主机上直接携带的账号密码 / 私钥（pem），
    * 未携带时回退为第一顺位已绑定身份（保证可测连）。
    * </p>
    *
    * @param host 主机（含 服务端主机账户/密码/私募键 瞬态字段）
    * @return 处理后的主机
    */
    MsServerHost autoCreateIdentity(MsServerHost host);

    /**
    * 批量同步主机资产：对指定/全部启用主机进行 SSH 硬件探测（含公网/内网 IP），
    * 将探测到的不变信息（主机信息、硬件规格、IP）同步回资产表；
    * 若目标公网/内网 IP 与库中不一致，仅在该场景触发更新。
    * 使用虚拟线程并发执行，避免阻塞请求线程。
    *
    * @param hostIds 目标主机 ID 列表；为空则同步全部启用主机
    * @return 同步结果概要（成功数 / 跳过数 / 失败数 / 变更明细）
    */
    ReturnResult<java.util.Map<String, Object>> syncHosts(java.util.List<Long> hostIds);
}
