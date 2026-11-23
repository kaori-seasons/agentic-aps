package cn.windwheel.aps.solve.calculate.service.impl;

import cn.windwheel.aps.entity.dto.*;
import cn.windwheel.aps.entity.enums.*;
import cn.windwheel.aps.enums.DayOfWeekEnum;
import cn.windwheel.aps.solve.model.*;
import cn.windwheel.aps.solve.util.TaskCalendarUtils;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShiftBoundaryTest {
    static List<WorkCalendar> calendars() {
        var days = new EnumMap<DayOfWeekEnum,Boolean>(DayOfWeekEnum.class);
        for (var d : DayOfWeekEnum.values()) days.put(d, !Set.of("SATURDAY","SUNDAY").contains(d.name()));
        var calendar = new WorkCalendar().setBeginTime(LocalDateTime.parse("2026-01-01T00:00:00"))
                .setEndTime(LocalDateTime.parse("2026-12-31T23:59:59")).setPriority(1).setWorkDaySetting(days)
                .setWorkModeTimes(List.of(new WorkModeTime().setStart(LocalTime.of(8,0)).setEnd(LocalTime.NOON),
                        new WorkModeTime().setStart(LocalTime.of(13,0)).setEnd(LocalTime.of(17,0))));
        return List.of(calendar);
    }

    @Test void closedWeekendDoesNotBecomeContinuousCapacity() {
        var friday = LocalDateTime.parse("2026-11-27T16:00:00");
        var monday = LocalDateTime.parse("2026-11-30T09:00:00");
        assertEquals(monday, TaskCalendarUtils.calculateEndTime(friday,Duration.ofHours(2),calendars()));
        assertEquals(Duration.ofHours(2),TaskCalendarUtils.workDuration(friday,monday,calendars()));
        assertEquals(monday.minusHours(1),TaskCalendarUtils.calculateStartTime(friday.plusHours(1),calendars(),PlanningDirectionEnum.FORWARD));
        assertEquals(monday.minusHours(1),TaskCalendarUtils.calculateStartTime(friday.plusDays(1),calendars(),PlanningDirectionEnum.FORWARD));
        assertEquals(friday,TaskCalendarUtils.calculateBackwardStartTime(monday,Duration.ofHours(2),calendars()));
    }

    @Test void actualForwardEntryAlignsShiftEndAndLunchWithoutMaterialDelay() {
        var resource = new MainProductionResource().setId("r").setWorkCalendars(calendars());
        var process = new ProcessRouteNodePropertiesDTO().setProcessRelationship(ProcessRelationshipEnum.ES)
                .setUseMainResources(List.of(new ProcessUseMainResourcesDTO().setId("r").setThroughput("1pH")));
        var task = new ProductionTask().setCode("t").setProcess(process).setResource(resource)
                .setQuantity(BigDecimal.ONE).setStartTask(true);
        var service = new TaskTimeForwardServiceImpl();
        for (var pair : List.of(new String[]{"2026-11-24T12:00:00","2026-11-24T13:00:00"},
                new String[]{"2026-11-27T17:00:00","2026-11-30T08:00:00"})) {
            assertEquals(LocalDateTime.parse(pair[1]), service.calculateStartTime(resource,task,null,
                    LocalDateTime.parse(pair[0]),List.of(task),List.of()));
        }
        assertEquals(LocalDateTime.parse("2026-11-28T10:00:00"),TaskCalendarUtils.calculateEndTime(
                LocalDateTime.parse("2026-11-28T09:00:00"),Duration.ofHours(1),List.of()));
    }

    @Test void explicitWeekendCalendarRetainsExistingWorkingDaySelection() {
        var weekday = calendars().get(0).setPriority(10);
        var overtime = calendars().get(0).setPriority(1);
        overtime.getWorkDaySetting().replaceAll((day,value) -> day == DayOfWeekEnum.SATURDAY);
        var saturday = LocalDateTime.parse("2026-11-28T08:00:00");
        assertEquals(saturday, TaskCalendarUtils.calculateStartTime(saturday,List.of(weekday,overtime),PlanningDirectionEnum.FORWARD));
        assertEquals(saturday.plusHours(1),TaskCalendarUtils.calculateEndTime(saturday,Duration.ofHours(1),List.of(weekday,overtime)));
        assertEquals(saturday.plusDays(2),TaskCalendarUtils.calculateStartTime(saturday.plusDays(1),List.of(weekday,overtime),PlanningDirectionEnum.FORWARD));
    }
}
