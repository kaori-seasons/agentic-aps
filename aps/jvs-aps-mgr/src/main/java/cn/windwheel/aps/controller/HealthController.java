package cn.windwheel.aps.controller;

import cn.windwheel.common.utils.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P0 启动验活接口
 */
@RestController
public class HealthController {

    @Value("${spring.application.name:jvs-aps}")
    private String applicationName;

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("application", applicationName);
        payload.put("timestamp", LocalDateTime.now());
        payload.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        return R.ok(payload);
    }
}
