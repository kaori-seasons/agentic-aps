package cn.bctools.aps.controller;

import cn.bctools.aps.dto.schedule.WorkAssignmentDTO;
import cn.bctools.aps.service.facade.TaskAssignmentFacadeService;
import cn.bctools.aps.vo.schedule.WorkAssignmentExcelVO;
import cn.bctools.common.exception.BusinessException;
import cn.bctools.common.utils.BeanCopyUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.http.Header;
import com.alibaba.excel.EasyExcelFactory;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author jvs
 */
@Slf4j
@Api(tags = "派工")
@RestController
@RequestMapping("/task-assignment")
@AllArgsConstructor
public class TaskAssignmentController {

    private final TaskAssignmentFacadeService taskAssignmentFacadeService;

    @ApiOperation("导出")
    @PostMapping("/export")
    public void exportWorkAssignments(@Validated @RequestBody WorkAssignmentDTO workAssignment, HttpServletResponse response) {
        String fileName =
                LocalDateTimeUtil.format(workAssignment.getBeginTime(), DatePattern.PURE_DATETIME_PATTERN)
                + "至"
                + LocalDateTimeUtil.format(workAssignment.getEndTime(), DatePattern.PURE_DATETIME_PATTERN)
                + "派工计划";
        List<WorkAssignmentExcelVO> workAssignmentExcels = BeanCopyUtil.copys(taskAssignmentFacadeService.workAssign(workAssignment), WorkAssignmentExcelVO.class);
        response.setCharacterEncoding("utf-8");
        response.setHeader(Header.CONTENT_DISPOSITION.toString(), "attachment; filename=".concat(URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8)));
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
        } catch (IOException e) {
            log.error("导出派工计划失败", e);
            throw new BusinessException("导出派工计划失败");
        }
        EasyExcelFactory.write(outputStream, WorkAssignmentExcelVO.class)
                .sheet("派工计划")
                .doWrite(workAssignmentExcels);
    }

}
