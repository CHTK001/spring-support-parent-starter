package com.chua.shell.support.shell.adaptor;

import com.chua.common.support.utils.IoUtils;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * 解析器
 *
 * @author CH
 */
public class JsonCommandAdaptor implements CommandAdaptor {


    @Override
    public String handler(String file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return "@json " + IoUtils.toString(fis, "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }
}
