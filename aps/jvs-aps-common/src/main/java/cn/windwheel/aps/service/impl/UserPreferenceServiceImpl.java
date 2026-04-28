package cn.windwheel.aps.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.windwheel.aps.dto.SaveUserPreferenceDTO;
import cn.windwheel.aps.entity.UserPreferencePO;
import cn.windwheel.aps.mapper.UserPreferenceMapper;
import cn.windwheel.aps.service.UserPreferenceService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferenceServiceImpl extends ServiceImpl<UserPreferenceMapper, UserPreferencePO> implements UserPreferenceService {

    @Override
    public UserPreferencePO getByTenantAndUser(String tenantId, String userId) {
        return getOne(Wrappers.<UserPreferencePO>lambdaQuery()
                .eq(UserPreferencePO::getTenantId, tenantId)
                .eq(UserPreferencePO::getUserId, userId)
                .last("limit 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPreferencePO saveOrUpdate(String tenantId, String userId, SaveUserPreferenceDTO preference) {
        UserPreferencePO entity = getByTenantAndUser(tenantId, userId);
        if (entity == null) {
            entity = new UserPreferencePO()
                    .setTenantId(tenantId)
                    .setUserId(userId);
        }
        entity.setTheme(StrUtil.trim(preference.getTheme()));
        entity.setLanguage(StrUtil.trim(preference.getLanguage()));
        entity.setDefaultRoute(StrUtil.trim(preference.getDefaultRoute()));
        if (StrUtil.isBlank(entity.getId())) {
            save(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }
}
