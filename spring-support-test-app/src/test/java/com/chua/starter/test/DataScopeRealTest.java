package com.chua.starter.test;

import com.chua.starter.common.support.constant.DataFilterTypeEnum;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.test.config.DataScopeAuthService;
import com.chua.starter.test.entity.SysUser;
import com.chua.starter.test.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
* 数据权限（@数据scope）真实测试：MyBatis-Plus + H2 + 真实 映射器 + 真实数据权限拦截器。
*
* <p>数据：sys_user 表含部门 10（张三/李四）与部门 20（王五），列名用框架默认值
* （sys_dept_标识 / 创建_by）。当前用户经 {@link DataScopeAuthService} 按用例注入。</p>
*
* @author CH
* @since 2026/09/04
 */
@SpringBootTest
class DataScopeRealTest {

    @Autowired
    private SysUserMapper sysUserMapper; // sys用户映射器
    @Autowired
    private JdbcTemplate jdbcTemplate; // jdbctemplate

    @BeforeEach
    /**
    * 参见。
     */
    void seed() {
        jdbcTemplate.execute("drop table if exists sys_user");
        jdbcTemplate.execute("create table sys_user("
                + "id bigint auto_increment primary key,"
                + "name varchar(50),"
                + "sys_dept_id varchar(20),"
                + "create_by varchar(20))");
        jdbcTemplate.update("insert into sys_user(name, sys_dept_id, create_by) values ('张三', '10', 'u1')");
        jdbcTemplate.update("insert into sys_user(name, sys_dept_id, create_by) values ('李四', '10', 'u1')");
        jdbcTemplate.update("insert into sys_user(name, sys_dept_id, create_by) values ('王五', '20', 'u2')");
    }

    /**
    * 用户。
    * @param dataPermission 数据权限
    * @param deptId deptid
    * @param roles 角色
    * @return 用户的结果
     */
    private static CurrentUser user(DataFilterTypeEnum dataPermission, String deptId, Set<String> roles) {
        CurrentUser user = new CurrentUser();
        user.setUserId("1");
        user.setUsername("tester");
        user.setDeptId(deptId);
        user.setDataPermission(dataPermission);
        if (roles != null) {
            user.setRoles(roles);
        }
        return user;
    }

    /** DEPT 数据权限：只返回本部门（10）数据 */
    @Test
    void deptScopeFiltersRows() {
        DataScopeAuthService.setCurrentUser(user(DataFilterTypeEnum.DEPT, "10", Set.of("USER")));
        List<SysUser> users = sysUserMapper.selectByScope();
        /**
        * 断言equals。
        * @param 2 2
        * @param users.size( 用户.大小(
         */
        assertEquals(2, users.size(), "DEPT 范围应只返回本部门数据");
        assertTrue(users.stream().allMatch(u -> "10".equals(u.getSysDeptId())),
                "返回数据应全部属于当前部门");
    }

    /** 超级管理员：无视数据权限，返回全部数据 */
    @Test
    void superAdminSeesAll() {
        DataScopeAuthService.setCurrentUser(user(DataFilterTypeEnum.DEPT, "10", Set.of("SUPER_ADMIN")));
        List<SysUser> users = sysUserMapper.selectByScope();
        /**
        * 断言equals。
        * @param 3 3
        * @param users.size( 用户.大小(
         */
        assertEquals(3, users.size(), "超级管理员应看到全部数据");
    }

    /** @数据scope(已启用=false)：显式禁用数据权限，返回全部数据 */
    @Test
    void disabledScopeSeesAll() {
        DataScopeAuthService.setCurrentUser(user(DataFilterTypeEnum.DEPT, "10", Set.of("USER")));
        List<SysUser> users = sysUserMapper.selectIgnoreScope();
        /**
        * 断言equals。
        * @param 3 3
        * @param users.size( 用户.大小(
         */
        assertEquals(3, users.size(), "@DataScope(enabled=false) 应禁用数据权限过滤");
    }

    /** 全部 数据权限：返回全部数据 */
    @Test
    void allPermissionSeesAll() {
        DataScopeAuthService.setCurrentUser(user(DataFilterTypeEnum.ALL, "10", Set.of("USER")));
        List<SysUser> users = sysUserMapper.selectByScope();
        /**
        * 断言equals。
        * @param 3 3
        * @param users.size( 用户.大小(
         */
        assertEquals(3, users.size(), "ALL 数据权限应看到全部数据");
    }
}
