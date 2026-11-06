package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.entity.CollectDatasource;
import com.chua.starter.collect.vo.CollectDatasourceVO;
import com.chua.starter.collect.vo.CollectOptionVO;

import java.util.List;

/**
 * 采集数据源服务。
 *
 * <p>SQL 节点与结果导出按ID或名称引用这里登记的数据源，留空表示应用主库。
 * 配置变更后同步丢弃连接池缓存，避免改完仍连到旧参数。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectDatasourceService extends IService<CollectDatasource> {

    /**
     * 数据源列表，不含密码。
     *
     * @return 列表
     */
    List<CollectDatasourceVO> listDatasources();

    /**
     * 数据源下拉选项，值可直接填进节点的引用框。
     *
     * @return 选项列表
     */
    List<CollectOptionVO> options();

    /**
     * 新增数据源。
     *
     * @param datasource 数据源
     * @return 出参
     */
    CollectDatasourceVO addDatasource(CollectDatasource datasource);

    /**
     * 修改数据源，密码留空或回传占位符表示不修改。
     *
     * @param datasource 数据源
     * @return 是否成功
     */
    boolean editDatasource(CollectDatasource datasource);

    /**
     * 删除数据源，被流程引用时拒绝。
     *
     * @param datasourceId 数据源ID
     * @return 是否成功
     */
    boolean deleteDatasource(Integer datasourceId);

    /**
     * 测试连通性，允许在未保存的配置上执行。
     *
     * @param datasource 数据源
     * @return 数据库产品名与版本
     */
    String testConnection(CollectDatasource datasource);
}
