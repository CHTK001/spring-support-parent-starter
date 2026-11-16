package com.chua.starter.datasearch.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 峰谷定价时段。
 *
 * <p>时间用「当天第几分钟」表示，避免时区字符串解析差异；{@code timezone} 只用于
 * 向用户说明这个窗口按哪个时区解释（如 {@code UTC} / {@code Asia/Shanghai}）。
 * 星期按 1=周一 ... 7=周日；空列表表示每天。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceWindow implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 时区（IANA 名称或 UTC）
     */
    private String timezone;

    /**
     * 生效星期（1=周一 ... 7=周日）；空 = 每天
     */
    private List<Integer> weekdays;

    /**
     * 开始分钟（0-1439，含）
     */
    private Integer startMinute;

    /**
     * 结束分钟（0-1440，不含）
     */
    private Integer endMinute;
}
