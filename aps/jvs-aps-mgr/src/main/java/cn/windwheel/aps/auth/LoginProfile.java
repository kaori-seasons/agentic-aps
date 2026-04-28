package cn.windwheel.aps.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class LoginProfile implements Serializable {

    private String tenantId;
    private String userId;
    private String username;
    private String displayName;
}
