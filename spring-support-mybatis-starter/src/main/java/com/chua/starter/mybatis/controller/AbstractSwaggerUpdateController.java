package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.result.ResultData;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 超类
 *
 * @author CH
 */
public abstract class AbstractSwaggerUpdateController<S extends IService<T>, T> {


    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "删除数据")
    @DeleteMapping("delete")
    public ResultData<Boolean> delete(@Parameter(name = "主键") String id) {
        if(null == id) {
            return ResultData.failure(REQUEST_PARAM_ERROR,  "主键不能为空");
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
    @Operation(summary = "更新数据")
    @PutMapping("update")
    public ResultData<Boolean> updateById(@Validated(UpdateGroup.class) @RequestBody T t , @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ResultData.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
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
    @Operation(summary = "添加数据")
    @PostMapping("save")
    public ResultData<Boolean> save(@Validated(AddGroup.class) @RequestBody T t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ResultData.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(getService().save(t));
    }


    abstract public S getService();
}
