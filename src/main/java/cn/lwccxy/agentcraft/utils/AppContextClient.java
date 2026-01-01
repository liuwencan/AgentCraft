package cn.lwccxy.agentcraft.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class AppContextClient {
    @Resource
    private ApplicationContext applicationContext;
    private static ApplicationContext applicationContextRef;

    public AppContextClient() {
    }

    @PostConstruct
    public void init() {
        applicationContextRef = this.applicationContext;
    }

    public static boolean ready() {
        return applicationContextRef == null;
    }

    public static <T> T getBean(String beanName) {
        if (StringUtils.isNotEmpty(beanName) && applicationContextRef != null) {
            try {
                return (T) applicationContextRef.getBean(beanName);
            } catch (Exception var2) {
                log.error("NO Bean found " + beanName, var2);
            }
        }

        return null;
    }

    public static <T> T getBean(Class<T> requiredType) {
        try {
            return applicationContextRef.getBean(requiredType);
        } catch (Exception var2) {
            log.error("NO Type found " + requiredType, var2);
            return null;
        }
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        try {
            return applicationContextRef.getBeansOfType(type);
        } catch (Exception var2) {
            log.error("NO Class found " + type, var2);
            return null;
        }
    }

    public static String getEnvProperty(String key) {
        return applicationContextRef.getEnvironment().getProperty(key);
    }

    public static String getAppName() {
        return getEnvProperty("spring.application.name");
    }
}
