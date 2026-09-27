package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsProxy;
import com.chua.starter.server.support.model.vo.ProxyCheckSummaryVO;

import java.util.List;

/**
 * 免费代理服务：自研抓取（databay 可用源）→ 清空代理表 → 录入；页面从表读取
 * <p>
 * 除抓取外还提供台账维护（新增/编辑/删除/清空）与探测，使代理表可脱离外部源独立运营。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
public interface ProxyService {

    /**
     * 未检测状态标识（{@code status} 取值之一）
     */
    String STATUS_UNCHECKED = "unchecked";

    /**
     * 可达状态标识（{@code status} 取值之一）
     */
    String STATUS_REACHABLE = "reachable";

    /**
     * 不可达状态标识（{@code status} 取值之一）
     */
    String STATUS_UNREACHABLE = "unreachable";

    /**
     * 获取免费代理（抓取→清空表→录入——返回新列表——较慢）
     *
     * @return 免费代理列表
     */
    List<MsProxy> fetchFreeProxies();

    /**
     * 代理列表（从数据表读取——快）
     *
     * @param protocol 协议精确过滤，可为空
     * @param status   探测状态过滤，取值 {@link #STATUS_UNCHECKED}/{@link #STATUS_REACHABLE}/
     *                 {@link #STATUS_UNREACHABLE}，可为空表示不过滤
     * @param keyword  主机关键词模糊过滤，可为空
     * @param limit    最多返回条数，为空取默认窗口
     * @return 免费代理列表，按主键升序
     */
    List<MsProxy> listProxies(String protocol, String status, String keyword, Integer limit);

    /**
     * 查询单个代理
     *
     * @param id 代理主键
     * @return 代理详情，不存在时返回业务错误
     */
    ReturnResult<MsProxy> get(Long id);

    /**
     * 新增代理（探测字段由本服务维护，入参里的探测值一律忽略）
     *
     * @param proxy 代理（host/port 必填，protocol 缺省为 Http）
     * @return 新增后的代理；地址重复或参数非法时返回业务错误
     */
    ReturnResult<MsProxy> create(MsProxy proxy);

    /**
     * 编辑代理
     *
     * <p>地址或协议变更后旧的探测结论即失效，因此本方法总是把探测字段重置为未检测。</p>
     *
     * @param id    代理主键
     * @param proxy 代理
     * @return 更新后的代理；不存在或参数非法时返回业务错误
     */
    ReturnResult<MsProxy> update(Long id, MsProxy proxy);

    /**
     * 删除代理
     *
     * @param id 代理主键
     * @return 结果
     */
    ReturnResult<Void> delete(Long id);

    /**
     * 清空代理表
     *
     * @return 结果，data 为删除条数
     */
    ReturnResult<Integer> clear();

    /**
     * 当前代理（自动——表内第一个——业务无需手动指定）
     *
     * @return "host:port"，无则 空
     */
    String currentProxy();

    /**
     * 批量检测代理（虚拟线程并发探测，全部收敛后返回）
     *
     * @return 已检测条数与其中可达条数
     */
    ProxyCheckSummaryVO checkProxies();

    /**
     * 检测单个代理
     *
     * @param id 代理ID
     * @return true 表示记录存在且已完成探测
     */
    boolean checkProxy(Long id);
}
