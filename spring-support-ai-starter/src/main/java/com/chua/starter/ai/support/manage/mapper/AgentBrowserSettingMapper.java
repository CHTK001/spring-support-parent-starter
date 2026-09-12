package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.AgentBrowserSetting;
import org.apache.ibatis.annotations.Mapper;

/**
   * 浏览器 Setting 映射器 接口。
 *
 * <p>提供对 {@code sys_agent_browser_setting} 表的 CRUD 操作。
   * 继承 MyBatis-Plus 的 {@link BaseMapper}，自动获得基本的增删改查能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface AgentBrowserSettingMapper extends BaseMapper<AgentBrowserSetting> {
}
