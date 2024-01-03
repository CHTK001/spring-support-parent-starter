package com.chua.starter.common.support.profile;

import com.chua.common.support.converter.Converter;
import com.chua.common.support.lang.profile.DelegateProfile;
import com.chua.common.support.lang.profile.value.ProfileValue;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * spring environment
 *
 * @author CH
 */
public class EnvironmentProfile extends DelegateProfile {

    private final Environment environment;

    public EnvironmentProfile() {
        environment = SpringBeanUtils.getEnvironment();
    }

    public EnvironmentProfile(Environment environment) {
        super();
        this.environment = environment;
    }

    @Override
    public boolean noConfiguration() {
        return false;
    }

    @Override
    public <E> E bind(String pre, Class<E> target) {
        return Binder.get(environment).bindOrCreate(pre, target);
    }

    @Override
    public Map<String, ProfileValue> getProfile() {
        return super.getProfile();
    }

    @Override
    public <T> T getType(String name, T defaultValue, Class<T> returnType) {
        String property = environment.getProperty(name, (String) defaultValue);
        return Converter.convertIfNecessary(property, returnType);
    }

    @Override
    public Object getObject(String name) {
        return environment.getProperty(name);
    }

}
