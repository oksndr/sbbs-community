package com.itheima.sbbs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.sbbs.entity.Report;

public interface ReportService extends IService<Report> {
    /**
     * 提交新的举报
     *
     * @param report 举报信息
     * @return 是否成功
     */
    boolean submitReport(Report report);

    // TODO: 添加管理员处理举报的方法
} 