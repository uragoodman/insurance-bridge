package com.itheima.bolee.insurance.mapper.ai;

import com.itheima.bolee.insurance.pojo.ai.AiConversationDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** Owner-scoped persistence operations for AI conversations. */
@Mapper
public interface AiConversationMapper {

    @Insert("INSERT IGNORE INTO tab_ai_conversation (id, owner_user_id, session_id, agno_session_id, " +
            "status, last_message_at, expires_at, deleted_at, create_time, update_time, create_by, update_by, data_state) " +
            "VALUES (#{conversation.id}, #{conversation.ownerUserId}, #{conversation.sessionId}, " +
            "#{conversation.agnoSessionId}, 'ACTIVE', NULL, #{conversation.expiresAt}, NULL, " +
            "#{conversation.createTime}, #{conversation.updateTime}, #{conversation.createBy}, " +
            "#{conversation.updateBy}, '0')")
    int insertIfAbsent(@Param("conversation") AiConversationDO conversation);

    @Select("SELECT * FROM tab_ai_conversation WHERE owner_user_id = #{ownerUserId} " +
            "AND session_id = #{sessionId} AND data_state = '0' LIMIT 1")
    AiConversationDO findOwned(@Param("ownerUserId") Long ownerUserId,
                               @Param("sessionId") String sessionId);

    @Select("SELECT * FROM tab_ai_conversation WHERE owner_user_id = #{ownerUserId} " +
            "AND session_id = #{sessionId} AND data_state = '0' FOR UPDATE")
    AiConversationDO findOwnedForUpdate(@Param("ownerUserId") Long ownerUserId,
                                        @Param("sessionId") String sessionId);

    @Select("SELECT * FROM tab_ai_conversation WHERE id = #{id} " +
            "AND owner_user_id = #{ownerUserId} AND data_state = '0' FOR UPDATE")
    AiConversationDO findOwnedByIdForUpdate(@Param("ownerUserId") Long ownerUserId,
                                            @Param("id") Long id);

    @Select({"<script>",
            "SELECT * FROM tab_ai_conversation WHERE owner_user_id = #{ownerUserId} ",
            "AND status IN ('ACTIVE', 'CLOSED') AND data_state = '0' AND expires_at &gt; #{now} ",
            "<if test='cursorTime != null and cursorId != null'>",
            "AND (COALESCE(last_message_at, create_time) &lt; #{cursorTime} ",
            "OR (COALESCE(last_message_at, create_time) = #{cursorTime} AND id &lt; #{cursorId})) ",
            "</if>",
            "ORDER BY COALESCE(last_message_at, create_time) DESC, id DESC LIMIT #{limit}",
            "</script>"})
    List<AiConversationDO> findReadablePage(@Param("ownerUserId") Long ownerUserId,
                                            @Param("now") LocalDateTime now,
                                            @Param("cursorTime") LocalDateTime cursorTime,
                                            @Param("cursorId") Long cursorId,
                                            @Param("limit") int limit);

    @Update("UPDATE tab_ai_conversation SET last_message_at = #{lastMessageAt}, " +
            "expires_at = #{expiresAt}, update_time = #{lastMessageAt}, update_by = #{ownerUserId} " +
            "WHERE id = #{id} AND owner_user_id = #{ownerUserId} AND status = 'ACTIVE' AND data_state = '0'")
    int updateActivity(@Param("id") Long id,
                       @Param("ownerUserId") Long ownerUserId,
                       @Param("lastMessageAt") LocalDateTime lastMessageAt,
                       @Param("expiresAt") LocalDateTime expiresAt);

    @Update("UPDATE tab_ai_conversation SET status = 'DELETE_PENDING', update_time = #{now}, " +
            "update_by = #{ownerUserId} WHERE id = #{id} AND owner_user_id = #{ownerUserId} " +
            "AND status IN ('ACTIVE', 'CLOSED') AND data_state = '0'")
    int markDeletePending(@Param("id") Long id,
                          @Param("ownerUserId") Long ownerUserId,
                          @Param("now") LocalDateTime now);

    @Select("SELECT * FROM tab_ai_conversation WHERE status IN ('ACTIVE', 'CLOSED') " +
            "AND expires_at <= #{now} AND data_state = '0' ORDER BY expires_at, id LIMIT #{limit} FOR UPDATE")
    List<AiConversationDO> findExpiredForUpdate(@Param("now") LocalDateTime now,
                                                @Param("limit") int limit);

    @Delete("DELETE FROM tab_ai_conversation WHERE id = #{id} " +
            "AND owner_user_id = #{ownerUserId} AND status = 'DELETE_PENDING' AND data_state = '0'")
    int deletePendingOwned(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);

    @Delete("DELETE FROM tab_ai_conversation WHERE agno_session_id = #{agnoSessionId}")
    int deleteByAgnoSessionId(@Param("agnoSessionId") String agnoSessionId);
}
