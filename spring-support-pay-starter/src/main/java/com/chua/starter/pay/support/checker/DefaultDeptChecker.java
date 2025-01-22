package com.chua.starter.pay.support.checker;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.pay.support.pojo.PayOrderRequest;

/**
 * 部门校验
 *
 * @author CH
 */
@SpiDefault
public class DefaultDeptChecker implements DeptChecker{
    @Override
    public ReturnResult<String> check(PayOrderRequest request) {
        if(!request.isCheckDept()) {
            return ReturnResult.SUCCESS;
        }
        if(StringUtils.isEmpty(request.getDeptId())) {
            return ReturnResult.error("机构编码不能为空");
        }

        if(StringUtils.isEmpty(request.getDeptName())) {
            return ReturnResult.error("机构名称不能为空");
        }
        return ReturnResult.SUCCESS;
    }
}
