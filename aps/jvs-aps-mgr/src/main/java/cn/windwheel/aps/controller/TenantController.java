package cn.windwheel.aps.controller;

import cn.windwheel.aps.auth.ApsAuthService;
import cn.windwheel.common.utils.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final ApsAuthService authService;

    @GetMapping("/current")
    public R<Map<String, Object>> current() {
        return R.ok(authService.getCurrentTenant());
    }
}
