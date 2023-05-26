package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.mybatis.entity.DelegatePage;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import springfox.documentation.annotations.ApiIgnore;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * 超类
 *
 * @author CH
 */
public abstract class AbstractBaseController<S extends IService<T>, T> {

    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    public ResultData<Page<T>> page(DelegatePage<T> page, @Valid T entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(1, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(getService().page(page.createPage(), Wrappers.lambdaQuery(entity)));
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @GetMapping("delete/{id}")
    public ResultData<Boolean> delete(@PathVariable("id") String id) {
        if (null == id) {
            return ResultData.failure(1, "主键不能为空");
        }
        return ResultData.success(getService().removeById(id));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("update")
    public ResultData<Boolean> updateById(@Valid @RequestBody T t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(1, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(getService().updateById(t));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("save")
    public ResultData<Boolean> save(@Valid @RequestBody T t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResultData.failure(1, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ResultData.success(getService().save(t));
    }

    abstract public S getService();
}
