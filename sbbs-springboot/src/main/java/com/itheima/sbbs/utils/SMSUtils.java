package com.itheima.sbbs.utils;

import com.itheima.sbbs.config.MailConfig;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 短信发送工具类
 */
@Component
public class SMSUtils {

	private static final Logger log = LoggerFactory.getLogger(SMSUtils.class);
	
	@Autowired
	private MailConfig mailConfig;
	
	/**
	 * 配置邮件基本信息
	 */
	private SimpleEmail configureEmail() throws EmailException {
		SimpleEmail mail = new SimpleEmail();
		mail.setHostName(mailConfig.getHost()); // 发送邮件的服务器
		mail.setSmtpPort(mailConfig.getPort()); // 设置端口
		mail.setAuthentication(mailConfig.getUsername(), mailConfig.getPassword()); // 授权码
		mail.setFrom(mailConfig.getUsername(), mailConfig.getFromName()); // 发送邮件的邮箱和发件人
		
		// 配置加密方式
		if (mailConfig.getSslEnabled()) {
			mail.setSSLOnConnect(true); // 使用SSL
			mail.setSSLCheckServerIdentity(true); // SSL身份验证
		} else if (mailConfig.getTlsEnabled()) {
			mail.setStartTLSEnabled(true); // 使用STARTTLS
			mail.setStartTLSRequired(true); // 要求TLS
		}
		
		// 设置连接超时
		mail.setSocketConnectionTimeout(60000); // 60秒连接超时
		mail.setSocketTimeout(60000); // 60秒读取超时
		
		return mail;
	}

	/**
	 * 🚀 异步发送注册验证码邮件
	 */
	@Async
	public void sendMessage(String addressMail, String authCode){
		try {
			log.info("开始异步发送注册验证码邮件到: {}", addressMail);
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("sbbs论坛注册验证码");//设置邮件的主题
			mail.setMsg("尊敬的用户:您好!\n\n您的注册验证码为: " + authCode + "\n(有效期为5分钟), 请勿泄露给他人!\n\n祝您使用愉快！\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
			log.info("注册验证码邮件发送成功: {}", addressMail);
		} catch (EmailException e) {
			log.error("发送注册验证码邮件失败，邮箱: {}，错误: {}", addressMail, e.getMessage(), e);
		}
	}

	/**
	 * 收到有人回复的通知时: 发邮件通知作者
	 */
	@Async
	public void sendFriMessage(String addressMail, String title, String content){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("您的帖子收到新回复：" + title);//设置邮件的主题
			mail.setMsg("您好！\n\n您在sbbs论坛发布的帖子 \"" + title + "\" 收到了一条新回复。\n\n回复内容预览：\n" + content + "\n\n请登录sbbs论坛查看详情。\n\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
		} catch (EmailException e) {
			e.printStackTrace();
			log.error("邮件系统出问题了!!快去修理一下");
		}
	}

	/**
	 * 收到有人@的通知时: 发邮件通知被@的人
	 */
	@Async
	public void sendAiteMessage(String addressMail, String title){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("您在sbbs论坛被提及了");//设置邮件的主题
			mail.setMsg("您好！\n\n您在sbbs论坛的帖子 \"" + title + "\" 下的评论或回复中被提及了。\n\n请登录sbbs论坛查看详情。\n\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
		} catch (EmailException e) {
			e.printStackTrace();
			log.error("邮件系统出问题了!!快去修理一下");
		}
	}

	/**
	 * 我的评论被别人回复了
	 */
	@Async
	public void sendReplyMessage(String addressMail, String content){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("您的评论收到新回复");//设置邮件的主题
			mail.setMsg("您好！\n\n您在sbbs论坛的评论收到了新的回复。\n\n回复内容预览：\n" + content + "\n\n请登录sbbs论坛查看详情。\n\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
		} catch (EmailException e) {
			e.printStackTrace();
			log.error("邮件系统出问题了!!快去修理一下");
		}
	}


	/**
	 * 有人在2级评论里@人
	 */
	@Async
	public void sendReply2Message(String addressMail){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("您在sbbs论坛被提及了");//设置邮件的主题
			mail.setMsg("您好！\n\n您在sbbs论坛的评论或回复中被提及了。\n\n请登录sbbs论坛查看详情。\n\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
		} catch (EmailException e) {
			e.printStackTrace();
			log.error("邮件系统出问题了!!快去修理一下");
		}
	}


	/**
	 * 获得点赞
	 */
	@Async
	public void getLiked(String addressMail){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("恭喜！您的内容收到了赞");//设置邮件的主题
			mail.setMsg("您好！\n\n恭喜您！您在sbbs论坛发布的内容（帖子或评论）收到了一条新的点赞。\n\n快去看看吧！\n\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
		} catch (EmailException e) {
			e.printStackTrace();
			log.error("邮件系统出问题了!!快去修理一下");
		}
	}

	/**
	 * 获得点踩
	 */
	@Async
	public void getDisliked(String addressMail){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("您的内容收到了踩");//设置邮件的主题
			mail.setMsg("您好！\n\n很遗憾通知您，您在sbbs论坛发布的内容（帖子或评论）收到了一个踩。\n\n请登录sbbs论坛查看详情。\n\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
		} catch (EmailException e) {
			e.printStackTrace();
			log.error("邮件系统出问题了!!快去修理一下");
		}
	}

	/**
	 * 帖子被管理员删除通知
	 */
	@Async
	public void sendPostDeletedNotification(String addressMail, String postTitle){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("您的帖子已被删除");//设置邮件的主题
			mail.setMsg("您好！\n很遗憾通知您，您在sbbs论坛发布的帖子 \"" + postTitle + "\" 因违反社区规定已被管理员删除。\n\n如有疑问，请联系管理员。\n\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
		} catch (EmailException e) {
			e.printStackTrace();
			log.error("发送帖子删除通知邮件失败");
		}
	}

	/**
	 * 用户关注通知
	 */
	@Async
	public void sendFollowNotification(String addressMail, String followerUsername){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("您有新的关注者");//设置邮件的主题
			mail.setMsg("您好！\n\n恭喜您！用户 \"" + followerUsername + "\" 关注了您。\n\n快去看看这位新朋友吧！\n\nsbbs论坛");//设置邮件的内容
			mail.send();//发送
		} catch (EmailException e) {
			e.printStackTrace();
			log.error("发送用户关注通知邮件失败");
		}
	}

