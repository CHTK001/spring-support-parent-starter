package com.chua.starter.job.support.log;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;

/**
 * 空日志查询实现：没有日志表时的默认档。
 *
 * <p>{@link JobLogRepository} 的全部方法都是带默认实现的只读口，本类一行覆盖都不写，
 * 因此返回空页 / 0 条 / 0 行清理 —— 治理页面在未接 MyBatis-Plus 的部署里照常可打开，
 * 只是列表为空，而不是抛「没有实现」。</p>
 *
 * <p>注册名 {@code none}，{@code noop} 是同义键；对偶的持久化实现在
 * {@code com.chua.starter.job.support.store.MybatisJobLogRepository}（由自动配置显式装配）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@SpiDefault
@Spi({"none", "noop"})
public class NoopJobLogRepository implements JobLogRepository {

    /**
     * 无状态单例。
     */
    public static final NoopJobLogRepository INSTANCE = new NoopJobLogRepository();

    /**
     * 创建空日志查询实现。
     */
    public NoopJobLogRepository() {
    }
}
