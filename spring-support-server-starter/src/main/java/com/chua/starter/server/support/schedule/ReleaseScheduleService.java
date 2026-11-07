package com.chua.starter.server.support.schedule;

import java.util.Map;

/**
 * 发布管理定时能力：把项目的「定时发布」与「Git 定时拉取」登记为 job-starter 任务。
 *
 * <p>该接口不依赖 job-starter，宿主未引入任务调度模块时实现类不会装配，
 * 控制器通过 {@code ObjectProvider} 可选调用，保证发布模块本身仍可独立运行。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface ReleaseScheduleService {

    /**
     * 同步单个项目的定时任务（先撤销旧任务，再按当前配置重新登记）。
     *
     * @param projectId 项目ID
     */
    void syncProject(Long projectId);

    /**
     * 移除项目的全部定时任务。
     *
     * @param projectId 项目ID
     */
    void removeProject(Long projectId);

    /**
     * 查询项目定时任务运行态（下一次触发时间等）。
     *
     * @param projectId 项目ID
     * @return 运行态；未启用时为空对象
     */
    Map<String, Object> status(Long projectId);

    /**
     * 启动后全量同步（应用重启后把库里已启用的定时任务重新登记）。
     */
    void syncAll();
}
