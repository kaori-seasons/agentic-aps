package cn.windwheel.aps.solve.model;

import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ResourceDowntime {
    private String resourceId;
    private LocalDateTime start;
    private LocalDateTime end;
}
