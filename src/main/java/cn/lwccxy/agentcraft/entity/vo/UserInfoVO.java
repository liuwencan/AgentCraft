package cn.lwccxy.agentcraft.entity.vo;

import cn.lwccxy.agentcraft.entity.User;
import cn.lwccxy.agentcraft.enums.UserRoleEnum;
import lombok.Data;

/**
 * 用户信息视图对象（对外，不含密码等敏感字段）
 */
@Data
public class UserInfoVO {

    /** 主键ID */
    private Long id;

    /** 昵称（前端展示为 name） */
    private String name;

    /** 账号（业务 uid） */
    private String account;

    /** 手机号（脱敏） */
    private String phone;

    /** 头像 */
    private String avatar;

    /** 角色 */
    private String role;

    public static UserInfoVO from(User user) {
        if (user == null) {
            return null;
        }
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setName(user.getNickname());
        vo.setAccount(user.getUid() == null ? null : String.valueOf(user.getUid()));
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole() == null ? UserRoleEnum.USER.getCode() : user.getRole().getCode());
        return vo;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
