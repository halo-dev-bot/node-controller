package io.metersphere.api.service;

import org.apache.commons.collections.MapUtils;

import java.util.HashMap;
import java.util.Map;

public class JMeterRunContext {
    private boolean enable;
    private String plugUrl;
    private Map<String, String> projectUrls;
    private static JMeterRunContext context;

    private JMeterRunContext() {

    }

    public Map<String, String> getProjectUrls() {
        if (MapUtils.isEmpty(projectUrls)) {
            return new HashMap<>();
        }
        return projectUrls;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getPlugUrl() {
        return plugUrl;
    }

    public void setPlugUrl(String plugUrl) {
        this.plugUrl = plugUrl;
    }

    public static JMeterRunContext getContext() {
        if (context == null) {
            return new JMeterRunContext();
        }
        return context;
    }
}
