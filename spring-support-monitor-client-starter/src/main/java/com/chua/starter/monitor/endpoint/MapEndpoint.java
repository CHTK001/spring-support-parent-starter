package com.chua.starter.monitor.endpoint;

import com.chua.common.support.constant.Projects;
import com.chua.common.support.utils.CmdUtils;
import com.chua.common.support.utils.StringUtils;
import lombok.Data;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import java.util.LinkedList;
import java.util.List;

/**
 * redis端点
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/02
 */

@WebEndpoint(id = "map")
public class MapEndpoint {
    @ReadOperation
    public List<JavaMap> read() {
        String exec = CmdUtils.exec("jmap -histo:live " + Projects.getPid() + " "+ (Projects.isLinux() ? " | head -n 20" : ""));

        List<JavaMap> javaMaps = new LinkedList<>();
        exec = StringUtils.subAfter(exec, "----------------------------------------------", true).trim();

        for (String line : exec.split("\n")) {
            try {
                JavaMap javaMap = new JavaMap();
                String[] split = line.trim().split("\\s+");
                if (split.length < 4) {
                    continue;
                }
                javaMap.setNum(Integer.parseInt(split[0].replace(":", "")));
                javaMap.setInstances(split[1]);
                javaMap.setBytes(Integer.parseInt(split[2]));
                javaMap.setClassName(split[3]);
                javaMaps.add(javaMap);
            } catch (NumberFormatException ignored) {
            }
        }
        return javaMaps;
    }

    /**
     * JavaMap类用于封装有关Java内存映射的相关信息。
     * 该类包含了一系列属性，用于描述内存映射的各个方面的信息。
     */
    @Data
    public static class JavaMap {

        /**
         * num属性表示内存映射的编号。
         * 该编号用于唯一标识不同的内存映射对象。
         */
        private Integer num;

        /**
         * instances属性表示内存映射对象的实例数量。
         * 通过该属性，可以了解某个内存映射对象有多少实际的实例存在。
         */
        private String instances;

        /**
         * bytes属性表示内存映射对象所占用的字节大小。
         * 通过该属性，可以了解到内存映射对象的大小，以字节为单位。
         */
        private Integer bytes;

        /**
         * className属性表示内存映射对象的类名称。
         * 通过该属性，可以了解到内存映射对象的具体类型，有助于进一步分析和处理。
         */
        private String className;
    }

}
