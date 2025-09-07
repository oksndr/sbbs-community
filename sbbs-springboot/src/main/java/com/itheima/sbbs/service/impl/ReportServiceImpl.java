package com.itheima.sbbs.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.sbbs.entity.Report;
import com.itheima.sbbs.mapper.ReportMapper;
import com.itheima.sbbs.mapper.PostMapper;
import com.itheima.sbbs.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private PostMapper postMapper; // 用于校验被举报帖子是否存在

    @Override
    @Transactional
    public boolean submitReport(Report report) {
        // 1. 校验被举报的帖子是否存在且未被删除
        if (report.getReportedPostId() == null) {
            // TODO: 抛出业务异常或返回错误码
            return false; // 示例：被举报帖子ID不能为空
        }
        // 使用 postMapper 检查帖子是否存在 (假设 PostMapper 有 getById 或类似的查询方法)
        // 您需要根据您的 PostMapper 实际方法来调用
        // 例如: Post post = postMapper.selectById(report.getReportedPostId());
        // if (post == null || post.getDeleted() == 1) { ... return false; }
        // 这里简化处理，仅检查ID不为空

        // 2. 设置举报人ID (从当前登录用户获取)
        // TODO: 从 Sa-Token 获取当前登录用户ID并设置到 report 对象
        // int reporterId = StpUtil.getLoginIdAsInt();
        // report.setReporterId(reporterId);
        // 这里假设 reporterId 已经通过其他方式设置到了 report 对象

        // 3. 设置举报状态为初始状态
        report.setStatus("pending");

        // 4. 保存举报记录到数据库
        return save(report);
    }
} 