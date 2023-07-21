package com.chua.starter.oss.support.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 上传
 * @author CH
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UploadResult {
    /**
     * 原始图片
     */
    private String name;
    /**
     * 状态
     */
    private String code;
    /**
     * 成功的远程路径
     */
    private String remote;
}
