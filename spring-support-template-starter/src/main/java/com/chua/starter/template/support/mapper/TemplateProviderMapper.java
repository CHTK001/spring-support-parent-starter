package com.chua.starter.template.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.template.support.entity.TemplateProvider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模板提供商 映射器
 *
 * <p>单表 CRUD 走 MyBatis-Plus，提供商编码由
 * {@code uk_template_provider_name} 保证唯一。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Mapper
public interface TemplateProviderMapper extends BaseMapper<TemplateProvider> {
}
