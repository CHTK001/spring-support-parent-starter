package com.chua.starter.common.support.debounce;

import com.chua.common.support.lang.code.ReturnResult;

/**
 * 返回结果异常
 * @author CH
 * @since 2024/12/20
 */
public class ReturnResultDebounceException implements DebounceException{
    @Override
    public Object returnValue() {
        return ReturnResult.fail("操作频繁, 请勿重复操作");
    }
}
