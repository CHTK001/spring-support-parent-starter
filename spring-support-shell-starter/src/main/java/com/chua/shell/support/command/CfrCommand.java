package com.chua.shell.support.command;

import com.chua.common.support.file.Decompress;
import com.chua.common.support.shell.ShellMapping;
import com.chua.common.support.shell.ShellParam;
import com.chua.common.support.shell.ShellResult;
import com.chua.common.support.utils.ClassUtils;
import com.chua.shell.support.CfrDecompiler;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;

/**
 * cfr命令
 *
 * @author CH
 */
@Slf4j
public class CfrCommand {

    /**
     * help
     *
     * @return help
     */
    @ShellMapping(value = {"jad"}, describe = "反编译命令")
    public ShellResult cfr(
            @ShellParam(value = "file", numberOfArgs = 2, describe = "执行spring对象", example = {"spring -f xxx: 反编译"}) String file) {
        if(null != file) {
            Class<?> aClass = ClassUtils.forName(file);
            if (null != aClass) {
                log.info("检测到反编译类文件");
                URL location = aClass.getProtectionDomain().getCodeSource().getLocation();
                Decompress decompress = new CfrDecompiler();

            }
        }
        return ShellResult.text("");
    }
}
