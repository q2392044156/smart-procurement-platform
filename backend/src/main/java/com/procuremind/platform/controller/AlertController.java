package com.procuremind.platform.controller;

import com.procuremind.platform.service.AlertService;

import com.procuremind.platform.pojo.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

    // 到期与超时提醒，路由前缀 /api/v1/alerts；类级 dashboard:view，复用看板权限、不单设 alert:view。
    // 唯一端点只读聚合：三个参数依次是资质到期天数、交期临近天数、审批停留小时数，由调用方传缺省 30/7/24。
    // 本类不含阈值判断与分类口径，都在 AlertMapper 的三条查询里；不落库、不推送，重复调用结果一致。
    // 提醒不是独立资源：没有按条的已读/忽略状态，前端按返回内容自行高亮。
/** 到期与超时提醒查询接口。 */
@RestController
@RequestMapping("/api/v1/alerts")
@PreAuthorize("hasAuthority('dashboard:view')")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    // 提醒汇总：dashboard:view；一次返回资质到期、交期临近、审批超时三类清单，不分页。
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(defaultValue = "30") int qualificationDays,
                                                    @RequestParam(defaultValue = "7") int deliveryDays,
                                                    @RequestParam(defaultValue = "24") int approvalHours) {
        return ApiResponse.ok(service.summary(qualificationDays, deliveryDays, approvalHours));
    }
}
