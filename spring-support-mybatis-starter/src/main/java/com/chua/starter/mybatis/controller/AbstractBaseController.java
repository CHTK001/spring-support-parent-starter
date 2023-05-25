package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ResultData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;

/**
 * 超类
 *
 * @author CH
 */
public abstract class AbstractBaseController<S extends IService<T>, T> {

    private final S service = getService();

    /**
     * 分页查询数据
     *
     * @param page   页码
     * @param entity 结果
     * @return 分页结果
     */
    @GetMapping("page")
    public ResultData<Page<T>> page(Page<T> page, T entity) {
        return ResultData.success(service.page(page, Wrappers.lambdaQuery(entity)));
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @GetMapping("delete/{id}")
    public ResultData<Boolean> delete(@PathVariable("id") String id) {
        return ResultData.success(service.removeById(id));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("update")
    public ResultData<Boolean> updateById(@RequestBody T t) {
        return ResultData.success(service.updateById(t));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @PostMapping("save")
    public ResultData<Boolean> save(@RequestBody T t) {
        return ResultData.success(service.save(t));
    }

    abstract public S getService();
}
