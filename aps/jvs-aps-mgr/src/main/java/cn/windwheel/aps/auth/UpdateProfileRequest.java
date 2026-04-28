package cn.windwheel.aps.auth;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class UpdateProfileRequest implements Serializable {

    @NotBlank(message = "显示名不能为空")
    private String displayName;
}
