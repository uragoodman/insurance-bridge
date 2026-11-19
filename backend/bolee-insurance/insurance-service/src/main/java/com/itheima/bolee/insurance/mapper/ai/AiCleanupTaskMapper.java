package com.itheima.bolee.insurance.mapper.ai;

import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** Durable cleanup outbox. Claims use row locks plus a lease for MySQL 5.7 multi-instance safety. */
@Mapper
public interface AiCleanupTaskMapper {

    @Insert("INSERT IGNORE INTO tab_ai_cleanup_task (id, owner_user_id, conversation_id, " +
            "agno_session_id, status, attempt_count, next_attempt_at, last_attempt_at, lease_until, " +
            "completed_at, expires_at, last_error_code, create_time, update_time, create_by, update_by, data_state) " +
            "VALUES (#{task.id}, #{task.ownerUserId}, #{task.conversationId}, #{task.agnoSessionId}, " +
            "'PENDING', 0, NULL, NULL, NULL, NULL, NULL, NULL, #{task.createTime}, " +
            "#{task.updateTime}, #{task.createBy}, #{task.updateBy}, '0')")
    int insertIfAbsent(@Param("task") AiCleanupTaskDO task);

    @Select("SELECT * FROM tab_ai_cleanup_task WHERE id = #{id}")
    AiCleanupTaskDO findById(@Param("id") Long id);

    @Select("SELECT * FROM tab_ai_cleanup_task WHERE (" +
            "status = 'PENDING' OR (status = 'RETRY_WAIT' AND next_attempt_at <= #{now}) " +
            "OR (status = 'PROCESSING' AND lease_until <= #{now}) " +
            ") AND NOT EXISTS (SELECT 1 FROM tab_ai_turn t WHERE " +
            "t.conversation_id = tab_ai_cleanup_task.conversation_id " +
            "AND t.owner_user_id = tab_ai_cleanup_task.owner_user_id " +
            "AND t.status = 'PROCESSING' AND t.data_state = '0') " +
            "ORDER BY COALESCE(next_attempt_at, create_time), id LIMIT 1 FOR UPDATE")
    AiCleanupTaskDO findNextClaimableForUpdate(@Param("now") LocalDateTime now);

    @Update("UPDATE tab_ai_cleanup_task SET status = 'PROCESSING', attempt_count = attempt_count + 1, " +
            "last_attempt_at = #{now}, lease_until = #{leaseUntil}, next_attempt_at = NULL, " +
            "update_time = #{now} WHERE id = #{id} AND (status = 'PENDING' " +
            "OR (status = 'RETRY_WAIT' AND next_attempt_at <= #{now}) " +
            "OR (status = 'PROCESSING' AND lease_until <= #{now}))")
    int claim(@Param("id") Long id,
              @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("UPDATE tab_ai_cleanup_task SET status = #{status}, next_attempt_at = #{nextAttemptAt}, " +
            "lease_until = NULL, last_error_code = #{errorCode}, update_time = #{now} " +
            "WHERE id = #{id} AND status = 'PROCESSING'")
    int scheduleRetry(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                      @Param("errorCode") String errorCode,
                      @Param("now") LocalDateTime now);

    @Update("UPDATE tab_ai_cleanup_task SET status = 'COMPLETED', completed_at = #{now}, " +
            "expires_at = #{expiresAt}, lease_until = NULL, next_attempt_at = NULL, " +
            "last_error_code = NULL, update_time = #{now} WHERE id = #{id} AND status = 'PROCESSING'")
    int markCompleted(@Param("id") Long id,
                      @Param("now") LocalDateTime now,
                      @Param("expiresAt") LocalDateTime expiresAt);

    @Select("SELECT * FROM tab_ai_cleanup_task WHERE status = 'COMPLETED' " +
            "AND expires_at <= #{now} ORDER BY expires_at, id LIMIT #{limit}")
    List<AiCleanupTaskDO> findExpiredCompleted(@Param("now") LocalDateTime now,
                                               @Param("limit") int limit);

    @Delete("DELETE FROM tab_ai_cleanup_task WHERE id = #{id} AND status = 'COMPLETED' AND expires_at <= #{now}")
    int deleteExpiredCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Delete("DELETE FROM tab_ai_cleanup_task WHERE agno_session_id = #{agnoSessionId}")
    int deleteByAgnoSessionId(@Param("agnoSessionId") String agnoSessionId);
}
