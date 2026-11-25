package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.CouponTemplateDTO;
import com.chua.payment.support.dto.CouponTemplateQueryDTO;
import com.chua.payment.support.vo.CouponTemplateVO;

/**
 * 优惠券模板服务接口，定义模板的增删改查契约。
 *
 * @author CH
 * @since 4.0.0
 */
public interface CouponTemplateService {

    /**
     * 创建优惠券模板
     *
     * @param dto 模板参数
     * @return 创建后的模板视图对象
     */
    CouponTemplateVO create(CouponTemplateDTO dto);

    /**
     * 更新优惠券模板
     *
     * @param id  模板标识
     * @param dto 模板参数
     * @return 更新后的模板视图对象
     */
    CouponTemplateVO update(Long id, CouponTemplateDTO dto);

    /**
     * 删除优惠券模板（逻辑删除）
     *
     * @param id 模板标识
     * @return 是否删除成功
     */
    boolean delete(Long id);

    /**
     * 查询优惠券模板详情
     *
     * @param id 模板标识
     * @return 模板视图对象
     */
    CouponTemplateVO get(Long id);

    /**
     * 分页查询优惠券模板
     *
     * @param dto 查询参数
     * @return 模板分页结果
     */
    Page<CouponTemplateVO> page(CouponTemplateQueryDTO dto);
}
