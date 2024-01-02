package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.mybatis.entity.PageRequest;
import com.chua.starter.mybatis.entity.PageResult;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static com.chua.common.support.lang.code.ReturnCode.PARAM_ERROR;

/**
 * 超类
 *
 * @author CH
 */
public abstract class AbstractSwaggerController<S extends IService<T>, T> {

    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ResultData<PageResult<T>> page(PageRequest<T> page, @Valid T entity, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(PageResult.copy(getService().page(page.createPage(), Wrappers.lambdaQuery(entity))));
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
     @Operation(summary = "根据主键删除数据")
    @GetMapping("delete/{id}")
    public ResultData<Boolean> delete(@Parameter(name = "主键") @PathVariable("id") String id) {
        if(null == id) {
            return ResultData.failure(PARAM_ERROR,  "主键不能为空");
        }

        return ResultData.success(getService().removeById(id));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
     @Operation(summary = "根据主键更新数据")
    @PostMapping("update")
    public ResultData<Boolean> updateById(@Valid @RequestBody T t , @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(getService().updateById(t));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
     @Operation(summary = "上报数据")
    @PostMapping("save")
    public ResultData<Boolean> save(@Valid @RequestBody T t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ResultData.failure(PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(getService().save(t));
    }


    abstract public S getService();
}
