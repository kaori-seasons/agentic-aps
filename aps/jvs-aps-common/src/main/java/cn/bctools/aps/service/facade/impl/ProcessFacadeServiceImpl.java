package cn.bctools.aps.service.facade.impl;

import cn.bctools.aps.dto.*;
import cn.bctools.aps.entity.MaterialPO;
import cn.bctools.aps.entity.ProcessPO;
import cn.bctools.aps.entity.ProcessRoutePO;
import cn.bctools.aps.entity.ProductionResourcePO;
import cn.bctools.aps.entity.dto.ProcessRouteNodePropertiesDTO;
import cn.bctools.aps.entity.dto.ProcessUseAuxiliaryResourcesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMainResourcesDTO;
import cn.bctools.aps.entity.dto.ProcessUseMaterialsDTO;
import cn.bctools.aps.service.MaterialService;
import cn.bctools.aps.service.ProcessRouteService;
import cn.bctools.aps.service.ProcessService;
import cn.bctools.aps.service.ProductionResourceService;
import cn.bctools.aps.service.facade.ProcessFacadeService;
import cn.bctools.aps.util.ProcessRouteDesignUtils;
import cn.bctools.aps.util.ProcessUtils;
import cn.bctools.aps.vo.DetailProcessVO;
import cn.bctools.aps.vo.MaterialProcessRouteVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.bctools.common.utils.ObjectNull;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * @author jvs
 */
@Service
@AllArgsConstructor
public class ProcessFacadeServiceImpl implements ProcessFacadeService {

