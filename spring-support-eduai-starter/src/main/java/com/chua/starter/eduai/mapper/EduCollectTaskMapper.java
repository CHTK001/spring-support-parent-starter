package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduCollectTask;
import com.chua.starter.eduai.model.dto.CollectTaskQueryDTO;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 题库采集任务 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduCollectTaskMapper extends BaseMapper<EduCollectTask> {

    /**
     * 分页查询采集任务（XML 语句 pageSelect）。
     *
     * @param page  分页参数
     * @param query 查询条件
     * @param scope 归属隔离参数
     * @return 任务分页
     */
    IPage<EduCollectTask> pageSelect(Page<EduCollectTask> page, @Param("query") CollectTaskQueryDTO query,
                                      @Param("scope") EduUserContext.OwnerScope scope);

    /**
     * 采集任务执行完成后回写计量列（XML 语句 writeBackRunMetrics，累计题数在库内自增）。
     *
     * @param taskId        采集任务标识
     * @param startTime     本次运行开始时刻
     * @param lastRunStatus 末次运行状态（1=成功, 0=失败）
     * @param lastRunCount  末次运行入库题数
     * @param savedCount    本次入库题数（累计列增量）
     * @return 更新行数
     */
    int writeBackRunMetrics(@Param("taskId") Long taskId,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("lastRunStatus") int lastRunStatus,
                            @Param("lastRunCount") int lastRunCount,
                            @Param("savedCount") int savedCount);
}
