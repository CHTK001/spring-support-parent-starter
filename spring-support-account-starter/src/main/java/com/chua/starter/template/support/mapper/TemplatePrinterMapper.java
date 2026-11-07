package com.chua.starter.template.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.template.support.entity.TemplatePrinter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 打印机配置 映射器
 *
 * <p>单表 CRUD 走 MyBatis-Plus，按设备名的唯一性由表上的
 * {@code uk_template_printer_name} 保证。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Mapper
public interface TemplatePrinterMapper extends BaseMapper<TemplatePrinter> {
}
