package cn.windwheel.aps.solve.presolve;

import cn.windwheel.aps.entity.enums.SolverProfileEnum;
import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
public class DynamicSchedulingRequest {
    @Size(max = 100)
    @Valid
    private List<Event> events = new ArrayList<>();

    @Size(max = 1000)
    @Valid
    private List<Seed> seeds = new ArrayList<>();

    private SolverProfileEnum recommendedProfile;

    @Valid
    private cn.windwheel.aps.entity.dto.planning.ConstraintWeightConfigDTO recommendedWeights;

    @NotNull
    private LocalDateTime freezeUntil;

    public enum EventType { INSERT_ORDER, RESOURCE_UNAVAILABLE, MATERIAL_DELAY, DUE_DATE_CHANGE }

    @Data
    public static class Event {
        @NotNull private EventType type;
        @NotBlank @Size(max = 128) private String targetId;
        private LocalDateTime start;
        private LocalDateTime end;
        private LocalDateTime availableAt;
        private LocalDateTime deliveryTime;
    }

    @Data
    public static class Seed {
        @NotBlank @Size(max = 128) private String taskCode;
        @NotBlank @Size(max = 128) private String resourceId;
        @Min(0) @Max(100000) private int position;
    }
}
