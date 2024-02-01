package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ResultData;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.validation.BindingResult;
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
     @Operation(summary = "根据主键删除数据")
    @GetMapping("delete/{id}")
    public ResultData<Boolean> delete(@Parameter(name = "主键") @PathVariable("id") String id) {
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
     @Operation(summary = "根据主键更新数据")
    @PostMapping("update")
    public ResultData<Boolean> updateById(@Valid @RequestBody T t , @Ignore BindingResult bindingResult) {
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
     @Operation(summary = "上报数据")
    @PostMapping("save")
    public ResultData<Boolean> save(@Valid @RequestBody T t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ResultData.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(getService().save(t));
    }


    abstract public S getService();
}
