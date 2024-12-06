package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

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
    public ReturnResult<Boolean> delete(@Parameter(name = "主键") String id) {
        if(null == id) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR,  "主键不能为空");
        }

        Set<String> ids = Splitter.on(",").trimResults().omitEmptyStrings().splitToSet(id);
        if(ids.isEmpty()) {
            return ReturnResult.ok();
        }

        if(ids.size() == 1) {
            return ReturnResult.of(getService().removeById(ids.iterator().next()));
        }

        return ReturnResult.of(getService().removeBatchByIds(ids));
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
    public ReturnResult<Boolean>updateById(@Validated(UpdateGroup.class) @RequestBody T t , @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        return ReturnResult.of(getService().updateById(t));
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
    public ReturnResult<T> save(@Validated(AddGroup.class) @RequestBody T t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        getService().save(t);
        return ReturnResult.ok(t);
    }


    /**
     * 获取服务的方法。
     * 这是一个抽象方法，需要在具体实现类中进行定义。
     *
     * @return S 返回服务的实例。这里的S是泛型，代表了服务的类型。
     */
    abstract public S getService();
    /**
     * 渲染实体
     *
     * @param t 实体
     * @return 分页结果
     */
    public T render(T t) {
        return t;
    }
}
