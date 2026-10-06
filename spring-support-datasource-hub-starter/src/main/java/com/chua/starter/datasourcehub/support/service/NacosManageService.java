package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.MiddlewareTestVO;
import com.chua.starter.datasourcehub.support.model.NacosClusterNodeVO;
import com.chua.starter.datasourcehub.support.model.NacosConfigDetailVO;
import com.chua.starter.datasourcehub.support.model.NacosConfigPageVO;
import com.chua.starter.datasourcehub.support.model.NacosInstanceVO;
import com.chua.starter.datasourcehub.support.model.NacosNamespaceVO;
import com.chua.starter.datasourcehub.support.model.NacosServiceVO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.model.dto.NacosConfigKeyDTO;
import com.chua.starter.datasourcehub.support.model.dto.NacosConfigQueryDTO;
import com.chua.starter.datasourcehub.support.model.dto.NacosConfigSaveDTO;
import com.chua.starter.datasourcehub.support.model.dto.NacosInstanceUpdateDTO;
import com.chua.starter.datasourcehub.support.model.dto.NacosNamespaceDTO;

import java.util.List;

/**
 * Nacos 配置中心管理服务：命名空间、配置读写、服务实例与集群节点。
 *
 * <p>所有方法都以「数据源 ID + 命名空间」定位目标 Nacos，数据源 ID 为空时退化为第一个
 * {@code nacos} 类型数据源；目标服务不可达时抛 {@link IllegalStateException} 并带上
 * Nacos 返回的原始原因，页面直接把原因摊开显示。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface NacosManageService {

    /**
     * 测试 Nacos 连通性并尽量取出版本号。
     *
     * @param request 测试请求，可为空（空即测试第一个 nacos 数据源）
     * @return 测试结果，失败时 connected 为 false 且 message 为原因
     */
    MiddlewareTestVO test(MiddlewareTestDTO request);

    /**
     * 列出命名空间。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 命名空间列表
     */
    List<NacosNamespaceVO> namespaces(Long datasourceId);

    /**
     * 新建命名空间。
     *
     * @param dto 命名空间载荷
     * @return 是否成功
     */
    boolean createNamespace(NacosNamespaceDTO dto);

    /**
     * 编辑命名空间（改名/改描述）。
     *
     * @param dto 命名空间载荷
     * @return 是否成功
     */
    boolean updateNamespace(NacosNamespaceDTO dto);

    /**
     * 删除命名空间。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param namespaceId  命名空间 ID
     * @return 是否成功
     */
    boolean deleteNamespace(Long datasourceId, String namespaceId);

    /**
     * 分页查询配置列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    NacosConfigPageVO configs(NacosConfigQueryDTO query);

    /**
     * 读取单个配置的正文。
     *
     * @param dataId 配置 Data ID
     * @param key    数据源/命名空间/分组定位条件
     * @return 配置详情
     */
    NacosConfigDetailVO configDetail(String dataId, NacosConfigKeyDTO key);

    /**
     * 发布配置（新建与更新同一入口，支持 beta 发布）。
     *
     * @param config 配置内容
     * @return 是否发布成功
     */
    boolean saveConfig(NacosConfigSaveDTO config);

    /**
     * 删除配置。
     *
     * @param dataId 配置 Data ID
     * @param key    数据源/命名空间/分组定位条件
     * @return 是否删除成功
     */
    boolean deleteConfig(String dataId, NacosConfigKeyDTO key);

    /**
     * 列出命名空间下的服务。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param namespace    命名空间标识
     * @return 服务列表
     */
    List<NacosServiceVO> services(Long datasourceId, String namespace);

    /**
     * 列出某服务下的实例。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param namespace    命名空间标识
     * @param serviceName  服务名
     * @param groupName    分组
     * @return 实例列表
     */
    List<NacosInstanceVO> instances(Long datasourceId, String namespace, String serviceName, String groupName);

    /**
     * 更新实例（上下线或改权重）。
     *
     * @param dto 实例更新载荷
     * @return 是否成功
     */
    boolean updateInstance(NacosInstanceUpdateDTO dto);

    /**
     * 列出集群节点。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 节点列表，单机部署只会有一个节点
     */
    List<NacosClusterNodeVO> clusterNodes(Long datasourceId);
}
