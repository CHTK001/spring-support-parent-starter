package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.PageRequest;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 超类
 *
 * @author CH
 */
public abstract class AbstractSwaggerController<S extends IService<T>, T> extends AbstractSwaggerUpdateController<S, T>{

    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<T> page(PageRequest<T> page, T entity) {
        return PageResultUtils.ok(getService().page(page.createPage(), Wrappers.lambdaQuery(entity)));
    }
    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "查询基础数据")
    @GetMapping("list")
    public ReturnResult<List<T>> list( T entity) {
        return ReturnResult.ok(getService().list( Wrappers.lambdaQuery(entity)));
    }

}
