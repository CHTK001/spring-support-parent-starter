package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.ZkChildNodeVO;
import com.chua.starter.datasourcehub.support.model.ZkClusterNodeVO;
import com.chua.starter.datasourcehub.support.model.ZkNodeAclVO;
import com.chua.starter.datasourcehub.support.model.ZkNodeDetailVO;
import com.chua.starter.datasourcehub.support.model.ZkNodeVO;
import com.chua.starter.datasourcehub.support.model.ZookeeperTestVO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.model.dto.ZkNodeDTO;
import java.util.List;

/**
 * ZooKeeper 注册中心管理能力：连通性探测、节点树浏览与节点读写。
 *
 * <p>所有方法的 {@code datasourceId} 允许为空，由实现退化为第一个 {@code zookeeper} 数据源，
 * 以便只登记一套 ZooKeeper 时页面不必先选。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ZookeeperManageService {

    /**
     * 测试连通性，顺带回传服务端版本与会话 ID。
     *
     * @param request 测试载荷，为空时按已登记数据源探测
     * @return 测试结果
     */
    ZookeeperTestVO test(MiddlewareTestDTO request);

    /**
     * 读取某路径下的直接子节点，用于树形按需展开。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path         父路径，空视为根节点
     * @return 子节点列表
     */
    List<ZkNodeVO> tree(Long datasourceId, String path);

    /**
     * 读取节点详情（stat + data）。
     *
     * @param path         节点绝对路径
     * @param datasourceId 数据源 ID，可空
     * @return 节点详情
     */
    ZkNodeDetailVO node(String path, Long datasourceId);

    /**
     * 读取子节点及其数据。
     *
     * @param path         父路径
     * @param datasourceId 数据源 ID，可空
     * @return 子节点列表
     */
    List<ZkChildNodeVO> children(String path, Long datasourceId);

    /**
     * 创建节点，父级缺失时一并补齐。
     *
     * @param request 节点载荷
     * @return 是否成功
     */
    boolean create(ZkNodeDTO request);

    /**
     * 覆盖节点数据。
     *
     * @param request 节点载荷
     * @return 是否成功
     */
    boolean update(ZkNodeDTO request);

    /**
     * 删除节点。
     *
     * @param path         节点绝对路径
     * @param datasourceId 数据源 ID，可空
     * @param recursive    为真时递归删除所有子孙节点
     * @return 是否成功
     */
    boolean delete(String path, Long datasourceId, boolean recursive);

    /**
     * 采集集群各节点的运行指标（四字命令 {@code mntr}）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 每个集群节点一行指标
     */
    List<ZkClusterNodeVO> cluster(Long datasourceId);

    /**
     * 读取节点的 ACL 列表。
     *
     * @param path         节点绝对路径
     * @param datasourceId 数据源 ID，可空
     * @return ACL 条目列表
     */
    List<ZkNodeAclVO> acl(String path, Long datasourceId);
}
