package cn.lwccxy.agentcraft.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.lwccxy.agentcraft.common.BaseResult;
import cn.lwccxy.agentcraft.entity.User;
import cn.lwccxy.agentcraft.entity.dto.LoginDTO;
import cn.lwccxy.agentcraft.entity.dto.SendCodeDTO;
import cn.lwccxy.agentcraft.entity.vo.LoginVO;
import cn.lwccxy.agentcraft.entity.vo.UserInfoVO;
import cn.lwccxy.agentcraft.enums.LoginTypeEnum;
import cn.lwccxy.agentcraft.enums.UserStatusEnum;
import cn.lwccxy.agentcraft.service.LoginGuardService;
import cn.lwccxy.agentcraft.service.UserService;
import cn.lwccxy.agentcraft.service.VerificationCodeService;
import cn.lwccxy.agentcraft.utils.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * 鉴权控制器：登录 / 退出登录（基于 Sa-Token，Cookie 模式）
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** 中国大陆手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    /** 验证码：6 位数字 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    /** 账号密码错误统一提示（避免账号枚举） */
    private static final String LOGIN_FAIL_MSG = "账号或密码错误";

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final LoginGuardService loginGuardService;

    public AuthController(UserService userService,
                          VerificationCodeService verificationCodeService,
                          LoginGuardService loginGuardService) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.loginGuardService = loginGuardService;
    }

    /**
     * 发送短信验证码
     */
    @PostMapping("/send-code")
    public BaseResult<Void> sendCode(@RequestBody @Valid SendCodeDTO dto) {
        verificationCodeService.sendCode(dto.getPhone());
        return BaseResult.newSuccess("验证码已发送");
    }

    /**
     * 统一登录入口
     * <p>type=1：手机号 + 验证码（不存在则自动注册）；type=2：账号(uid/手机号) + 密码。</p>
     */
    @PostMapping("/login")
    public BaseResult<LoginVO> login(@RequestBody @Valid LoginDTO dto, HttpServletRequest request) {
        LoginTypeEnum loginType = LoginTypeEnum.of(dto.getType());
        if (loginType == null) {
            throw new IllegalArgumentException("不支持的登录方式");
        }
        String ip = IpUtil.getClientIp(request);
        String device = IpUtil.getDevice(request);

        User user = switch (loginType) {
            case PHONE_CODE -> loginByCode(dto, ip);
            case PASSWORD -> loginByPassword(dto, ip);
        };

        // 状态校验：禁用用户不允许登录
        if (user.getStatus() == UserStatusEnum.DISABLED) {
            throw new IllegalArgumentException("账号已被禁用，请联系管理员");
        }

        // Sa-Token 登录，Cookie 模式下 token 会自动写入 Cookie
        StpUtil.login(user.getUid());
        userService.recordLoginSuccess(user, ip, device);

        String token = StpUtil.getTokenValue();
        LoginVO vo = new LoginVO(token, UserInfoVO.from(user));
        return BaseResult.newSuccess(vo, "登录成功");
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public BaseResult<Void> logout() {
        StpUtil.logout();
        return BaseResult.newSuccess("已退出登录");
    }

    // ==================== 私有登录逻辑 ====================

    /**
     * 手机号 + 验证码登录（不存在则自动注册）
     */
    private User loginByCode(LoginDTO dto, String ip) {
        String phone = trim(dto.getPhone());
        String code = trim(dto.getCode());
        if (!PHONE_PATTERN.matcher(safe(phone)).matches()) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        if (!CODE_PATTERN.matcher(safe(code)).matches()) {
            throw new IllegalArgumentException("验证码格式不正确");
        }
        loginGuardService.assertNotLocked(phone, ip);

        if (!verificationCodeService.verifyCode(phone, code)) {
            long fails = loginGuardService.recordFailure(phone, ip);
            log.warn("验证码登录失败 phone={} ip={} fails={}", mask(phone), ip, fails);
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        loginGuardService.clearFailure(phone, ip);

        User user = userService.getByPhone(phone);
        if (user == null) {
            // 验证码校验通过且用户不存在 -> 自动注册
            user = userService.registerByPhone(phone);
            log.info("手机号自动注册成功 phone={} uid={}", mask(phone), user.getUid());
        }
        return user;
    }

    /**
     * 账号(uid/手机号) + 密码登录
     */
    private User loginByPassword(LoginDTO dto, String ip) {
        String account = trim(dto.getAccount());
        String password = dto.getPassword();
        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("账号或密码不能为空");
        }
        // 基本长度约束，抵御异常超长输入
        if (account.length() > 32 || password.length() > 64) {
            throw new IllegalArgumentException(LOGIN_FAIL_MSG);
        }
        loginGuardService.assertNotLocked(account, ip);

        User user = userService.getByAccount(account);
        // 用户不存在或密码错误统一返回同一提示，避免账号枚举
        if (user == null || !userService.matchesPassword(password, user)) {
            long fails = loginGuardService.recordFailure(account, ip);
            log.warn("密码登录失败 account={} ip={} fails={}", account, ip, fails);
            throw new IllegalArgumentException(LOGIN_FAIL_MSG);
        }
        loginGuardService.clearFailure(account, ip);
        return user;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String mask(String phone) {
        if (phone == null || phone.length() != 11) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
