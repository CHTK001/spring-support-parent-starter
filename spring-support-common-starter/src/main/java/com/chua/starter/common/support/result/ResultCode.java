package com.chua.starter.common.support.result;

import static com.chua.starter.common.support.result.ReturnCode.*;

/**
 * @author haoxr
 **/
public interface ResultCode {
    /**
     * 转化编码
     * @param status 编码
     * @return 结果
     */
    static String transferHttpCode(Integer status) {
        if(status == 200) {
            return OK.getCode();
        }
        if(status == 403) {
            return RESULT_ACCESS_UNAUTHORIZED.getCode();
        }

        if(status.toString().startsWith("40")) {
            return PARAM_ERROR.getCode();
        }

        return SERVER_ERROR.getCode();
    }

    String getCode();

    String getMsg();

}
