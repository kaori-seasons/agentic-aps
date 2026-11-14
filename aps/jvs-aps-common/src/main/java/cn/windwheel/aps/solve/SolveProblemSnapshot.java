package cn.windwheel.aps.solve;

import cn.windwheel.aps.solve.model.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 将图关系保存为稳定编码，避免影子变量与前置任务的循环引用。 */
@Data
public class SolveProblemSnapshot {
    private LocalDateTime planTime;
    private PlanningStrategy strategy;
    private List<ProductionOrder> orders;
    private List<MainProductionResource> resources;
    private List<ProductionTask> tasks;
    private Map<String,List<String>> queues;

    private abstract static class TaskMixin {
        @JsonIgnore abstract MainProductionResource getResource();
        @JsonIgnore abstract Set<ProductionTask> getFrontTasks();
    }

    private abstract static class ResourceMixin {
        @JsonIgnore abstract List<ProductionTask> getTaskList();
    }

    static ObjectMapper mapper(ObjectMapper mapper) {
        return mapper.copy().addMixIn(ProductionTask.class,TaskMixin.class).addMixIn(MainProductionResource.class,ResourceMixin.class);
    }

    public static SolveProblemSnapshot from(SchedulingSolution solution) {
        SolveProblemSnapshot snapshot = new SolveProblemSnapshot();
        snapshot.setPlanTime(solution.getPlanTime());
        snapshot.setStrategy(solution.getStrategy());
        snapshot.setOrders(solution.getProductionOrders());
        snapshot.setResources(solution.getMainResources());
        snapshot.setTasks(solution.getTasks());
        snapshot.setQueues(solution.getMainResources().stream().collect(Collectors.toMap(MainProductionResource::getId,
                resource -> resource.getTaskList().stream().map(ProductionTask::getCode).toList(),(a,b)->a,LinkedHashMap::new)));
        return snapshot;
    }

    public SchedulingSolution restore() {
        Map<String,ProductionTask> byCode = tasks.stream().collect(Collectors.toMap(ProductionTask::getCode,task->task));
        for (MainProductionResource resource : resources) {
            resource.setTaskList(new ArrayList<>());
            for (String code : queues.getOrDefault(resource.getId(),List.of())) {
                ProductionTask task = Objects.requireNonNull(byCode.get(code),"快照引用未知任务");
                resource.getTaskList().add(task);
                task.setResource(resource);
            }
        }
        for (ProductionTask task : tasks) {
            task.setFrontTasks(task.getFrontTaskCodes() == null ? new HashSet<>() : task.getFrontTaskCodes().stream()
                    .map(byCode::get).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        return new SchedulingSolution().setPlanTime(planTime).setStrategy(strategy).setProductionOrders(orders)
                .setMainResources(resources).setTasks(tasks);
    }
}
