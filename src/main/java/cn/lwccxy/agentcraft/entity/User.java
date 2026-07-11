package cn.lwccxy.agentcraft.entity;

import cn.lwccxy.agentcraft.enums.UserRoleEnum;
import cn.lwccxy.agentcraft.enums.UserStatusEnum;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库表 users
 */
@Data
@TableName("users")
public class User {

    /**
     * 主键ID（数据库自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务用户ID（对外展示 / 登录账号），唯一
     */
    @TableField("uid")
    private Long uid;

    /**
     * 密码（BCrypt 加密存储），不对外序列化
     */
    @JsonIgnore
    @TableField("password")
    private String password;

    /**
     * 昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 头像
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 最近登录IP
     */
    @TableField("last_login_ip")
    private String lastLoginIp;

    /**
     * 最近登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 累计登录次数
     */
    @TableField("login_count")
    private Long loginCount;

    /**
     * 最近登录设备
     */
    @TableField("last_login_device")
    private String lastLoginDevice;

    /**
     * 状态：0=禁用，1=正常
     */
    @TableField("status")
    private UserStatusEnum status;

    /**
     * 角色：USER / ADMIN
     */
    @TableField("role")
    private UserRoleEnum role;

    /**
     * 创建时间（DB 默认 CURRENT_TIMESTAMP）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（DB 默认 ON UPDATE CURRENT_TIMESTAMP）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记（bit：0=未删，1=已删）
     */
    @JsonIgnore
    @TableLogic
    @TableField("is_deleted")
    private Boolean deleted;
}
