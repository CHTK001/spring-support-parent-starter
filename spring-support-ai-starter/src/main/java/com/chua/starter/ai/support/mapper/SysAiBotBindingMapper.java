package com.chua.starter.ai.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.ai.support.entity.SysAiBotBinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微信 iLink Bot 绑定 Mapper。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface SysAiBotBindingMapper extends BaseMapper<SysAiBotBinding> {
}
