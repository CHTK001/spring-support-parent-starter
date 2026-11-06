package com.chua.starter.lowcode.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.lowcode.entity.LowFormVersion;
import com.chua.starter.lowcode.vo.LowFormVersionVO;

import java.util.List;

/**
 * 低代码-表单版本服务。
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface LowFormVersionService extends IService<LowFormVersion> {

    /**
     * 查询表单的所有版本。
     *
     * @param formId 表单ID
     * @return 版本列表
     */
    List<LowFormVersionVO> listByFormId(Integer formId);

    /**
     * 版本详情。
     *
     * @param versionId 版本ID
     * @return 版本
     */
    LowFormVersionVO detail(Integer versionId);
}
