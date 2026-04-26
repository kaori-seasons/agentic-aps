package cn.windwheel.common.auth;

import cn.windwheel.common.model.AuthContext;
import cn.windwheel.common.utils.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionServiceImplTest {

    private final PermissionServiceImpl permissionService = new PermissionServiceImpl();

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void adminShouldHaveWildcardPermission() {
        setRoles("APS_ADMIN");

        assertTrue(permissionService.hasPermission("aps:material:view"));
        assertTrue(permissionService.hasPermission("aps:audit:view"));
    }

    @Test
    void planManagerShouldHaveScheduleConfirmAndAuditPermission() {
        setRoles("APS_PLAN_MANAGER");

        assertTrue(permissionService.hasPermission("aps:schedule:confirm"));
        assertTrue(permissionService.hasPermission("aps:audit:view"));
        assertFalse(permissionService.hasPermission("aps:unknown:view"));
    }

    @Test
    void schedulerShouldNotHaveConfirmOrAuditPermission() {
        setRoles("APS_SCHEDULER");

        assertTrue(permissionService.hasPermission("aps:schedule:run"));
        assertTrue(permissionService.hasPermission("aps:task-adjust:edit"));
        assertFalse(permissionService.hasPermission("aps:schedule:confirm"));
        assertFalse(permissionService.hasPermission("aps:audit:view"));
    }

    @Test
    void dispatcherShouldBeViewOnlyForTaskAdjust() {
        setRoles("APS_DISPATCHER");

        assertTrue(permissionService.hasPermission("aps:task-adjust:view"));
        assertFalse(permissionService.hasPermission("aps:task-adjust:edit"));
    }

    @Test
    void explicitWildcardPermissionCodeShouldMatchByPrefix() {
        setRoles("aps:resource:*");

        assertTrue(permissionService.hasPermission("aps:resource:view"));
        assertTrue(permissionService.hasPermission("aps:resource:edit"));
        assertFalse(permissionService.hasPermission("aps:material:view"));
    }

    @Test
    void hasRoleShouldIgnoreCase() {
        setRoles("APS_PLAN_MANAGER");

        assertTrue(permissionService.hasRole("aps_plan_manager"));
        assertFalse(permissionService.hasRole("APS_DISPATCHER"));
    }

    private void setRoles(String... roleCodes) {
        AuthContextHolder.setAuthContext(new AuthContext()
                .setTenantId("tenant-a")
                .setUserId("u-1")
                .setUsername("tester")
                .setRoleCodes(List.of(roleCodes))
                .setFactoryIds(List.of())
                .setWorkshopIds(List.of())
                .setResourceGroupIds(List.of()));
    }
}
