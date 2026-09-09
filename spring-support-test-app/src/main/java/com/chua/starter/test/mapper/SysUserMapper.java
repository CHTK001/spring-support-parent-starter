package com.chua.starter.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.mybatis.annotations.DataScope;
import com.chua.starter.test.entity.SysUser;

import java.util.List;

/**
 * 数据权限测试 mapper。
 *
 * <p>{@code selectByScope} 应用数据权限（默认取当前用户的数据权限类型）；
 * {@code selectIgnoreScope} 通过 {@code @DataScope(enabled=false)} 显式禁用。
 * SQL 定义见 {@code resources/mapper/SysUserMapper.xml}。</p>
 *
 * @author CH
 * @since 2026/09/04
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    @DataScope
    List<SysUser> selectByScope();

    @DataScope(enabled = false)
    List<SysUser> selectIgnoreScope();
}
