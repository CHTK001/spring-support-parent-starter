package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.AgentBrowserTab;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 浏览器 Tab 映射器 接口。
 *
 * <p>提供对 {@code sys_agent_browser_tab} 表的 CRUD 操作。
 * 继承 MyBatis-Plus 的 {@link BaseMapper}，自动获得基本的增删改查能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface AgentBrowserTabMapper extends BaseMapper<AgentBrowserTab> {

    /**
     * 根据 Lane 标识 查询关联的 Tab 列表。
     *
     * <p>用于获取指定 Lane 下的所有标签页信息。</p>
     *
     * @param laneId Lane 标识
     * @return 该 Lane 下的 Tab 列表
     */
    List<AgentBrowserTab> selectByLaneId(String laneId);

    /**
     * 将指定 Lane 的所有 Tab 设置为非活跃状态。
     *
     * <p>在切换活跃 Tab 时，先将当前 Lane 的所有 Tab 设置为非活跃，再激活目标 Tab。</p>
     *
     * @param laneId Lane 标识
     */
    void deactivateAllByLaneId(String laneId);

    /**
     * 删除指定 Lane 下的所有 Tab。
     *
     * <p>在关闭 Lane 时联动删除其关联的所有 Tab。</p>
     *
     * @param laneId Lane 标识
     */
    void deleteByLaneId(String laneId);
}
