package com.itheima.sbbs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.core.lang.UUID;
import com.alibaba.druid.util.StringUtils;
import com.itheima.sbbs.common.Constant;
import com.itheima.sbbs.entity.RegisterDto;
import com.itheima.sbbs.entity.User;
import com.itheima.sbbs.mapper.UserMapper;
import com.itheima.sbbs.service.UserService;
import com.itheima.sbbs.service.ForumStatsService;
import com.itheima.sbbs.utils.SMSUtils;
import com.itheima.sbbs.utils.SaltMD5Util;
import com.itheima.sbbs.utils.ValidateCodeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * 有关用户注册的controller
 */
@Slf4j
@RequestMapping("/v1")
@RestController
public class LoginController {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private ForumStatsService forumStatsService;
    @Autowired
    private SMSUtils smsUtils;


    /**
     * 注册时: 发送验证码
     * 验证码存储在redis中
     */
    @GetMapping("/rcode/{email}")
    public SaResult sendRegisterCode(@PathVariable("email") String email){
        try {
            // 🚀 基本邮箱格式验证
            if (email == null || !email.contains("@")) {
                return SaResult.error("请输入有效的邮箱地址");
            }
            
            // 🚀 检查邮箱是否已注册
            User userByEmail = userService.getUserByEmail(email);
            if (userByEmail != null) {
                return SaResult.error("此邮箱已注册，请直接登录");
            }
            
            // 🚀 检查是否频繁发送验证码（防刷机制）
            String rateLimitKey = "rate_limit:" + email;
            if (redisTemplate.hasKey(rateLimitKey)) {
                return SaResult.error("验证码发送过于频繁，请1分钟后再试");
            }
            
            String vCode = ValidateCodeUtils.generateValidateCode4String(6);
            redisTemplate.opsForValue().set(email, vCode, 5, TimeUnit.MINUTES);//验证码存储在redis中(1个邮箱对应1个验证码)
            
            // 🚀 设置频率限制（1分钟内不能重复发送）
            redisTemplate.opsForValue().set(rateLimitKey, "1", 1, TimeUnit.MINUTES);
            
            log.info("生成注册验证码: {} -> {}", email, vCode);
            // 🚀 异步发送邮件，立即返回成功响应
            smsUtils.sendMessage(email, vCode);
            
            return SaResult.ok("验证码已发送，请查收邮件（可能在垃圾邮件中）");
        } catch (Exception e) {
            log.error("发送注册验证码失败: {}", email, e);
            return SaResult.error("发送验证码失败，请稍后重试");
        }
    }


    /**
     * 普通的注册方式
     * @return
     */
    @PostMapping("/register")
    public SaResult normalRegister(@RequestBody RegisterDto registerDto) {
        //用户名 密码 邮箱(验证码验证) 头像(字符串)
        //检验邮箱有没有注册过
        User userByEmail = userService.getUserByEmail(registerDto.getEmail());
        if (userByEmail != null) {
            return SaResult.error("这个邮箱已经注册过了哦~");
        }
        //比较 验证码 是否正确
        if (registerDto == null || StringUtils.isEmpty(registerDto.getVerificationCode())) {
            return SaResult.error("请不要恶意调试接口");
        }
        String email = registerDto.getEmail();
        String verificationCode = registerDto.getVerificationCode();//验证码
        String code = (String) redisTemplate.opsForValue().get(email);
        if (code == null || !code.equals(verificationCode)) {
            //验证码有问题
            return SaResult.error("验证码错误, 请重新输入");
        } else {
            //验证码没问题 继续进行下一步操作
            redisTemplate.delete(email);//删除redis中的验证码缓存
            User user = new User();
            BeanUtils.copyProperties(registerDto, user);
            user.setGroupId("普通用户");
            user.setPassword(SaltMD5Util.generateSaltPassword(registerDto.getPassword()));
            userMapper.insert(user);
            return SaResult.ok();
        }
    }

