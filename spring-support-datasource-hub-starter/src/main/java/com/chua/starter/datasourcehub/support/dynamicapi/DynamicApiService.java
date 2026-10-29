package com.chua.starter.datasourcehub.support.dynamicapi;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.dbconsole.DbSqlResultVO;
import com.chua.starter.datasourcehub.support.dynamicapi.model.DynamicApiDTO;
import com.chua.starter.datasourcehub.support.dynamicapi.model.DynamicApiVO;

import java.util.List;
import java.util.Map;

/**
 * 动态接口服务：数据源「接口管理」的读写与发布生命周期。
 *
 * <p>一条记录 = 绑定到某个数据源（{@code datasourceId}，可为 calcite 合并源）的
 * SQL 查询（{@code sqlText}）对外暴露成一个接口：保存落 {@code ms_datasource_api}，
 * 执行时经 {@code DbConsoleController} 同口径执行链查库返回。发布分两轨：</p>
 * <ul>
 *   <li>{@code embedded} — 动态注册进当前应用的 {@code RequestMappingHandlerMapping}，
 *       走应用自身的系统 oauth 安全门控；</li>
 *   <li>{@code standalone} — 在独立端口上起 common-starter 的 {@code Server}，
 *       直接对外提供 HTTP 接口，鉴权靠 AppKey（{@code AiAppKeySupport}）。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface DynamicApiService {

    /**
     * 按数据源列出动态接口（datasourceId 为空列出全部）。
     *
     * @param datasourceId 数据源 id，可为 null
     * @return 接口列表（含数据源名与发布状态）
     */
    ReturnResult<List<DynamicApiVO>> list(Long datasourceId);

    /**
     * 查询单个动态接口。
     *
     * @param id 接口 id
     * @return 接口详情
     */
    ReturnResult<DynamicApiVO> get(Long id);

    /**
     * 新建动态接口（SQL 保存入库）。
     *
     * @param dto 接口定义
     * @return 创建后的接口详情
     */
    ReturnResult<DynamicApiVO> create(DynamicApiDTO dto);

    /**
     * 更新动态接口定义。
     *
     * <p>若该接口当前已发布，更新会<b>原位重新发布</b>（改完 SQL/path/方法后
     * 的运行时立即生效），避免出现「库里的定义与线上行为不一致」的静默漂移。</p>
     *
     * @param id  接口 id
     * @param dto 新的接口定义
     * @return 更新后的接口详情
     */
    ReturnResult<DynamicApiVO> update(Long id, DynamicApiDTO dto);

    /**
     * 删除动态接口定义（已发布则先下线）。
     *
     * @param id 接口 id
     * @return 是否成功
     */
    ReturnResult<Boolean> delete(Long id);

    /**
     * 发布接口（embedded / standalone 按定义中的 publishMode 走）。
     *
     * @param id 接口 id
     * @return 发布状态与外部可调用地址
     */
    ReturnResult<DynamicApiPublishResult> publish(Long id);

    /**
     * 下线接口（取消动态映射 / 停掉独立 Server 上的路由）。
     *
     * @param id 接口 id
     * @return 是否成功
     */
    ReturnResult<Boolean> unpublish(Long id);

    /**
     * 试运行：用给定参数执行接口 SQL（不发布、不走鉴权与缓存）。
     *
     * <p>管理界面「SQL 保存入库」后点运行，直接复用查询控制台的执行链
     * （{@code bindParams}/{@code applyQueryTimeout}/{@code SQL_MAX_ROWS}）。</p>
     *
     * @param id     接口 id
     * @param params {@code ${name}} 占位符取值
     * @return 执行结果
     */
    ReturnResult<DbSqlResultVO> run(Long id, Map<String, Object> params);

    /**
     * 发布后的运行时调用：校验鉴权、命中结果缓存则直接返回，否则执行并回填缓存。
     *
     * @param apiId 接口 id
     * @param params 请求参数
     * @param appKey AppKey（authMode=appkey 时必填）
     * @return 执行结果
     */
    ReturnResult<Object> invokePublished(Long apiId, Map<String, Object> params, String appKey);

    /**
     * 当前实例上该接口是否已发布。
     *
     * @param id 接口 id
     * @return 是否已发布
     */
    boolean isPublished(Long id);
}