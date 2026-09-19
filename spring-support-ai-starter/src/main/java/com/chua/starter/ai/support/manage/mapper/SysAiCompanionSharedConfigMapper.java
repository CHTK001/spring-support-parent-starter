package com.chua.starter.ai.support.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.manage.store.SysAiCompanionSharedConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 桌面伙伴共享配置表 映射器（MyBatis-Plus 标准 CRUD）。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiCompanionSharedConfigMapper extends BaseMapper<SysAiCompanionSharedConfig> {
}