package com.chua.starter.server.support.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerUserView {
    private String username;
    private Integer uid;
    private Integer gid;
    private String homeDir;
    private String shell;
    private String fullName;
    private Boolean enabled;
    private List<String> groups;
    private String lastLogon;
    private String description;
}