    /**
     * 生成图片验证码
     */
    @GetMapping("/captcha")
    @CrossOrigin(exposedHeaders = "captcha-id")
    public void getCaptcha(HttpServletResponse response) throws IOException {
        String uuid = UUID.randomUUID().toString();
        ShearCaptcha shearCaptcha = CaptchaUtil.createShearCaptcha(200, 100, 4, 4);
        //存储无状态验证码:
        redisTemplate.opsForValue().set(Constant.CAPTCHA_PREFIX + uuid, shearCaptcha.getCode(), 5, TimeUnit.MINUTES);
        //返回图片数据
        response.setHeader("Captcha-Id", uuid);
        response.setContentType("image/png");
        shearCaptcha.write(response.getOutputStream());
        response.getOutputStream().flush();
    }


    /**
     * 登录接口
     * 登录完返回token + 用户名 + 用户组
     * 需要比较用户传上来的验证码
     */
    @PostMapping("/login")
    public SaResult login(@RequestBody RegisterDto dto) {
        //确保数据不为空
        if (dto == null || StringUtils.isEmpty(dto.getEmail()) || StringUtils.isEmpty(dto.getPassword()) || StringUtils.isEmpty(dto.getVerificationCode()) || StringUtils.isEmpty(dto.getUuid())) {
            return SaResult.error("请不要恶意调试接口");
        }
        String captchaKey = Constant.CAPTCHA_PREFIX + dto.getUuid();
        String code = (String) redisTemplate.opsForValue().get(captchaKey);
        //比较验证码
        if (StringUtils.isEmpty(code) || !code.equals(dto.getVerificationCode())) {
            return SaResult.error("验证码错误, 请重试");
        }
        //验证码验证无误: 删除验证码缓存，然后开始登录逻辑
        redisTemplate.delete(captchaKey);

        User user = userService.getUserByEmail(dto.getEmail());
        if (user == null) {
            return SaResult.error("账户未注册");
        }
        //用户存在:
        boolean verified = SaltMD5Util.verifySaltPassword(dto.getPassword(), user.getPassword());
        if (verified) {
            //验证成功
            StpUtil.login(user.getId());
            HashMap<String, String> map = new HashMap<>();
            map.put("username", user.getUsername());
            map.put("role", user.getGroupId());
            map.put("email", user.getEmail());
            map.put("token", StpUtil.getTokenInfo().getTokenValue());
            map.put("id", user.getId().toString());
            map.put("avatar", user.getAvatar());
            return SaResult.code(200).data(map);
        } else {
            return SaResult.error("密码输入错误");
        }
    }

    /**
     * 验证token是否有效
     * 前端可以通过此接口检查token是否过期
     * 返回用户基本信息，并使用Redis缓存优化性能
     */
    @SaCheckLogin
    @PostMapping("/validateToken")
    public SaResult validateToken() {
        // 如果能进入这个方法，说明token是有效的
        Object loginId = StpUtil.getLoginId();
        String userId = loginId.toString();
        
        // Redis缓存key
        String cacheKey = "user_info:" + userId;
        
        // 先尝试从Redis缓存获取用户信息
        HashMap<String, Object> userInfo = (HashMap<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        
        if (userInfo == null) {
            // 缓存中没有，从数据库查询
            User user = userMapper.selectById(Integer.parseInt(userId));
            if (user == null) {
                return SaResult.error("用户不存在");
            }
            
            // 构建用户信息
            userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("email", user.getEmail());
            userInfo.put("avatar", user.getAvatar());
            userInfo.put("role", user.getGroupId());
            
            // 缓存24小时
            redisTemplate.opsForValue().set(cacheKey, userInfo, 24, TimeUnit.HOURS);
            log.info("用户信息已缓存: userId={}", userId);
        } else {
            log.debug("从缓存获取用户信息: userId={}", userId);
        }
        
        return SaResult.ok("token有效").setData(userInfo);
    }

    /**
     * 测试管理员接口
     */
    @SaCheckRole("管理员")
    @GetMapping("/admin")
    public SaResult admin() {
        return SaResult.code(200).data("管理员接口测试成功");
    }

}
