package com.chua.starter.template.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.template.support.entity.TemplateLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模板调用日志 映射器
 *
 * <p>单表写入与按条件查询，走 MyBatis-Plus。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Mapper
public interface TemplateLogMapper extends BaseMapper<TemplateLog> {
}
