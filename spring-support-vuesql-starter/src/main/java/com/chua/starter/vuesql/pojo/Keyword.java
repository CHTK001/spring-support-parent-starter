package com.chua.starter.vuesql.pojo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 *
 * @author CH
 */
@Data
@Builder
public class Keyword {

    private String text;
    private List<ColumnKeyword> columns;

    @Data
    @Builder
    public static class ColumnKeyword {

        private String displayText;
        private String text;
    }
}
