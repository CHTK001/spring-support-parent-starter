package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import org.apache.ibatis.annotations.Mapper;

/**
* 远程主机（SSH 主机簿）表（sys_AI_ssh_主机）映射器。
*
* <p>继承 {@link BaseMapper}，提供主机的增删改查能力（MyBatis-Plus 标准 CRUD）。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Mapper
public interface SysAiSshHostMapper extends BaseMapper<SysAiSshHost> {
}
