package com.chua.starter.gen.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.gen.support.entity.SysGenTable;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys-gen表映射器
 *
 * @author CH
 * @since 2023/09/21
 */
@Mapper
public interface SysGenTableMapper extends BaseMapper<SysGenTable> {
}