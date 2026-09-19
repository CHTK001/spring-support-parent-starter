package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiUserSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 用户设置覆盖 映射器。
 *
 * <p>只用 MyBatis-Plus 的条件构造器，无自定义语句，因此不需要 XML。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiUserSettingMapper extends BaseMapper<SysAiUserSetting> {
}
