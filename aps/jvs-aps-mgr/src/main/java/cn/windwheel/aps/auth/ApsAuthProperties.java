package cn.windwheel.aps.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "aps.auth")
public class ApsAuthProperties {

    private String systemName = "智能APS";
    private boolean allowQueryFallback = true;
    private String defaultTheme = "darkblue";
    private String defaultLanguage = "zh_CN";
    private String defaultRoute = "/productionSchedulingPlan";
    private List<DemoUser> users = new ArrayList<>();

    @Data
    public static class DemoUser {
        private String tenantId;
        private String userId;
        private String username;
        private String displayName;
        private String password;
        private String theme;
        private String language;
        private String defaultRoute;
        private List<String> roleCodes = new ArrayList<>();
        private List<String> factoryIds = new ArrayList<>();
        private List<String> workshopIds = new ArrayList<>();
        private List<String> resourceGroupIds = new ArrayList<>();
    }
}
