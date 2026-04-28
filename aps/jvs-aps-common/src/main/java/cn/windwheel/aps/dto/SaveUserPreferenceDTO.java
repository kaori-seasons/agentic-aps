package cn.windwheel.aps.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 用户个人偏好保存参数
 */
@Data
public class SaveUserPreferenceDTO implements Serializable {

    @NotBlank(message = "主题不能为空")
    private String theme;

    @NotBlank(message = "语言不能为空")
    private String language;

    @NotBlank(message = "默认首页不能为空")
    private String defaultRoute;
}
