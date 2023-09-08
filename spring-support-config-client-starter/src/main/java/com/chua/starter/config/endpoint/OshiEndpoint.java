package com.chua.starter.config.endpoint;

import com.chua.oshi.support.*;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

/**
 * oshi
 * @author CH
 */
@WebEndpoint(id = "oshi")
public class OshiEndpoint {
    private static final int OSHI_WAIT_SECOND = 1000;


    /**
     * cpu
     *
     * @return {@link Oshi}
     */
    @ReadOperation
    public Cpu cpu() {
        return Oshi.newCpu();
    }

    /**
     * mem
     *
     * @return {@link Mem}
     */
    @ReadOperation
    public Mem mem() {
        return Oshi.newMem();
    }
    /**
     * Jvm
     *
     * @return {@link Mem}
     */
    @ReadOperation
    public Jvm jvm() {
        return Oshi.newJvm();
    }
    /**
     * Sys
     *
     * @return {@link Mem}
     */
    @ReadOperation
    public Sys sys() {
        return Oshi.newSys();
    }
}
