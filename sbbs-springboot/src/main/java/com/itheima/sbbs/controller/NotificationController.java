package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.sbbs.entity.Notification;
import com.itheima.sbbs.entity.NotificationJumpInfo;
import com.itheima.sbbs.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知相关接口
 */
@RestController
@RequestMapping("/api/notifications")
@Slf4j
public class NotificationController {
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * 获取通知列表
     * 
     * @param page 页码，默认1
     * @param size 每页大小，默认10，最大100
     * @param onlyUnread 是否只查询未读通知，默认false
     * @return 通知列表
     */
    @GetMapping
    @SaCheckLogin
    public SaResult getNotificationList(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size,
                                       @RequestParam(defaultValue = "false") Boolean onlyUnread) {
        log.info("查询通知列表，页码: {}, 每页大小: {}, 只查未读: {}", page, size, onlyUnread);
        
        try {
            Integer currentUserId = StpUtil.getLoginIdAsInt();
            
            Page<Notification> result = notificationService.getNotificationList(currentUserId, page, size, onlyUnread);
            
            log.info("通知列表查询成功，用户ID: {}, 总数: {}, 当前页记录数: {}", 
                    currentUserId, result.getTotal(), result.getRecords().size());
            
            return SaResult.code(200).data(result).setMsg("获取通知列表成功");
            
        } catch (Exception e) {
            log.error("查询通知列表失败", e);
            return SaResult.error("获取通知列表失败，请稍后重试");
        }
    }
    
    /**
     * 标记通知为已读
     * 
     * @param notificationId 通知ID
     * @return 操作结果
     */
    @PutMapping("/{notificationId}/read")
    @SaCheckLogin
    public SaResult markAsRead(@PathVariable Integer notificationId) {
        log.info("标记通知为已读，通知ID: {}", notificationId);
        
        try {
            if (notificationId == null || notificationId <= 0) {
                return SaResult.error("通知ID不能为空或无效");
            }
            
            Integer currentUserId = StpUtil.getLoginIdAsInt();
            
            boolean success = notificationService.markAsRead(notificationId, currentUserId);
            
            if (success) {
                log.info("标记通知为已读成功，通知ID: {}, 用户ID: {}", notificationId, currentUserId);
                return SaResult.code(200).setMsg("标记为已读成功");
            } else {
                log.warn("标记通知为已读失败，通知ID: {}, 用户ID: {}", notificationId, currentUserId);
                return SaResult.error("标记为已读失败，通知不存在或您无权操作");
            }
            
        } catch (Exception e) {
            log.error("标记通知为已读失败，通知ID: {}", notificationId, e);
            return SaResult.error("标记为已读失败，请稍后重试");
        }
    }
    
    /**
     * 批量标记通知为已读
     * 
     * @param notificationIds 通知ID列表
     * @return 操作结果
     */
    @PutMapping("/batch-read")
    @SaCheckLogin
    public SaResult markBatchAsRead(@RequestBody List<Integer> notificationIds) {
        log.info("批量标记通知为已读，通知ID数量: {}", notificationIds != null ? notificationIds.size() : 0);
        
        try {
            if (notificationIds == null || notificationIds.isEmpty()) {
                return SaResult.error("通知ID列表不能为空");
            }
            
            if (notificationIds.size() > 100) {
                return SaResult.error("单次最多处理100条通知");
            }
            
            Integer currentUserId = StpUtil.getLoginIdAsInt();
            
            int successCount = notificationService.markBatchAsRead(notificationIds, currentUserId);
            
            log.info("批量标记通知为已读完成，用户ID: {}, 成功数量: {}/{}", 
                    currentUserId, successCount, notificationIds.size());
            
            return SaResult.code(200).data(successCount).setMsg("批量标记为已读完成，成功处理" + successCount + "条");
            
        } catch (Exception e) {
            log.error("批量标记通知为已读失败", e);
            return SaResult.error("批量标记为已读失败，请稍后重试");
        }
    }
    
    /**
     * 获取通知跳转信息
     * 用于前端点击通知后的页面跳转
     * 
     * @param notificationId 通知ID
     * @return 跳转信息
     */
    @GetMapping("/{notificationId}/jump-info")
    @SaCheckLogin
    public SaResult getJumpInfo(@PathVariable Integer notificationId) {
        log.info("获取通知跳转信息，通知ID: {}", notificationId);
        
        try {
            if (notificationId == null || notificationId <= 0) {
                return SaResult.error("通知ID不能为空或无效");
            }
            
            NotificationJumpInfo jumpInfo = notificationService.getJumpInfo(notificationId);
            
            if (jumpInfo == null) {
                return SaResult.error("通知不存在、已删除或您无权访问");
            }
            
            log.info("成功获取通知跳转信息，通知ID: {}, 跳转类型: {}, 帖子ID: {}, 页码: {}", 
                    notificationId, jumpInfo.getNotificationType(), jumpInfo.getPostId(), jumpInfo.getPageNumber());
            
            return SaResult.code(200).data(jumpInfo).setMsg("获取跳转信息成功");
            
        } catch (Exception e) {
            log.error("获取通知跳转信息失败，通知ID: {}", notificationId, e);
            return SaResult.error("获取跳转信息失败，请稍后重试");
        }
    }
}
