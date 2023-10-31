package com.chua.starter.device.support.adaptor.factory.client.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author CH
 */
@NoArgsConstructor
@Data
public class HikAnFangOrgListResult {

    @JsonProperty("total")
    private Integer total;
    @JsonProperty("pageNo")
    private Integer pageNo;
    @JsonProperty("pageSize")
    private Integer pageSize;
    @JsonProperty("list")
    private List<ListDTO> list;

    @NoArgsConstructor
    @Data
    public static class ListDTO {
        @JsonProperty("orgIndexCode")
        private String orgIndexCode;
        @JsonProperty("organizationCode")
        private String organizationCode;
        @JsonProperty("orgName")
        private String orgName;
        @JsonProperty("orgPath")
        private String orgPath;
        @JsonProperty("parentOrgIndexCode")
        private String parentOrgIndexCode;
        @JsonProperty("available")
        private Boolean available;
        @JsonProperty("leaf")
        private Boolean leaf;
        @JsonProperty("sort")
        private Integer sort;
        @JsonProperty("createTime")
        private String createTime;
        @JsonProperty("updateTime")
        private String updateTime;
    }
}
