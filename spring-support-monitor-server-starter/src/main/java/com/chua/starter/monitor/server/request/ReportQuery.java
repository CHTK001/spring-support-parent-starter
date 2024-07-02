package com.chua.starter.monitor.server.request;

import com.chua.common.support.protocol.protocol.CommandType;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author CH
 * @since 2024/7/2
 */
@NoArgsConstructor
@Data
public class ReportQuery {


    @SerializedName("moduleType")
    private String moduleType;
    @SerializedName("commandType")
    private CommandType commandType;
    @SerializedName("appName")
    private String appName;
    @SerializedName("subscribeAppName")
    private String subscribeAppName;
    @SerializedName("profile")
    private String profile;
    @SerializedName("content")
    private String content;
    @SerializedName("profileName")
    private String profileName;
}
