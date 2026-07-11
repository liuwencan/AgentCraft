package cn.lwccxy.agentcraft.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.lwccxy.agentcraft.common.BaseResult;
import cn.lwccxy.agentcraft.context.UserContext;
import cn.lwccxy.agentcraft.entity.User;
import cn.lwccxy.agentcraft.entity.vo.UserInfoVO;
import cn.lwccxy.agentcraft.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 * <p>鉴权由 UserInterceptor 统一拦截，users.id 已写入 UserContext。</p>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public BaseResult<UserInfoVO> info() {
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            return BaseResult.newAuthError();
        }
        User user = userService.getById(userId);
        if (user == null) {
            StpUtil.logout();
            return BaseResult.newAuthError();
        }
        return BaseResult.newSuccess(UserInfoVO.from(user));
    }
}
