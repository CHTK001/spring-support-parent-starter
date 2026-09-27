package com.chua.starter.job.support.manage;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.job.support.dispatch.JobQuery;
import com.chua.starter.job.support.log.JobLogQuery;
import com.chua.starter.job.support.manage.dto.JobScheduleDTO;
import com.chua.starter.job.support.manage.dto.JobSpecDTO;
import com.chua.starter.job.support.manage.dto.JobStatusDTO;
import com.chua.starter.job.support.manage.vo.JobExecutorVO;
import com.chua.starter.job.support.manage.vo.JobNodeVO;
import com.chua.starter.job.support.manage.vo.JobLogVO;
import com.chua.starter.job.support.manage.vo.JobRuntimeVO;
import com.chua.starter.job.support.manage.vo.JobSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务治理外部接口 (HTTP, 平台管理员鉴权)。
 *
 * <p>与进程内 {@code JobOperator} 同一套语义, 实现全部落在 {@link JobGovernanceService},
 * 本类只做路径绑定。调度配置落库后立即失效触发器缓存, 因此改期/启停无需重启。</p>
 *
 * <p>{@code sys_job} 没有归属列, 任务是全局资源, 故整组出口按管理员权限收口;
 * 业务侧面向用户的任务页应走各自的归属接口, 由业务服务在内部委托治理实现。
 * 用不到统一认证的宿主 (如采集控制台自带会话) 不要放开本组地址, 改为自带一个
 * 带 {@code @Ignore} 的控制台出口复用同一治理实现, 见类注释中的先例。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/v2/job")
@Permission("admin")
@Tag(name = "任务治理")
public class JobManageController {

    /**
     * 治理实现
     */
    private final JobGovernanceService governance;

    /**
     * 分页查询任务运行态。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    public ReturnResult<PageResult<JobRuntimeVO>> page(JobQuery query) {
        return governance.page(query);
    }

    /**
     * 按分组列出任务运行态 (不分页)。
     *
     * @param group 分组, 为空列全部
     * @return 运行态列表
     */
    @Operation(summary = "按分组列出任务")
    @GetMapping("/list")
    public ReturnResult<List<JobRuntimeVO>> list(@RequestParam(required = false) String group) {
        return governance.list(group);
    }

    /**
     * 读取单个任务运行态。
     *
     * @param jobKey 任务键
     * @return 运行态
     */
    @Operation(summary = "任务详情")
    @GetMapping("/detail/{jobKey}")
    public ReturnResult<JobRuntimeVO> detail(@PathVariable String jobKey) {
        return governance.detail(jobKey);
    }

    /**
     * 登记或更新任务 (调度配置、启停一次写入)。
     *
     * @param dto 任务规格
     * @return 是否成功
     */
    @Operation(summary = "登记或更新任务")
    @PostMapping("/save")
    public ReturnResult<Boolean> save(@RequestBody JobSpecDTO dto) {
        return governance.save(dto);
    }

    /**
     * 修改调度配置并立即生效。
     *
     * @param dto 调度配置
     * @return 是否成功
     */
    @Operation(summary = "修改调度配置")
    @PutMapping("/schedule")
    public ReturnResult<Boolean> updateSchedule(@RequestBody JobScheduleDTO dto) {
        return governance.updateSchedule(dto);
    }

    /**
     * 启停自动调度。
     *
     * @param dto 启停入参
     * @return 是否成功
     */
    @Operation(summary = "启停自动调度")
    @PutMapping("/status")
    public ReturnResult<Boolean> changeStatus(@RequestBody JobStatusDTO dto) {
        return governance.changeStatus(dto);
    }

    /**
     * 立即派发一次 (停用任务会被拒绝, 需先启用; 与治理实现的把关口径一致, 避免绕过页面点起停用任务)。
     *
     * @param jobKey 任务键
     * @return 是否已投递
     */
    @Operation(summary = "立即派发一次")
    @PostMapping("/trigger")
    public ReturnResult<Boolean> trigger(@RequestParam String jobKey) {
        return governance.trigger(jobKey);
    }

    /**
     * 删除任务。
     *
     * @param jobKey 任务键
     * @return 是否成功
     */
    @Operation(summary = "删除任务")
    @DeleteMapping("/{jobKey}")
    public ReturnResult<Boolean> remove(@PathVariable String jobKey) {
        return governance.remove(jobKey);
    }

    /**
     * 分页查询任务执行日志 (按触发时刻倒序)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询执行日志")
    @GetMapping("/log/page")
    public ReturnResult<PageResult<JobLogVO>> pageLogs(JobLogQuery query) {
        return governance.pageLogs(query);
    }

    /**
     * 清理执行日志。
     *
     * @param retentionDays 保留天数, 0 表示清空全部, 负数会被拒绝 (全清必须显式传 0)
     * @return 删除条数
     */
    @Operation(summary = "清理执行日志")
    @PostMapping("/log/cleanup")
    public ReturnResult<Integer> cleanupLogs(@RequestParam(defaultValue = "30") int retentionDays) {
        return governance.cleanupLogs(retentionDays);
    }

    /**
     * 列出本进程已注册的执行器。
     *
     * @return 执行器清单, 按名称排序
     */
    @Operation(summary = "已注册执行器清单")
    @GetMapping("/executors")
    public ReturnResult<List<JobExecutorVO>> executors() {
        return governance.executors();
    }

    /**
     * 在线执行器节点清单（远程作业页）。
     *
     * @return 节点清单, 按首次登记时间升序
     */
    @Operation(summary = "在线执行器节点清单")
    @GetMapping("/nodes")
    public ReturnResult<List<JobNodeVO>> nodes() {
        return governance.nodes();
    }

    /**
     * 工作台概览: 本节点角色与选主态 + 任务与日志计数。
     *
     * @return 概览
     */
    @Operation(summary = "调度概览")
    @GetMapping("/summary")
    public ReturnResult<JobSummaryVO> summary() {
        return governance.summary();
    }
}
