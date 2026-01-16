package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.validator.group.SelectGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
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
public abstract class AbstractSwaggerController<S extends IService<T>, T> extends AbstractSwaggerUpdateController<S, T>{

    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    /**
     * 分页查询数据
     *
     * @param page          分页参数
     * @param entity        查询实体
     * @param bindingResult 绑定结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<T> page(Query<T> page, @Validated(SelectGroup.class) T entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            var fieldError = bindingResult.getFieldError();
            String errorMessage = fieldError != null ? fieldError.getDefaultMessage() : "参数验证失败";
            return ReturnPageResult.error(errorMessage);
        }
        S service = getService();
        IPage<T> tPage = service.page(page.createPage(), createWrapper(entity));
        List<T> records = tPage.getRecords();
        if(CollectionUtils.isEmpty(records)) {
            return ReturnPageResult.ok();
        }
        pageAfter(records);
        return PageResultUtils.ok(tPage);
    }
    /**
     * 查询基础数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    /**
     * 查询基础数据
     *
     * @param entity        查询实体
     * @param bindingResult 绑定结果
     * @return 查询结果列表
     */
    @ResponseBody
    @Operation(summary = "查询基础数据")
    @GetMapping("list")
    public ReturnResult<List<T>> list(@Validated(SelectGroup.class) T entity, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            var fieldError = bindingResult.getFieldError();
            String errorMessage = fieldError != null ? fieldError.getDefaultMessage() : "参数验证失败";
            return ReturnResult.error(errorMessage);
        }
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
     * 分页查询后
     * @param page 分页
     */
    protected void pageAfter(List<T> page) {

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
