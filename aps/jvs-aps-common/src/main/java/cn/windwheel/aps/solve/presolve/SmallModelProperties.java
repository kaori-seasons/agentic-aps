package cn.windwheel.aps.solve.presolve;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aps.small-model")
public class SmallModelProperties {
    public enum ResponseFormat { JSON_OBJECT, JSON_SCHEMA }

    private boolean enabled;
    private ResponseFormat responseFormat = ResponseFormat.JSON_OBJECT;
    private String endpoint = "http://127.0.0.1:11434/v1/chat/completions";
    private String model = "qwen2.5:7b";
    private String apiKey;
    private int timeoutSeconds = 20;
    private int maxContextTasks = 200;
}
