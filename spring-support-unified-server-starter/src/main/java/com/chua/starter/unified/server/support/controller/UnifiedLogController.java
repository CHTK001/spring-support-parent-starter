package com.chua.starter.unified.server.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.starter.mybatis.entity.DelegatePage;
import com.chua.starter.unified.server.support.entity.UnifiedLog;
import com.chua.starter.unified.server.support.service.UnifiedLogService;
import lombok.AllArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

import static com.chua.common.support.lang.code.ReturnCode.PARAM_ERROR;

/**
 * 配置中心接口
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/log")
@AllArgsConstructor
public class UnifiedLogController {

    private final UnifiedLogService unifiedLogService;


    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    @ResponseBody
    public ReturnPageResult<Page<UnifiedLog>> page(DelegatePage<UnifiedLog> page, @Valid UnifiedLog entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnPageResult.illegal(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ReturnPageResult.ok(unifiedLogService.page(page.createPage(), Wrappers.lambdaQuery(entity)
                .orderByDesc(UnifiedLog::getCreateTime, UnifiedLog::getUnifiedLogId)
        ));
    }


}