    private final MaterialService materialService;
    private final ProductionResourceService productionResourceService;
    private final ProcessService processService;
    private final ProcessRouteService processRouteService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveProcess(SaveProcessDTO saveProcess) {
        ProcessPO process = BeanCopyUtil.copy(saveProcess, ProcessPO.class);
        checkCanSaveProcess(process);
        processService.save(process);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateProcess(UpdateProcessDTO updateProcess) {
        ProcessPO process = BeanCopyUtil.copy(updateProcess, ProcessPO.class);
        checkCanSaveProcess(process);
        processService.updateById(process);
    }

    @Override
    public Page<DetailProcessVO> pageProcess(PageProcessDTO pageQuery) {
        LambdaQueryWrapper<ProcessPO> wrapper = Wrappers.<ProcessPO>lambdaQuery()
                .like(ObjectNull.isNotNull(pageQuery.getCode()), ProcessPO::getCode, pageQuery.getCode())
                .like(ObjectNull.isNotNull(pageQuery.getName()), ProcessPO::getName, pageQuery.getName())
                .orderByDesc(ProcessPO::getCreateTime);
        Page<ProcessPO> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        processService.page(page, wrapper);

        // 填充数据
        List<String> resourceIds = new ArrayList<>();
        List<String> materialIds = new ArrayList<>();
        page.getRecords().forEach(process -> {
            resourceIds.addAll(ProcessUtils.extractProcessMainResourceIds(process.getUseMainResources()));
            resourceIds.addAll(ProcessUtils.extractAuxiliaryResourceIds(process.getUseAuxiliaryResources()));
            materialIds.addAll(ProcessUtils.extractMaterialIds(process.getUseMaterials()));
        });
        Map<String, ProductionResourcePO> productionResourceMap = productionResourceService.listByIdsToMap(new HashSet<>(resourceIds));
        Map<String, MaterialPO> materialMap = materialService.listByIdsToMap(new HashSet<>(materialIds));

        List<DetailProcessVO> voList = page.getRecords().stream().map(detail -> {
            DetailProcessVO vo = BeanCopyUtil.copy(detail, DetailProcessVO.class);
            ProcessUtils.fillProcessInfo(vo, productionResourceMap, materialMap);
            return vo;
        }).toList();

        Page<DetailProcessVO> pageVoList = new Page<>(page.getCurrent(), page.getSize());
        pageVoList
                .setRecords(voList)
                .setTotal(page.getTotal());
        return pageVoList;
    }

    @Override
    public DetailProcessVO getProcessDetail(String id) {
        ProcessPO process = processService.getById(id);
        if (ObjectNull.isNull(process)) {
            return null;
        }
        DetailProcessVO vo = BeanCopyUtil.copy(process, DetailProcessVO.class);
        // 填充数据
        List<String> resourceIds = new ArrayList<>();
        resourceIds.addAll(ProcessUtils.extractProcessMainResourceIds(process.getUseMainResources()));
        resourceIds.addAll(ProcessUtils.extractAuxiliaryResourceIds(process.getUseAuxiliaryResources()));
        List<String> materialIds = ProcessUtils.extractMaterialIds(process.getUseMaterials());
        Map<String, ProductionResourcePO> productionResourceMap = productionResourceService.listByIdsToMap(resourceIds);
        Map<String, MaterialPO> materialMap = materialService.listByIdsToMap(materialIds);
        ProcessUtils.fillProcessInfo(vo, productionResourceMap, materialMap);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveProcessRoute(SaveProcessRouteDTO saveProcessRoute) {
        ProcessRoutePO processRoute = BeanCopyUtil.copy(saveProcessRoute, ProcessRoutePO.class);
        checkCanSaveProcessRoute(processRoute);
        setProcessRouteDefault(processRoute);
        processRouteService.save(processRoute);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateProcessRoute(UpdateProcessRouteDTO updateProcessRoute) {
        ProcessRoutePO processRoute = BeanCopyUtil.copy(updateProcessRoute, ProcessRoutePO.class);
        checkCanSaveProcessRoute(processRoute);
        setProcessRouteDefault(processRoute);
        processRouteService.updateById(processRoute);
    }

    @Override
    public MaterialProcessRouteVO getMaterialProcessRoute(String materialId) {
        ProcessRoutePO processRoute = processRouteService.getOne(Wrappers.<ProcessRoutePO>lambdaQuery()
                .eq(ProcessRoutePO::getMaterialId, materialId));
        if (ObjectNull.isNull(processRoute)) {
            return new MaterialProcessRouteVO().setMaterialId(materialId);
        }

        // 填充数据
        Set<String> resourceIds = new HashSet<>();
        Set<String> materialIds = new HashSet<>();
        processRoute.getRouteDesign().getNodes().stream()
                .filter(node -> ObjectNull.isNotNull(node.getData()))
                .forEach(node -> {
                    ProcessRouteNodePropertiesDTO properties = node.getData();
                    resourceIds.addAll(ProcessUtils.extractProcessMainResourceIds(properties.getUseMainResources()));
                    resourceIds.addAll(ProcessUtils.extractAuxiliaryResourceIds(properties.getUseAuxiliaryResources()));
                    materialIds.addAll(ProcessUtils.extractMaterialIds(properties.getUseMaterials()));
                });
        Map<String, ProductionResourcePO> resourceMap = productionResourceService.listByIdsToMap(resourceIds);
        Map<String, MaterialPO> materialMap = materialService.listByIdsToMap(materialIds);
        MaterialProcessRouteVO materialProcessRouteVO = BeanCopyUtil.copy(processRoute, MaterialProcessRouteVO.class);
        materialProcessRouteVO.getRouteDesign().getNodes().stream()
                .filter(node -> ObjectNull.isNotNull(node.getData()))
                .forEach(node -> {
                    DetailProcessVO process = node.getData();
                    ProcessUtils.fillProcessInfo(process, resourceMap, materialMap);
                });
        return materialProcessRouteVO;
    }

    /**
     * 校验工序是否可保存
     *
     * @param process 工序
     */
    private void checkCanSaveProcess(ProcessPO process) {
        // 校验工序编码是否重复
        checkExistsProcess(process.getId(), process.getCode());
        // 校验资源
        checkResource(process.getUseMainResources(), process.getUseAuxiliaryResources());
        // 校验物料是否存在
        checkExistsMaterial(process.getUseMaterials());
    }

    /**
     * 校验工序编码是否重复
     *
     * @param id   工序id
     * @param code 工序编码
     */
    private void checkExistsProcess(String id, String code) {
        ProcessPO existsProcess = processService.getOne(Wrappers.<ProcessPO>lambdaQuery()
                .select(ProcessPO::getId)
                .eq(ProcessPO::getCode, code));
        if (ObjectNull.isNull(existsProcess)) {
            return;
        }
        if (existsProcess.getId().equals(id)) {
            return;
        }
        throw new BusinessException("工序编码已存在");
    }

    /**
     * 校验资源
     *
     * @param useMainResources 主资源集合
     * @param useAuxiliaryResources 辅资源集合
     */
    private void checkResource(List<ProcessUseMainResourcesDTO> useMainResources, List<ProcessUseAuxiliaryResourcesDTO> useAuxiliaryResources) {
        List<String> mainResourceIds = ProcessUtils.extractProcessMainResourceIds(useMainResources);
        if (ObjectNull.isNull(mainResourceIds)) {
            return;
        }
        List<ProductionResourcePO> productionResourceList = productionResourceService.listByIds(mainResourceIds);
        // true-工序资源类型配置错误，false-配置正确
        boolean processResourceTypeError = productionResourceList.stream()
                .anyMatch(resource -> !mainResourceIds.contains(resource.getId()));
        if (processResourceTypeError) {
            throw new BusinessException("工序资源类型配置错误");
        }
        // 校验资源是否存在
        if (mainResourceIds.size() == productionResourceList.size()) {
            return;
        }
        throw new BusinessException("资源编码不存在");
    }

    /**
     * 校验物料是否存在
     *
     * @param useMaterials 物料集合
     */
    private void checkExistsMaterial(List<ProcessUseMaterialsDTO> useMaterials) {
        List<String> materialIds = ProcessUtils.extractMaterialIds(useMaterials);
        if (ObjectNull.isNull(materialIds)) {
            return;
        }
        if (materialIds.size() == materialService.listByIds(materialIds).size()) {
            return;
        }
        throw new BusinessException("物料编码不存在");
    }


    /**
     * 校验工艺路线是否可保存
     *
     * @param processRoute 工艺路线
     */
    public void checkCanSaveProcessRoute(ProcessRoutePO processRoute) {
        // 校验工艺路线设计
        ProcessRouteDesignUtils.validateDesign(processRoute.getRouteDesign());

        // 一个物料只能有一条工艺路线
        ProcessRoutePO existsProcessRoute = processRouteService.getOne(Wrappers.<ProcessRoutePO>lambdaQuery()
                .select(ProcessRoutePO::getId)
                .eq(ProcessRoutePO::getMaterialId, processRoute.getMaterialId()));
        if (ObjectNull.isNull(existsProcessRoute)) {
            return;
        }
        if (existsProcessRoute.getId().equals(processRoute.getId())) {
            return;
        }
        throw new BusinessException("工艺路线已存在");
    }

    /**
     * 工艺路线设置默认值
     *
     * @param processRoute 工艺路线
     */
    private void setProcessRouteDefault(ProcessRoutePO processRoute) {
        // 设置默认值
        processRoute.setEnabled(Boolean.FALSE);
    }
}
