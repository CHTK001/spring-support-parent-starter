package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.common.support.task.message.MessageResponse;
import com.chua.starter.collect.engine.CollectRunResult;
import com.chua.starter.collect.entity.CollectNotice;
import com.chua.starter.collect.entity.CollectTask;
import com.chua.starter.collect.vo.CollectNotifyChannelVO;

import java.util.List;

/**
 * 采集执行通知服务。
 *
 * <p>负责通知配置的增删改查、渠道清单，以及任务落定终态后的命中判定与投递。
 * 投递一律异步执行且不外抛异常，通知失败只影响"最近结果"，不改变任务状态。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectNoticeService extends IService<CollectNotice> {

    /**
     * 通知配置列表。
     *
     * <p>通知已全局化，这里不接任何筛选维度，返回的就是全平台这一份清单。</p>
     *
     * @return 通知配置列表
     */
    List<CollectNotice> listNotices();

    /**
     * 已注册的通知渠道，供前端下拉与目标输入提示。
     *
     * @return 渠道列表
     */
    List<CollectNotifyChannelVO> channelOptions();

    /**
     * 新增通知配置。
     *
     * @param notice 通知配置
     * @return 保存后的配置
     */
    CollectNotice addNotice(CollectNotice notice);

    /**
     * 修改通知配置。
     *
     * @param notice 通知配置
     * @return 是否成功
     */
    boolean editNotice(CollectNotice notice);

    /**
     * 删除通知配置。
     *
     * @param noticeId 通知ID
     * @return 是否成功
     */
    boolean deleteNotice(Integer noticeId);

    /**
     * 按配置试发一条测试通知，用于在配置页面直接验证渠道是否可用。
     *
     * @param noticeId 通知ID
     * @return 发送结果
     */
    MessageResponse testNotice(Integer noticeId);

    /**
     * 任务终态后发送通知。
     *
     * @param task   任务行
     * @param result 执行结果
     */
    void notifyTask(CollectTask task, CollectRunResult result);
}
