package com.chua.starter.project.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.project.support.entity.MsProjectDeployLog;
import org.apache.ibatis.annotations.Param;

/**
 * msprojectdeploy日志 映射器
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface MsProjectDeployLogMapper extends BaseMapper<MsProjectDeployLog> {

    /**
    * 删除指定时间之前创建的部署日志（保留期清理用）
    *
    * @param beforeTime 时间界限
    * @return 删除的行数
    */
    int deleteByTimeBefore(@Param("beforeTime") java.time.LocalDateTime beforeTime);
}
