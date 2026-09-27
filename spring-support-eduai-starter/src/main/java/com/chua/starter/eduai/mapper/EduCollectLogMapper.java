package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduCollectLog;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 题库采集执行日志 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduCollectLogMapper extends BaseMapper<EduCollectLog> {

    /**
     * 分页查询采集执行日志（XML 语句 pageSelect）。
     *
     * @param page   分页参数
     * @param taskId 采集任务标识，为空时查全部
     * @param scope  归属隔离参数
     * @return 日志分页
     */
    IPage<EduCollectLog> pageSelect(Page<EduCollectLog> page, @Param("taskId") Long taskId,
                                    @Param("scope") EduUserContext.OwnerScope scope);
}
