package cn.lwccxy.agentcraft.mapper;

import cn.lwccxy.agentcraft.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
