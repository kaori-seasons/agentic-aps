package cn.windwheel.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * 请求级身份上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class AuthContext implements Serializable {

    private String tenantId;
    private String userId;
    private String username;
    private List<String> roleCodes;
    private List<String> factoryIds;
    private List<String> workshopIds;
    private List<String> resourceGroupIds;
}
