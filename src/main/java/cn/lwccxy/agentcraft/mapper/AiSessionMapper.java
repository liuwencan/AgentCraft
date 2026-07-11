package cn.lwccxy.agentcraft.mapper;

import cn.lwccxy.agentcraft.entity.AiSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI会话 Mapper 接口
 */
@Mapper
public interface AiSessionMapper extends BaseMapper<AiSession> {

    /**
     * 分页查询当前用户的会话首条记录列表
     *
     * @param page   分页对象
     * @param userId 当前登录用户 users.id，传 {@code null} 表示不过滤
     */
    @Select("""
            <script>
            SELECT s1.* FROM ai_session s1
            WHERE s1.id = (SELECT s2.id FROM ai_session s2
                           WHERE s2.session_id = s1.session_id
                           ORDER BY s2.create_time ASC LIMIT 1)
            <if test="userId != null">
              AND s1.userId = #{userId}
            </if>
            ORDER BY s1.update_time DESC
            </script>
            """)
    IPage<AiSession> selectSessionListWithFirstRecord(Page<AiSession> page, @Param("userId") Long userId);
}
