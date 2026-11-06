package com.chua.starter.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.lowcode.entity.LowForm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 低代码-表单定义 Mapper。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface LowFormMapper extends BaseMapper<LowForm> {
}
