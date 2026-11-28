package com.chua.starter.datapool.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datapool.support.model.DataSourceTestVO;
import com.chua.starter.datapool.support.model.DataSourceVO;
import com.chua.starter.datapool.support.model.DatasourceJdbcInfo;
import com.chua.starter.datapool.support.model.dto.DataSourceDTO;

import java.sql.Connection;
import java.util.List;

/**
 * 数据源中心服务：数据源配置落库、连通性探测与可复用 JDBC 连接构建。
 *
 * <p>独立于监控模块，供监控、采集等系统共享。数据源台账落 {@code datasource_info}。</p>
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
     * 列出数据源，可按「共享给采集平台」过滤。
     *
     * @param sharedCollect {@code true} 只列已注册给采集平台的；{@code false} 只列未注册的；null 不过滤
     * @return 数据源列表
     */
    ReturnResult<List<DataSourceVO>> list(Boolean sharedCollect);

    /**
     * 切换数据源的「共享给采集平台」标记。
     *
     * @param id     数据源 ID
     * @param shared true 注册共享、false 取消共享
     * @return 更新后的数据源
     */
    ReturnResult<DataSourceVO> toggleSharedCollect(Long id, boolean shared);

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
     * 新建 Calcite 合并数据源（跨库联合查询）。
     *
     * <p>合并记录由本动作独占创建：{@code type=calcite}，成员 id 列表以
     * {@code {"members":[id,...]}} 存入 {@code datasource_info_database}。
     * 普通 {@code create}/{@code update} 不接收该类型，避免旁路写入缺成员或
     * 非法嵌套的合并记录。</p>
     *
     * @param name      合并数据源名称
     * @param memberIds 参与联合查询的成员数据源 ID 列表（按 JDBC 类型、非合并）
     * @return 合并后的数据源
     */
    ReturnResult<DataSourceVO> merge(String name, List<Long> memberIds);

    /**
     * 删除数据源。
     *
     * @param id 数据源 ID
     * @return 删除结果
     */
    ReturnResult<Boolean> delete(Long id);

    /**
     * 置顶 / 取消置顶数据源。
     *
     * @param id  数据源 ID
     * @param top true 置顶、false 取消置顶
     * @return 更新后的数据源
     */
    ReturnResult<DataSourceVO> toggleTop(Long id, boolean top);

    /**
     * 测试数据源连通性：jdbc 真实建连，其余类型做 TCP 探测。
     *
     * @param id 数据源 ID
     * @return 测试结果
     */
    ReturnResult<DataSourceTestVO> test(Long id);

    /**
     * 只探测、不回写台账。
     *
     * <p>与 {@link #test(Long)} 的唯一区别是不写 {@code lastTestOk / lastTestTime /
     * 延迟} 这几个台账字段。</p>
     *
     * <p><strong>为什么要分出这一条路</strong>：监控指标的采集是定时发生的，
     * 如果它复用 {@code test}，那么「有人打开了监控指标页签」这件事就会把台账上的
     * 健康状态刷成新值 —— 左栏的「正常 / 异常」徽标会随之跳变。用户只是看了一眼图表，
     * 却改变了数据源的状态判定，这是采集行为不该有的副作用。</p>
     *
     * @param id 数据源 ID
     * @return 测试结果
     * @author CH
     * @since 4.0.0.43
     */
    ReturnResult<DataSourceTestVO> probeOnly(Long id);

    /**
     * 借出本次操作独占的 JDBC 连接，调用方必须关闭以归还连接池。
     *
     * @param datasourceId 数据源 ID，null/0 表示应用默认主数据源
     * @return 连接租约，close 时归还原池或释放底层连接
     * @throws Exception 数据源不存在、类型不受支持或连接失败
     */
    Connection connection(Long datasourceId) throws Exception;

    /**
     * 取得一条生命周期由调用方独占管理的 JDBC 连接。
     *
     * <p>台账 JDBC 每次建立独立连接；应用主库从容器已有的 DataSource 借出连接，
     * 关闭时归还原池。适用于跨请求事务会话与 Calcite 联邦成员；不承诺新建物理连接，
     * 不得为了满足该调用而绕过主库连接池。</p>
     *
     * @param datasourceId 数据源 ID，null/0 表示应用默认主数据源
     * @return 新连接，调用方负责关闭
     * @throws Exception 数据源不存在、类型不受支持或连接失败
     */
    Connection newConnection(Long datasourceId) throws Exception;

    /**
     * 解析 JDBC 连接三元组（URL / 账号 / 明文口令 / 驱动类名）。
     *
     * <p>供需要自行建连、且只接受 URL 与账号口令的调用方使用（例如数据同步的 源 与 Sink）。
     * 与 {@link #connection(Long)} 不同，本方法**不建立连接**，只做台账读取与 URL 拼装，
     * 因此不需要关闭任何资源。拼装规则与 {@link #connection(Long)} 完全一致。</p>
     *
     * @param datasourceId 数据源 ID；**不允许**为 null/0（应用默认主数据源的连接串由 Spring 装配，
     *                     台账里没有它的主机/库名，无法拼出 URL）
     * @return 连接三元组
     * @throws IllegalStateException    数据源不存在、为默认主数据源或缺少主机/库名
     * @throws UnsupportedOperationException 非 JDBC 数据源类型
     */
    DatasourceJdbcInfo jdbcInfo(Long datasourceId);
}
