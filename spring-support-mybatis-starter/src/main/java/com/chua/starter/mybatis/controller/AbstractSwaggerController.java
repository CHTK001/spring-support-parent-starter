package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.starter.common.support.result.ResultData;
import com.chua.starter.mybatis.entity.RequestPage;
import com.chua.starter.mybatis.entity.ResultPage;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

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
    @ApiOperation("分页查询基础数据")
    @GetMapping("page")
    public ResultData<ResultPage<T>> page(RequestPage<T> page, T entity) {
        return ResultData.success(ResultPage.copyList(getService().page(page.createPage(), Wrappers.lambdaQuery(entity))));
    }

    /**
     * 根据主键删除数据
     *
     * @param id 页码
     * @return 分页结果
     */
    @ApiOperation("根据主键删除数据")
    @GetMapping("delete/{id}")
    public ResultData<Boolean> delete(@ApiParam("主键") @PathVariable("id") String id) {
        return ResultData.success(getService().removeById(id));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ApiOperation("根据主键更新数据")
    @PostMapping("update")
    public ResultData<Boolean> updateById(@RequestBody T t) {
        return ResultData.success(getService().updateById(t));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ApiOperation("上报数据")
    @PostMapping("save")
    public ResultData<Boolean> save(@RequestBody T t) {
        return ResultData.success(getService().save(t));
    }


    abstract public S getService();
}
