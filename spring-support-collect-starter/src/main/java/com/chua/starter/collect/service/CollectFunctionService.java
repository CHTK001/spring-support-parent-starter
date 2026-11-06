package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.dto.CollectFunctionTestDTO;
import com.chua.starter.collect.engine.spi.CollectFunctionDef;
import com.chua.starter.collect.entity.CollectFunction;
import com.chua.starter.collect.vo.CollectFunctionTestVO;

import java.util.List;

/**
 * 采集自定义函数服务。
 *
 * <p>负责函数登记的增删改查、保存前的编译与命名校验，以及把启用的函数交给引擎侧的
 * {@code CollectFunctionProvider} 扩展点。写入后立即让注册表缓存失效，改完就能在流程里用到。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectFunctionService extends IService<CollectFunction> {

    /**
     * 函数列表。
     *
     * @param keyword 名称或说明关键字，为空返回全部
     * @return 函数列表
     */
    List<CollectFunction> listFunctions(String keyword);

    /**
     * 新增函数。
     *
     * @param function 函数定义
     * @return 保存后的函数
     */
    CollectFunction addFunction(CollectFunction function);

    /**
     * 修改函数。
     *
     * @param function 函数定义
     * @return 是否成功
     */
    boolean editFunction(CollectFunction function);

    /**
     * 删除函数。
     *
     * @param functionId 函数ID
     * @return 是否成功
     */
    boolean deleteFunction(Integer functionId);

    /**
     * 按实参试跑一个函数，结果写进该函数的最近结果。
     *
     * @param dto 试跑入参
     * @return 试跑结果
     */
    CollectFunctionTestVO testFunction(CollectFunctionTestDTO dto);

    /**
     * 当前启用的函数，供引擎侧装载。
     *
     * @return 函数清单
     */
    List<CollectFunctionDef> runtimeFunctions();
}
