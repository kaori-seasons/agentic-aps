package cn.windwheel.aps.service;

import cn.windwheel.aps.dto.SaveUserPreferenceDTO;
import cn.windwheel.aps.entity.UserPreferencePO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserPreferenceService extends IService<UserPreferencePO> {

    UserPreferencePO getByTenantAndUser(String tenantId, String userId);

    UserPreferencePO saveOrUpdate(String tenantId, String userId, SaveUserPreferenceDTO preference);
}
