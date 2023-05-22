package com.chua.starter.common.support.eventbus;

import com.chua.common.support.eventbus.EventbusProvider;
import com.chua.common.support.lang.profile.Profile;

import java.util.concurrent.Executor;

/**
 * EventbusTemplate
 *
 * @author CH
 */
public class EventbusTemplate extends EventbusProvider {

    public EventbusTemplate() {
    }

    public EventbusTemplate(Profile profile) {
        super(profile);
    }

    public EventbusTemplate(Executor executor) {
        super(executor);
    }

    public EventbusTemplate(Profile profile, Executor executor) {
        super(profile, executor);
    }
}
