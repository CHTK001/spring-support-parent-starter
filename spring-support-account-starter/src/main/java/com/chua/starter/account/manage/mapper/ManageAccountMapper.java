package com.chua.starter.account.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.account.manage.store.SysAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一账号映射器。
 *
 * <p><b>为什么叫 ManageAccount 而不是 SysAccount</b>：本模块与
 * {@code spring-api-support-common-system-starter} 会同时出现在同一个应用里，
 * 后者已经有一个 {@code com.chua.starter.system.mapper.SysAccountMapper}
 * （系统登录账号）。MyBatis 的 Mapper 与 Spring 的 {@code @Service} 都按
 * <b>简单类名</b>生成 Bean 名，两个 {@code SysAccountMapper} 会直接抛
 * {@code ConflictingBeanDefinitionException}；加上 {@code Manage} 前缀后
 * Bean 名变成 {@code manageAccountMapper}，两边可以共存。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Mapper
public interface ManageAccountMapper extends BaseMapper<SysAccount> {
}
