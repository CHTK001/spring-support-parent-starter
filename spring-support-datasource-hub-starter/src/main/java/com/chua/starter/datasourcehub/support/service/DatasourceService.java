package com.chua.starter.datasourcehub.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceTestVO;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.model.dto.DataSourceDTO;

import java.sql.Connection;
import java.util.List;

/**
 * 数据源中心服务：数据源配置落库、连通性探测与可复用 JDBC 连接构建。
 *
 * <p>独立于监控模块，供监控、采集等系统共享。数据源台账落 {@code ms_datasource}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface DatasourceService {

    /**
     * 支持的数据源类型。
     *
     * @return 数据源类型列表
     */
    List<String> supportedTypes();

    /**
     * 列出全部数据源。
     *
     * @return 数据源列表
     */
    ReturnResult<List<DataSourceVO>> list();

    /**
     * 查询数据源详情。
     *
     * @param id 数据源 ID
     * @return 数据源详情，不存在时返回错误
     */
    ReturnResult<DataSourceVO> get(Long id);

    /**
     * 新增数据源。
     *
     * @param data 数据源配置
     * @return 新增后的数据源
     */
    ReturnResult<DataSourceVO> create(DataSourceDTO data);

    /**
     * 更新数据源。
     *
     * @param id   数据源 ID
     * @param data 数据源配置
     * @return 更新后的数据源
     */
    ReturnResult<DataSourceVO> update(Long id, DataSourceDTO data);

    /**
     * 删除数据源。
     *
     * @param id 数据源 ID
     * @return 删除结果
     */
    ReturnResult<Boolean> delete(Long id);

    /**
     * 测试数据源连通性：jdbc 真实建连，其余类型做 TCP 探测。
     *
     * @param id 数据源 ID
     * @return 测试结果
     */
    ReturnResult<DataSourceTestVO> test(Long id);

    /**
     * 构建 JDBC 连接（空闲 TTL 缓存 + 屏蔽 close 的代理）。
     *
     * @param datasourceId 数据源 ID，null/0 表示应用默认主数据源
     * @return 连接，调用方负责关闭（代理的 close 为空操作）
     * @throws Exception 数据源不存在、类型不受支持或连接失败
     */
    Connection connection(Long datasourceId) throws Exception;
}