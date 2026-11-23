package com.chua.starter.account.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传的配置文件（解析入口用）。
 *
 * <p>前端统一以 base64 传输文件字节（SQLite 等二进制文件必须走字节，
 * 文本粘贴由前端 UTF-8 编码后同样以 base64 传递），后端按需解码。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChannelImportFile {

    /** 文件名（仅用于展示与按名查找，如 auth.json / config.toml） */
    @JsonProperty("name")
    private String name = "";

    /** 文件字节 */
    @JsonProperty("bytes")
    private byte[] bytes = new byte[0];
}
