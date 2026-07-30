package com.chua.starter.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.entity.project.SysSmsTemplate;
import com.chua.starter.project.pojo.sms.SmsPageV1Request;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 短信模板
 *
 * @author CH
 */
@Mapper
public interface SysSmsTemplateMapper extends BaseMapper<SysSmsTemplate> {
    /**
     * 分页
     *
     * @param page 分页
     * @param request 请求
     * @return 分页
     */
    IPage<SysSmsTemplate> pageFor(@Param("page")Page<SysSmsTemplate> page,
                                  @Param("request") SmsPageV1Request request);
}