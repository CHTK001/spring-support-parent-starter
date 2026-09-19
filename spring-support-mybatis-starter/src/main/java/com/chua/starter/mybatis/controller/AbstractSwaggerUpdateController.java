package com.chua.starter.mybatis.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.google.common.base.Splitter;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.common.support.lang.code.ReturnResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Set;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * Swagger更新控制器基类
 * 提供通用的增删改查功能
 *
 * @param <S> 服务类型
 * @param <T> 实体类型
 * @author CH
 * @since 4.0.0
 */
public abstract class AbstractSwaggerUpdateController<S extends IService<T>, T> {

    /**
     * 事务模板，用于执行事务操作
     */
    @Autowired(required = false)
    /** Transaction模板 */
    protected TransactionTemplate transactionTemplate;

    /**
    * 根据主键删除数据
    *
    * @param id 页码
    * @return 分页结果
    * @param "主键" "主键"
    * @param "主键" "主键"
    * @param "主键" "主键"
    */
    @ResponseBody
    @Operation(summary = "删除数据")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(@Parameter(name = "主键") String id) {
        if(null == id) {
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), "主键不能为空");
        }

        Set<String> ids = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(id).stream().collect(java.util.stream.Collectors.toSet());
        if(ids.isEmpty()) {
            return ReturnResult.success();
        }

        if(ids.size() == 1) {
            return ReturnResult.of(getService().removeById(ids.iterator().next()));
        }

        return ReturnResult.of(getService().removeBatchByIds(ids));
    }

    /**
     * 批量保存或更新数据
     *
     * @param t            实体列表
     * @param bindingResult 绑定结果
     * @return 操作结果
     * @param @Validated(UpdateGroup.class @校验(更新群体.类
     * @param @Validated(UpdateGroup.class @校验(更新群体.类
     * @param @Validated(UpdateGroup.class @Validated(UpdateGroup.class
     */
    @ResponseBody
    @Operation(summary = "更新/保存数据")
    @PutMapping("saveOrUpdateBatch")
    public ReturnResult<Boolean> saveOrUpdateBatch(@Validated(UpdateGroup.class) @RequestBody List<T> t, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            var allErrors = bindingResult.getAllErrors();
            String errorMessage = allErrors.isEmpty() ? "参数验证失败" : allErrors.getFirst().getDefaultMessage();
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), errorMessage);
        }

        return ReturnResult.of(getService().saveOrUpdateBatch(t));
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     * @param @Validated(UpdateGroup.class @校验(更新群体.类
     * @param @Validated(UpdateGroup.class @校验(更新群体.类
     * @param @Validated(UpdateGroup.class @Validated(UpdateGroup.class
     * @param bindingResult binding结果，不允许为 null
     */
    @ResponseBody
    @Operation(summary = "更新/保存数据")
    @PutMapping("saveOrUpdate")
    public ReturnResult<Boolean> saveOrUpdate(@Validated(UpdateGroup.class) @RequestBody T t, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) { return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage()); }


        if (transactionTemplate == null) {
            boolean saveOrUpdate = getService().saveOrUpdate(render(t));
            if (!saveOrUpdate) {
                return ReturnResult.error("保存失败");
            }
            /**
             * 保存或更新之后。
             * @param t t
             */
            saveOrUpdateAfter(t);
            return ReturnResult.of(saveOrUpdate);
        }

        return transactionTemplate.execute(it -> {
            boolean saveOrUpdate = getService().saveOrUpdate(render(t));
            if (!saveOrUpdate) {
                throw new RuntimeException("保存失败");
            }
            /**
             * 保存或更新之后。
             * @param t t
             */
            saveOrUpdateAfter(t);
            return ReturnResult.of(saveOrUpdate);
        });
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     * @param @Validated(UpdateGroup.class @校验(更新群体.类
     * @param @Validated(UpdateGroup.class @校验(更新群体.类
     * @param @Validated(UpdateGroup.class @Validated(UpdateGroup.class
     * @param bindingResult binding结果，不允许为 null
     */
    @ResponseBody
    @Operation(summary = "更新数据")
    @PutMapping("update")
    public ReturnResult<Boolean> updateById(@Validated(UpdateGroup.class) @RequestBody T t, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) { return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage()); }

        return ReturnResult.of(getService().updateById(render(t)));
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     * @param @Validated(AddGroup.class @校验(添加群体.类
     * @param @Validated(AddGroup.class @校验(添加群体.类
     * @param @Validated(AddGroup.class @Validated(AddGroup.class
     * @param bindingResult binding结果，不允许为 null
     */
    @ResponseBody
    @Operation(summary = "添加数据")
    @PostMapping("save")
    public ReturnResult<T> save(@Validated(AddGroup.class) @RequestBody T t, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) { return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage()); }
        /**
         * 获取服务。
         */
        getService().save(render(t));
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

    /**
     * 保存或更新后
     *
     * @param t 实体
     */
    public void saveOrUpdateAfter(T t) {
    }
}
