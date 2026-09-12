package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.MerchantDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.vo.MerchantVO;

/**
* 商户服务接口
*
* @since 2026-03-18
* @author CH
 */
public interface MerchantService {

    /**
    * 创建商户
    *
    * @param dto 商户DTO
    * @return 商户VO
     */
    MerchantVO createMerchant(MerchantDTO dto);

    /**
    * 更新商户
    *
    * @param id 商户标识
    * @param dto 商户DTO
    * @return 商户VO
     */
    MerchantVO updateMerchant(Long id, MerchantDTO dto);

    /**
    * 删除商户
    *
    * @param id 商户标识
    * @return 是否成功
     */
    boolean deleteMerchant(Long id);

    /**
    * 查询商户
    *
    * @param id 商户标识
    * @return 商户VO
     */
    MerchantVO getMerchant(Long id);

    /**
    * 分页查询商户列表
    *
    * @param page 页码
    * @param size 每页大小
    * @param merchantName 商户名称（可选）
    * @param status 状态（可选）
    * @return 分页结果
     */
    Page<MerchantVO> listMerchants(int page, int size, String merchantName, Integer status);

    /**
    * 激活商户
    *
    * @param id 商户标识
    * @return 是否成功
     */
    boolean activateMerchant(Long id);

    /**
    * 停用商户
    *
    * @param id 商户标识
    * @return 是否成功
     */
    boolean deactivateMerchant(Long id);
}
