package com.chua.starter.job.support.manage;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.job.support.dispatch.JobQuery;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.manage.dto.JobScheduleDTO;
import com.chua.starter.job.support.manage.dto.JobSpecDTO;
import com.chua.starter.job.support.manage.dto.JobStatusDTO;
import com.chua.starter.job.support.manage.vo.JobRuntimeVO;
import com.chua.starter.job.support.operate.JobOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 任务治理外部接口 (HTTP)。
 *
 * <p>与进程内 {@link JobOperator} 同一套语义, 只多做一层入出参转换。调度配置落库后立即失效
 * 触发器缓存, 因此改期/启停无需重启。</p>
 *
 * <p>{@code sys_job} 没有归属列, 任务是全局资源, 故整组出口按管理员权限收口;
 * 业务侧面向用户的任务页应走各自的归属接口 (如采集任务走 {@code /v2/edu/collect/**}),
 * 由业务服务在内部委托本治理接口, 不要放开本组地址给普通登录用户。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/job")
@RequiredArgsConstructor
@Permission("admin")
@Tag(name = "任务治理")
public class JobManageController {

    /**
     * 任务治理接口
     */
    private final JobOperator jobOperator;

    /**
     * 分页查询任务运行态。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询任务")
    @GetMapping("/page")
    public ReturnResult<PageResult<JobRuntimeVO>> page(JobQuery query) {
        PageResult<JobRuntime> source = jobOperator.page(query);
        List<JobRuntimeVO> rows = new ArrayList<>();
        if (source.getData() != null) {
            for (JobRuntime runtime : source.getData()) {
                rows.add(JobRuntimeVO.of(runtime));
            }
        }
        return ReturnResult.ok(PageResult.<JobRuntimeVO>builder()
                .pageNo(source.getPageNo())
                .pageSize(source.getPageSize())
                .totalPages(source.getTotalPages())
                .total(source.getTotal())
                .data(rows)
                .build());
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
        List<JobRuntimeVO> rows = new ArrayList<>();
        for (JobRuntime runtime : jobOperator.list(group)) {
            rows.add(JobRuntimeVO.of(runtime));
        }
        return ReturnResult.ok(rows);
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
        JobRuntimeVO vo = JobRuntimeVO.of(jobOperator.runtime(jobKey));
        return vo == null ? ReturnResult.error("任务不存在: " + jobKey) : ReturnResult.ok(vo);
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
        return execute("save", dto == null ? null : dto.getJobKey(),
                () -> jobOperator.declare(dto.toSpec()));
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
        return execute("schedule", dto == null ? null : dto.getJobKey(),
                () -> jobOperator.updateSchedule(dto.getJobKey(), dto.getScheduleType(), dto.getScheduleTime()));
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
        if (dto == null || dto.getEnabled() == null) {
            return ReturnResult.illegal("enabled 不能为空");
        }
        boolean enabled = dto.getEnabled();
        return execute("status", dto.getJobKey(), () -> jobOperator.changeStatus(dto.getJobKey(), enabled));
    }

    /**
     * 立即派发一次 (停用状态同样可用)。
     *
     * @param jobKey 任务键
     * @return 是否已投递
     */
    @Operation(summary = "立即派发一次")
    @PostMapping("/trigger")
    public ReturnResult<Boolean> trigger(@RequestParam String jobKey) {
        return execute("trigger", jobKey, () -> jobOperator.trigger(jobKey));
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
        return execute("remove", jobKey, () -> jobOperator.remove(jobKey));
    }

    /**
     * 统一执行入口: 校验任务键、把非法调度配置等参数问题转成可读提示。
     *
     * @param action 动作名 (仅用于日志)
     * @param jobKey 任务键
     * @param call   治理调用
     * @return 结果
     */
    private ReturnResult<Boolean> execute(String action, String jobKey, BooleanSupplier call) {
        if (jobKey == null || jobKey.isBlank()) {
            return ReturnResult.illegal("jobKey 不能为空");
        }
        try {
            return call.getAsBoolean()
                    ? ReturnResult.ok(true)
                    : ReturnResult.error("任务不存在或未被更新: " + jobKey);
        } catch (IllegalArgumentException ex) {
            return ReturnResult.illegal(ex.getMessage());
        } catch (Exception ex) {
            log.error("[job] 治理接口执行失败 action={} job={}", action, jobKey, ex);
            return ReturnResult.error("操作失败: " + ex.getMessage());
        }
    }
}
