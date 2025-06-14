/*
 * Copyright (c) 2019 Of Him Code Technology Studio
 * Jpom is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 * 			http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.chua.report.client.starter.jpom.common.model;

/**
 * @author bwcx_jzy
 * @since 2020/3/21
 */
public enum AfterOpt implements BaseEnum {
    /**
     * 操作
     */
    No(0, "不做任何操作"),
    /**
     * 并发执行项目分发
     */
    Restart(1, "并发重启"),
    /**
     * 顺序执行项目分发
     */
    Order_Must_Restart(2, "完整顺序重启(有重启失败将结束本次)"),
    /**
     * 顺序执行项目分发
     */
    Order_Restart(3, "顺序重启(有重启失败将继续)"),
    ;
    private final int code;
    private final String desc;

    AfterOpt(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
