package cn.windwheel.aps.controller;

import cn.windwheel.aps.auth.ApsAuthService;
import cn.windwheel.aps.auth.ChangePasswordRequest;
import cn.windwheel.aps.auth.UpdateProfileRequest;
import cn.windwheel.aps.dto.SaveUserPreferenceDTO;
import cn.windwheel.common.utils.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final ApsAuthService authService;

    @GetMapping("/profile")
    public R<Map<String, Object>> profile(HttpServletRequest request) {
        return R.ok(authService.getProfile(request));
    }

    @PutMapping("/profile")
    public R<Map<String, Object>> updateProfile(@Validated @RequestBody UpdateProfileRequest request,
                                                 HttpServletRequest httpRequest) {
        return R.ok(authService.updateProfile(request, httpRequest));
    }

    @PutMapping("/password")
    public R<String> changePassword(@Validated @RequestBody ChangePasswordRequest request,
                                    HttpSession session) {
        authService.changePassword(request, session);
        return R.ok("密码已修改");
    }

    @GetMapping("/preferences")
    public R<Map<String, Object>> preferences(HttpSession session) {
        return R.ok(authService.getCurrentPreference(session));
    }

    @PutMapping("/preferences")
    public R<Map<String, Object>> savePreferences(@Validated @RequestBody SaveUserPreferenceDTO request, HttpSession session) {
        return R.ok(authService.saveCurrentPreference(request, session));
    }
}
