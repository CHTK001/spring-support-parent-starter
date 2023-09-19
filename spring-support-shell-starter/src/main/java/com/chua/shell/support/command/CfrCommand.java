package com.chua.shell.support.command;

import com.chua.common.support.lang.compile.Decompiler;
import com.chua.common.support.shell.ShellMapping;
import com.chua.common.support.shell.ShellMode;
import com.chua.common.support.shell.ShellParam;
import com.chua.common.support.shell.ShellResult;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.shell.support.CfrDecompiler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * cfr命令
 *
 * @author CH
 */
@Slf4j
public class CfrCommand {

    /**
     * jad
     *
     * @return jad
     */
    @ShellMapping(value = {"jad"}, describe = "反编译命令")
    public ShellResult cfr(
            @ShellParam(value = "file", describe = "反编译命令", example = {"jad -f xxx: 反编译"}) String file) {
        if (null != file) {
            Class<?> aClass = ClassUtils.forName(file);
            if (null != aClass) {
                log.info("检测到反编译类文件");
                URL location = aClass.getProtectionDomain().getCodeSource().getLocation();
                Decompiler decompiler = new CfrDecompiler();
                File file1 = new File("cfr", System.nanoTime() + ".java");
                try (InputStream is = new URL(location.toExternalForm() + aClass.getTypeName().replace(".", "/") + ".class").openStream()) {
                    FileUtils.copyFile(is, file1);
                    String decompile = decompiler.decompile(file1.getAbsolutePath(), null, true, true);
                    return ShellResult.builder().mode(ShellMode.CODE).result(decompile).build();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        FileUtils.forceDelete(file1);
                    } catch (IOException ignored) {
                    }
                }

            }
        }
        return ShellResult.text("");
    }
}
