package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.itheima.sbbs.entity.Report;
import com.itheima.sbbs.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 提交举报接口
     *
     * @param report 包含被举报帖子ID和举报理由
     * @return
     */
    @SaCheckLogin // 需要用户登录才能举报
    @PostMapping
    public SaResult submitReport(@RequestBody Report report) {
        // 从当前登录用户获取举报人ID
        int reporterId = StpUtil.getLoginIdAsInt();
        report.setReporterId(reporterId);

        // 简单校验举报理由
        if (report.getReason() == null || report.getReason().trim().isEmpty()) {
            return SaResult.error("举报理由不能为空");
        }

        boolean success = reportService.submitReport(report);

        if (success) {
            return SaResult.ok("举报已提交，感谢您的反馈。");
        } else {
            // TODO: 根据 submitReport 中的具体失败原因返回更详细的错误信息
            return SaResult.error("举报提交失败。");
        }
    }
} 