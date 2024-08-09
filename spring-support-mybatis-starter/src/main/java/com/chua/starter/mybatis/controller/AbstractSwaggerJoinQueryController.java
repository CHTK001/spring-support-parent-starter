package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.SelectGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 超类
 *
 * @author CH
 */
public abstract class AbstractSwaggerJoinQueryController<S extends IService<T>, T> extends AbstractSwaggerController<S, T>{

    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<T> page(Query<T> page, @Validated(SelectGroup.class)  T entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnPageResult.error();
        }
        S service = getService();
        Page<T> tPage = service.page(page.createPage(), createJoinWrapper(entity));
        return PageResultUtils.ok(tPage);
    }
    /**
     * 查询基础数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "查询基础数据")
    @GetMapping("list")
    public ReturnResult<List<T>> list(@Validated(SelectGroup.class)  T entity, BindingResult bindingResult) {
        return ReturnResult.ok(getService().list(createJoinWrapper(entity)));
    }

    /**
     * 查询详细信息
     *
     * @param entity entity
     * @return 详细信息
     */

    abstract public MPJLambdaWrapper<T> createJoinWrapper(T entity);
}
