package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
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
        S service = getService();
        Page<T> tPage = service.page(page.createPage(), createWrapper(entity));
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
    public ReturnResult<List<T>> list( T entity) {
        return ReturnResult.ok(getService().list( createWrapper(entity)));
    }

    /**
     * 创建查询条件
     * @param entity 实体
     * @return 查询条件
     */
    protected Wrapper<T> createWrapper(T entity) {
        return Wrappers.lambdaQuery(entity);
    }

    /**
     * 查询详细信息
     * @param id id
     * @return 结果
     */
    @Operation(summary = "查询详细信息")
    @GetMapping("get")
    public ReturnResult<T> get(String id) {
        if(StringUtils.isEmpty(id)) {
            return ReturnResult.error("数据不存在, 请刷新后重试");
        }
        return ReturnResult.ok(getService().getById(id));
    }

}
