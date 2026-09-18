package com.chua.starter.project.support.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.util.List;
import java.util.Map;

/**
 * 项目实时日志流服务
 * <p>
 * <b>为什么需要它</b>：原先前端每 1.5s 各自发一次 REST，后端每轮现场执行
 * {@code wc -c} + {@code tail} 两条远程命令；N 个观察者看同一个项目 = N 倍远程开销。
 * 本服务把「拉取」收敛到后端：按 {@code projectId + hostId} 维护一条**共享采集流**，
 * 后台定时拉取增量并经 socket 广播，一条流服务 N 个观察者。
 * </p>
 * <p>
 * <b>游标归属</b>：改为后端持有。前端不再传 cursor，只做展示；这也顺带修掉了
 * 「多个观察者各自推进游标、互相把对方的增量吃掉」的隐患。
 * </p>
 * <p>
 * <b>订阅生命周期</b>：{@link #subscribe} 记录观察者并首次触发采集，
 * {@link #unsubscribe} 移除；观察者需周期性续订（前端 30s 心跳），
 * 超过 {@code project.support.log-stream-idle-millis} 未续订会被剔除，
 * 剔除后无观察者的流自动停止 —— 这是浏览器被强杀/断网未发退订时的兜底。
 * </p>
 * <p>
 * <b>降级</b>：宿主未装配 socket 时本服务仍会采集但推送为空操作，
 * 因此 {@link #readOnce} 保留为 REST 一次性读取通道，供前端在无推送时回退轮询。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ProjectLogStreamService {

    /**
    * 一次性读取日志增量（REST 降级通道，同时保留原有 {@code GET /{id}/log} 契约）
    *
    * @param projectId 项目标识
    * @param hostId    主机标识
    * @param cursor    上次读取的字节偏移，为空表示取末尾若干行
    * @param lines     首帧拉取行数，为空取配置默认值
    * @return 日志内容 + 下次偏移 + 是否无新内容
    */
    ReturnResult<Map<String, Object>> readOnce(Long projectId, Long hostId, Long cursor, Integer lines);

    /**
    * 订阅实时日志推送（幂等：同一 subscriberId 重复调用即为续订心跳）
    *
    * @param projectId    项目标识
    * @param hostId       主机标识
    * @param subscriberId 观察者标识（前端生成，同一日志窗口复用同一个）
    * @return 流信息（采集间隔、续订超时），失败时返回错误
    */
    ReturnResult<Map<String, Object>> subscribe(Long projectId, Long hostId, String subscriberId);

    /**
    * 取消订阅（幂等）
    *
    * @param projectId    项目标识
    * @param hostId       主机标识
    * @param subscriberId 观察者标识
    * @return 操作结果
    */
    ReturnResult<Map<String, Object>> unsubscribe(Long projectId, Long hostId, String subscriberId);

    /**
    * 当前活跃流（运维排查：谁在拉、拉了多少次、是否在退避）
    *
    * @return 流快照列表
    */
    List<Map<String, Object>> activeStreams();
}
