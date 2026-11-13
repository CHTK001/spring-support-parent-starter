package com.chua.starter.monitor.support.spi.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.starter.monitor.support.model.EnvVarVO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /proc/<pid>/environ} 解析的真实单元测试。
 *
 * <p>环境变量只在 POSIX 通道上采得到，而这条解析规则是纯文本处理：
 * {@code KEY=VALUE} 用 NUL 分隔、值里允许出现等号、CRLF 与空行都要吃掉。
 * 把它放在单元测试里，就不用为一格属性页签去准备一台 Linux 主机。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("进程环境变量解析")
class PosixProcessEnvTest {

    @Test
    @DisplayName("按第一个等号切分，值里的等号原样保留")
    void keepsEqualsInsideValue() {
        List<EnvVarVO> rows = PosixHostOperations.parseEnviron("JAVA_OPTS=-Da=b\nPATH=/usr/bin:/bin\n");
        assertEquals(2, rows.size());
        EnvVarVO javaOpts = rows.stream().filter(r -> "JAVA_OPTS".equals(r.name())).findFirst().orElseThrow();
        assertEquals("-Da=b", javaOpts.value());
    }

    @Test
    @DisplayName("空行、无等号、缺变量名的行被跳过，不会产生脏数据")
    void skipsMalformedLines() {
        List<EnvVarVO> rows = PosixHostOperations.parseEnviron("\n====\nno-equals-here\n=orphan\nOK=1\n");
        assertEquals(1, rows.size());
        assertEquals("OK", rows.get(0).name());
        assertEquals("1", rows.get(0).value());
    }

    @Test
    @DisplayName("空值合法：VAR= 表示变量存在但为空")
    void allowsEmptyValue() {
        List<EnvVarVO> rows = PosixHostOperations.parseEnviron("EMPTY=\n");
        assertEquals(1, rows.size());
        assertEquals("", rows.get(0).value());
    }

    @Test
    @DisplayName("CRLF 换行与结果排序")
    void handlesCrlfAndSorts() {
        List<EnvVarVO> rows = PosixHostOperations.parseEnviron("b=2\r\na=1\r\n");
        assertEquals(List.of("a", "b"), rows.stream().map(EnvVarVO::name).toList());
        assertEquals("1", rows.get(0).value());
    }

    @Test
    @DisplayName("读不到（权限不足）时返回空表而不是抛异常")
    void emptyInputYieldsEmptyList() {
        assertTrue(PosixHostOperations.parseEnviron(null).isEmpty());
        assertTrue(PosixHostOperations.parseEnviron("").isEmpty());
        assertTrue(PosixHostOperations.parseEnviron("   ").isEmpty());
    }
}
