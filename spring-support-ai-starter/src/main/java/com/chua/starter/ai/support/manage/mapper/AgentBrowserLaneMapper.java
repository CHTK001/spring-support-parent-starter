package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.AgentBrowserLane;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 浏览器 Lane 映射器 接口。
 *
 * <p>提供对 {@code sys_agent_browser_lane} 表的 CRUD 操作。
 * 继承 MyBatis-Plus 的 {@link BaseMapper}，自动获得基本的增删改查能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface AgentBrowserLaneMapper extends BaseMapper<AgentBrowserLane> {

    /**
     * 根据对话 标识 查询关联的 Lane 列表。
     *
     * <p>用于关闭对话时，找到该对话关联的所有 Lane 并联动关闭。</p>
     *
     * @param conversationId 对话 标识
     * @return 该对话关联的 Lane 列表
     */
    List<AgentBrowserLane> selectByConversationId(String conversationId);

    /**
     * 统计指定生命周期状态的 Lane 数量。
     *
     * <p>用于 Overview 接口统计 running/queued 等状态的数量。</p>
     *
     * @param state 生命周期状态
     * @return 该状态的 Lane 数量
     */
    int countByState(String state);

    /**
     * 查询所有非 失败 状态的 Lane。
     *
     * <p>用于 close-all 操作，找到所有需要关闭的 Lane。</p>
     *
     * @return 非 失败 状态的 Lane 列表
     */
    List<AgentBrowserLane> selectAllActive();
}
