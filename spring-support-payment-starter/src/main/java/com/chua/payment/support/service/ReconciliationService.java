package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.ReconciliationDiffQueryDTO;
import com.chua.payment.support.dto.ReconciliationStatementQueryDTO;
import com.chua.payment.support.dto.ReconciliationTriggerDTO;
import com.chua.payment.support.vo.ReconciliationDiffVO;
import com.chua.payment.support.vo.ReconciliationStatementVO;

import java.util.List;

/**
 * 渠道对账服务：下载渠道账单、与本地支付流水比对、登记对账单与差异明细。
 *
 * @author CH
 * @since 4.0.0
 */
public interface ReconciliationService {

    /**
     * 发起渠道对账
     *
     * <p>按账单日期逐渠道下载渠道账单并与本地成功支付流水比对，幂等登记对账单与差异明细：
     * 同一（商户、渠道、账单日）重复对账时，先清除旧差异再重算，不产生重复差异。</p>
     *
     * @param dto 对账触发参数（商户/渠道/账单日期可过滤，账单日期缺省取昨日）
     * @return 本次对账生成的对账单视图列表（每条渠道一个）
     */
    List<ReconciliationStatementVO> reconcile(ReconciliationTriggerDTO dto);

    /**
     * 分页查询对账单
     *
     * @param dto 查询参数
     * @return 对账单分页
     */
    Page<ReconciliationStatementVO> pageStatements(ReconciliationStatementQueryDTO dto);

    /**
     * 分页查询对账差异明细
     *
     * @param dto 查询参数
     * @return 差异明细分页
     */
    Page<ReconciliationDiffVO> pageDiffs(ReconciliationDiffQueryDTO dto);

    /**
     * 按对账单号查询对账单
     *
     * @param statementNo 对账单号
     * @return 对账单视图
     */
    ReconciliationStatementVO getByStatementNo(String statementNo);

    /**
     * 查询对账单下的全部差异明细
     *
     * @param statementNo 对账单号
     * @return 差异明细列表
     */
    List<ReconciliationDiffVO> listDiffsByStatementNo(String statementNo);
}
