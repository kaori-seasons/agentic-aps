package cn.windwheel.aps.controller;

import cn.windwheel.aps.auth.ApsAuthService;
import cn.windwheel.aps.auth.LoginRequest;
import cn.windwheel.common.exception.BusinessException;
import cn.windwheel.common.utils.R;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ApsAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<R<Map<String, Object>>> login(@Validated @RequestBody LoginRequest request, HttpSession session) {
        try {
            Map<String, Object> payload = authService.login(request, session);
            return ResponseEntity.ok(R.ok(payload));
        } catch (BusinessException e) {
            if (e.getCode() == 401) {
                return ResponseEntity.status(401).body(R.failed(401, e.getMessage()));
            }
            throw e;
        }
    }

    @PostMapping("/logout")
    public R<String> logout(HttpSession session) {
        authService.logout(session);
        return R.ok();
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(HttpServletRequest request) {
        return R.ok(authService.getCurrentUser(request));
    }
}
