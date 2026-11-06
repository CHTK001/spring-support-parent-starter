package com.chua.starter.mybatis.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.mybatis.properties.MybatisPlusDataScopeProperties;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.function.Supplier;

/**
   * 数据权限拦截器
   *
   * @author CH
   * @since 4.0.0
   */
@Slf4j
public class MybatisPlusDataPermissionInterceptor extends DataPermissionInterceptor {
      /**
       * Dept_leader
      */
    private static final String DEPT_LEADER = "DEPT_LEADER";
      /**
       * Managed_dept_树_标识
      */
    private static final String MANAGED_DEPT_TREE_IDS = "managedDeptTreeIds";

      /**
       * Methodsecurityinterceptor
      */
    private final MybatisPlusDataScopeProperties methodSecurityInterceptor;
      /**
       * 认证服务供应商
      */
    private final Supplier<AuthService> authServiceSupplier;

      /**
       * 当前线程正在解析登录用户，避免解析过程触发的嵌套查询重入数据权限处理
       */
    private static final ThreadLocal<Boolean> RESOLVING_USER = new ThreadLocal<>();

      /**
       * 创建 mybatisplus数据权限拦截器 实例
       * @param dataPermissionHandler 数据权限处理器
       * @param methodSecurityInterceptor mybatisplus数据scope属性
       * @param methodSecurityInterceptor 方法安全性拦截器
       */
    public MybatisPlusDataPermissionInterceptor(DataPermissionHandler dataPermissionHandler
        , MybatisPlusDataScopeProperties methodSecurityInterceptor) {
        this(dataPermissionHandler, methodSecurityInterceptor, () -> null);
    }

      /**
       * 创建 mybatisplus数据权限拦截器 实例
       * @param dataPermissionHandler 数据权限处理器
       * @param methodSecurityInterceptor 方法安全性拦截器
       * @param authServiceSupplier 认证服务供应商
       */
    public MybatisPlusDataPermissionInterceptor(DataPermissionHandler dataPermissionHandler,
                                                MybatisPlusDataScopeProperties methodSecurityInterceptor,
                                                Supplier<AuthService> authServiceSupplier) {
          /**
           * 父。
           * @param dataPermissionHandler 数据权限处理器
           */
        super(dataPermissionHandler);
        this.methodSecurityInterceptor = methodSecurityInterceptor;
        this.authServiceSupplier = authServiceSupplier;
    }

    @Override
      /**
       * 处理删除
       * @param delete 删除
       * @param index 整数
       * @param obj 对象
       * @param sql 字符串
       * @return 返回值
      */
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        if (!shouldApplyDataPermission()) {
            return;
        }
        super.processDelete(delete, index, sql, obj);
    }

    @Override
      /**
       * 处理更新
       * @param index 整数
       * @param obj 对象
       * @param sql 字符串
       * @param update 更新
       * @return 返回值
      */
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        if (!shouldApplyDataPermission()) {
            return;
        }
        super.processUpdate(update, index, sql, obj);
    }

    @Override
      /**
       * 处理选择
       * @param index 整数
       * @param obj 对象
       * @param select 参数
       * @param sql 字符串
       * @return 返回值
      */
    protected void processSelect(Select select, int index, String sql, Object obj) {
        if (!shouldApplyDataPermission()) {
            return;
        }
        super.processSelect(select, index, sql, obj);
    }

      /**
       * 查询前置处理：解析 SQL 并应用数据权限条件。
       *
       * <p>对 SQL 解析失败（如字段别名撞数据库保留字）做容错，
       * 跳过数据权限过滤而不阻断查询。</p>
       */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
            return;
        }
        PluginUtils.MPBoundSql mp = PluginUtils.mpBoundSql(boundSql);
        try {
            mp.sql(parserSingle(mp.sql(), ms.getId()));
        } catch (Exception e) {
            log.warn("[DataPermission] SQL 解析失败，跳过数据权限过滤: {}", e.getMessage());
        }
    }

      /**
       * 判断是否应该应用数据权限
       *
       * <p>解析当前用户可能触发业务 Mapper 查询，该查询会再次进入本拦截器，
       * 因此解析期间标记重入，嵌套查询不做数据权限过滤。</p>
       *
       * @return true-应用, false-不应用
       */
    private boolean shouldApplyDataPermission() {
          // 检查是否开启数据权限
        if (!methodSecurityInterceptor.isEnable()) {
            return false;
        }

          // 正在解析当前用户，嵌套查询不再解析，避免无限递归
        if (Boolean.TRUE.equals(RESOLVING_USER.get())) {
            return false;
        }

          // 获取当前用户
        RESOLVING_USER.set(Boolean.TRUE);
        CurrentUser currentUser;
        try {
            currentUser = getCurrentUser();
        } finally {
            RESOLVING_USER.remove();
        }
        if (ObjectUtils.isEmpty(currentUser)) {
            return false;
        }
        return true;
    }

      /**
       * 是否拥有managedDeptLeadScope
       *
       * @param currentUser 当前用户
       * @return 是否包含managedDeptLeadScope的结果
       */
    private boolean hasManagedDeptLeaderScope(CurrentUser currentUser) {
        return currentUser.hasRole(DEPT_LEADER)
                && null != currentUser.getExt()
                && null != currentUser.getExt().get(MANAGED_DEPT_TREE_IDS);
    }

      /**
       * 获取当前用户
       *
       * @return 获取当前用户的结果
       */
    private CurrentUser getCurrentUser() {
        if (authServiceSupplier == null) {
            return null;
        }
        AuthService authService = authServiceSupplier.get();
        return authService == null ? null : authService.getCurrentUser();
    }
}

