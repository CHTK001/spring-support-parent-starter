package com.chua.starter.collect.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.collect.entity.CollectCategory;
import com.chua.starter.collect.vo.CollectCategoryVO;
import com.chua.starter.collect.vo.CollectOptionVO;

import java.util.List;

/**
 * 采集分类服务。
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectCategoryService extends IService<CollectCategory> {

    /**
     * 分类树。
     *
     * @return 树形分类列表
     */
    List<CollectCategoryVO> tree();

    /**
     * 分类下拉选项。
     *
     * @return 选项列表
     */
    List<CollectOptionVO> options();

    /**
     * 新增分类。
     *
     * @param category 分类
     * @return 保存后的分类
     */
    CollectCategory addCategory(CollectCategory category);

    /**
     * 修改分类。
     *
     * @param category 分类
     * @return 是否成功
     */
    boolean editCategory(CollectCategory category);

    /**
     * 删除分类，存在子分类或流程时拒绝。
     *
     * @param categoryId 分类ID
     * @return 是否成功
     */
    boolean deleteCategory(Integer categoryId);
}
