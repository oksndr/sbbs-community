package com.itheima.sbbs.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * 通知相关工具类
 */
@Slf4j
public class NotificationUtils {
    
    private static final int PREVIEW_LENGTH = 50;
    
    /**
     * 提取评论内容预览
     * @param content 原始评论内容
     * @return 预览内容（前50字符，换行符替换为空格）
     */
    public static String extractCommentPreview(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        // 替换换行符为空格
        String cleanedContent = content.replace("\n", " ").replace("\r", " ");
        
        // 截取前50字符
        if (cleanedContent.length() <= PREVIEW_LENGTH) {
            return cleanedContent.trim();
        } else {
            return cleanedContent.substring(0, PREVIEW_LENGTH).trim() + "...";
        }
    }
    
    /**
     * 从"回复 xxx :"格式的评论中提取实际内容
     * @param content 原始评论内容
     * @return 实际内容（去掉"回复 xxx :"部分）
     */
    public static String extractReplyContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        // 查找冒号位置
        int colonIndex = content.indexOf(":");
        if (colonIndex != -1 && content.startsWith("回复")) {
            // 提取冒号后面的内容
            String actualContent = content.substring(colonIndex + 1).trim();
            return extractCommentPreview(actualContent);
        }
        
        return extractCommentPreview(content);
    }
    
    /**
     * 生成邮件通知内容
     * @param notificationType 通知类型
     * @param senderUsername 发送者用户名
     * @param relatedTitle 相关标题（帖子标题或评论内容）
     * @param commentPreview 评论预览
     * @return 邮件内容
     */
    public static String generateEmailContent(Integer notificationType, String senderUsername, 
                                            String relatedTitle, String commentPreview) {
        String sender = senderUsername != null ? senderUsername : "某用户";
        StringBuilder content = new StringBuilder("您好！\n\n");
        
        switch (notificationType) {
            case 1: // 评论了我的帖子
                content.append(String.format("用户 \"%s\" 评论了您的帖子", sender));
                if (relatedTitle != null) {
                    content.append(" 《").append(relatedTitle).append("》");
                }
                content.append("。\n\n");
                if (commentPreview != null && !commentPreview.trim().isEmpty()) {
                    content.append("评论内容：\n").append(commentPreview).append("\n\n");
                }
                break;
                
            case 2: // 回复了我的评论
                content.append(String.format("用户 \"%s\" 回复了您的评论", sender));
                if (relatedTitle != null) {
                    content.append("（").append(relatedTitle).append("）");
                }
                content.append("。\n\n");
                if (commentPreview != null && !commentPreview.trim().isEmpty()) {
                    content.append("回复内容：\n").append(commentPreview).append("\n\n");
                }
                break;
                
            case 3: // 在评论中@了我
                content.append(String.format("用户 \"%s\" 在评论中@了您。\n\n", sender));
                if (commentPreview != null && !commentPreview.trim().isEmpty()) {
                    content.append("评论内容：\n").append(commentPreview).append("\n\n");
                }
                break;
                
            case 4: // "回复 xxx :"格式回复了我
                content.append(String.format("用户 \"%s\" 回复了您。\n\n", sender));
                if (commentPreview != null && !commentPreview.trim().isEmpty()) {
                    content.append("回复内容：\n").append(commentPreview).append("\n\n");
                }
                break;
                
            case 5: // 点赞了我的帖子
                content.append(String.format("恭喜！用户 \"%s\" 点赞了您的帖子", sender));
                if (relatedTitle != null) {
                    content.append(" 《").append(relatedTitle).append("》");
                }
                content.append("。\n\n");
                break;
                
            case 6: // 点踩了我的帖子
                content.append(String.format("用户 \"%s\" 点踩了您的帖子", sender));
                if (relatedTitle != null) {
                    content.append(" 《").append(relatedTitle).append("》");
                }
                content.append("。\n\n");
                break;
                
            case 7: // 点赞了我的评论
                content.append(String.format("恭喜！用户 \"%s\" 点赞了您的评论", sender));
                if (relatedTitle != null) {
                    content.append("：").append(relatedTitle);
                }
                content.append("。\n\n");
                break;
                
            case 8: // 点踩了我的评论
                content.append(String.format("用户 \"%s\" 点踩了您的评论", sender));
                if (relatedTitle != null) {
                    content.append("：").append(relatedTitle);
                }
                content.append("。\n\n");
                break;
                
            case 10: // 用户关注
                content.append(String.format("恭喜！用户 \"%s\" 关注了您。\n\n", sender));
                content.append("快去看看这位新朋友吧！\n\n");
                break;
                
            default:
                content.append(String.format("用户 \"%s\" 与您发生了互动。\n\n", sender));
                break;
        }
        
        content.append("请登录sbbs论坛查看详情。\n\n");
        content.append("sbbs论坛");
        
        return content.toString();
    }
    
    /**
     * 生成邮件主题
     * @param notificationType 通知类型
     * @param senderUsername 发送者用户名
     * @return 邮件主题
     */
    public static String generateEmailSubject(Integer notificationType, String senderUsername) {
        String sender = senderUsername != null ? senderUsername : "某用户";
        
        switch (notificationType) {
            case 1:
                return String.format("%s 评论了您的帖子", sender);
            case 2:
                return String.format("%s 回复了您的评论", sender);
            case 3:
                return String.format("%s 在评论中@了您", sender);
            case 4:
                return String.format("%s 回复了您", sender);
            case 5:
                return String.format("恭喜！%s 点赞了您的内容", sender);
            case 6:
                return String.format("%s 点踩了您的内容", sender);
            case 7:
                return String.format("恭喜！%s 点赞了您的评论", sender);
            case 8:
                return String.format("%s 点踩了您的评论", sender);
            case 10:
                return String.format("您有新的关注者：%s", sender);
            default:
                return "您有新的通知";
        }
    }
} 