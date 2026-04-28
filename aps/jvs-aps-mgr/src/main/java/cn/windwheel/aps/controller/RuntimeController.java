package cn.windwheel.aps.controller;

import cn.windwheel.aps.auth.ApsAuthService;
import cn.windwheel.common.utils.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/runtime")
@RequiredArgsConstructor
public class RuntimeController {

    private final ApsAuthService authService;

    @GetMapping("/bootstrap")
    public R<Map<String, Object>> bootstrap(HttpServletRequest request, HttpSession session) {
        return R.ok(authService.buildBootstrap(request, session));
    }

    @GetMapping("/menu")
    public R<List<Map<String, Object>>> menu() {
        return R.ok(authService.buildMenuTree());
    }
}