	/**
	 * 🎉 用户升级通知邮件
	 */
	@Async
	public void sendLevelUpNotification(String addressMail, String newLevelName, String content){
		try {
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);//接收的邮箱
			mail.setSubject("🎉 恭喜升级为【" + newLevelName + "】！");//设置邮件的主题
			mail.setMsg(content);//设置邮件的内容
			mail.send();//发送
			log.info("升级通知邮件发送成功: {}", addressMail);
		} catch (EmailException e) {
			log.error("发送升级通知邮件失败，邮箱: {}，错误: {}", addressMail, e.getMessage(), e);
		}
	}

	/**
	 * 📧 发送增强的通知邮件（包含用户名和内容预览）
	 * @param addressMail 接收邮箱
	 * @param notificationType 通知类型
	 * @param senderUsername 发送者用户名
	 * @param relatedTitle 相关标题（帖子标题或评论内容）
	 * @param commentPreview 评论内容预览
	 */
	@Async
	public void sendEnhancedNotification(String addressMail, Integer notificationType, 
	                                   String senderUsername, String relatedTitle, String commentPreview) {
		try {
			log.info("开始发送增强通知邮件，类型: {}, 发送者: {}, 收件人: {}", notificationType, senderUsername, addressMail);
			
			SimpleEmail mail = configureEmail();
			mail.addTo(addressMail);
			
			// 使用工具类生成邮件主题和内容
			String subject = com.itheima.sbbs.utils.NotificationUtils.generateEmailSubject(notificationType, senderUsername);
			String content = com.itheima.sbbs.utils.NotificationUtils.generateEmailContent(
				notificationType, senderUsername, relatedTitle, commentPreview);
			
			mail.setSubject(subject);
			mail.setMsg(content);
			mail.send();
			
			log.info("增强通知邮件发送成功: {}, 类型: {}", addressMail, notificationType);
		} catch (EmailException e) {
			log.error("发送增强通知邮件失败，邮箱: {}, 类型: {}, 错误: {}", addressMail, notificationType, e.getMessage(), e);
		}
	}

}

