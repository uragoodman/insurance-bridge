package com.itheima.bolee.insurance.mapper.ai;

import com.itheima.bolee.insurance.pojo.ai.AiTurnDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** Owner-scoped idempotency and lifecycle persistence for AI turns. */
@Mapper
public interface AiTurnMapper {

    @Select("SELECT * FROM tab_ai_turn WHERE owner_user_id = #{ownerUserId} " +
            "AND conversation_id = #{conversationId} AND client_message_id = #{clientMessageId} " +
            "AND data_state = '0' LIMIT 1")
    AiTurnDO findByIdempotencyKey(@Param("ownerUserId") Long ownerUserId,
                                  @Param("conversationId") Long conversationId,
                                  @Param("clientMessageId") String clientMessageId);

    @Select("SELECT * FROM tab_ai_turn WHERE owner_user_id = #{ownerUserId} " +
            "AND turn_id = #{turnId} AND data_state = '0' LIMIT 1")
    AiTurnDO findByOwnerAndTurnId(@Param("ownerUserId") Long ownerUserId,
                                  @Param("turnId") String turnId);

    @Select({"<script>",
            "SELECT turn_id, agno_run_id, encrypted_response FROM tab_ai_turn WHERE owner_user_id = #{ownerUserId} ",
            "AND conversation_id = #{conversationId} AND status = 'COMPLETED' AND data_state = '0' ",
            "AND agno_run_id IN ",
            "<foreach collection='runIds' item='runId' open='(' separator=',' close=')'>",
            "#{runId}",
            "</foreach>",
            "</script>"})
    @Results({
            @Result(column = "turn_id", property = "turnId"),
            @Result(column = "agno_run_id", property = "agnoRunId"),
            @Result(column = "encrypted_response", property = "encryptedResponse")
    })
    List<AiTurnDO> findCompletedByAgnoRunIds(@Param("ownerUserId") Long ownerUserId,
                                             @Param("conversationId") Long conversationId,
                                             @Param("runIds") List<String> runIds);

    @Insert("INSERT IGNORE INTO tab_ai_turn (id, owner_user_id, conversation_id, turn_id, " +
            "client_message_id, request_hash, agno_run_id, encrypted_response, trace_id, status, failure_code, " +
            "started_at, completed_at, expires_at, create_time, update_time, create_by, update_by, data_state) " +
            "VALUES (#{turn.id}, #{turn.ownerUserId}, #{turn.conversationId}, #{turn.turnId}, " +
            "#{turn.clientMessageId}, #{turn.requestHash}, #{turn.agnoRunId}, NULL, #{turn.traceId}, " +
            "'PROCESSING', NULL, #{turn.startedAt}, NULL, #{turn.expiresAt}, #{turn.createTime}, " +
            "#{turn.updateTime}, #{turn.createBy}, #{turn.updateBy}, '0')")
    int insertIfAbsent(@Param("turn") AiTurnDO turn);

    @Update("UPDATE tab_ai_turn SET status = 'COMPLETED', agno_run_id = #{agnoRunId}, " +
            "encrypted_response = #{encryptedResponse}, " +
            "completed_at = #{completedAt}, update_time = #{completedAt}, update_by = #{ownerUserId} " +
            "WHERE owner_user_id = #{ownerUserId} AND turn_id = #{turnId} " +
            "AND status = 'PROCESSING' AND data_state = '0'")
    int completeProcessing(@Param("ownerUserId") Long ownerUserId,
                           @Param("turnId") String turnId,
                           @Param("agnoRunId") String agnoRunId,
                           @Param("encryptedResponse") String encryptedResponse,
                           @Param("completedAt") LocalDateTime completedAt);

    @Update("UPDATE tab_ai_turn SET status = 'FAILED', failure_code = #{failureCode}, " +
            "completed_at = #{failedAt}, update_time = #{failedAt}, update_by = #{ownerUserId} " +
            "WHERE owner_user_id = #{ownerUserId} AND turn_id = #{turnId} " +
            "AND status = 'PROCESSING' AND data_state = '0'")
    int failProcessing(@Param("ownerUserId") Long ownerUserId,
                       @Param("turnId") String turnId,
                       @Param("failureCode") String failureCode,
                       @Param("failedAt") LocalDateTime failedAt);

    @Update("UPDATE tab_ai_turn t INNER JOIN tab_ai_conversation c " +
            "ON c.id = t.conversation_id AND c.owner_user_id = t.owner_user_id " +
            "SET t.status = 'FAILED', t.failure_code = 'CLEANUP_TIMEOUT', " +
            "t.completed_at = #{now}, t.update_time = #{now}, t.update_by = t.owner_user_id " +
            "WHERE c.status = 'DELETE_PENDING' AND t.status = 'PROCESSING' " +
            "AND t.started_at <= #{cutoff} AND t.data_state = '0'")
    int failAbandonedProcessingForPendingDeletes(@Param("cutoff") LocalDateTime cutoff,
                                                 @Param("now") LocalDateTime now);

    @Delete("DELETE FROM tab_ai_turn WHERE conversation_id = #{conversationId} " +
            "AND owner_user_id = #{ownerUserId} AND data_state = '0'")
    int deleteForConversation(@Param("conversationId") Long conversationId,
                              @Param("ownerUserId") Long ownerUserId);

    @Delete("DELETE t FROM tab_ai_turn t INNER JOIN tab_ai_conversation c ON c.id = t.conversation_id " +
            "WHERE c.agno_session_id = #{agnoSessionId}")
    int deleteByAgnoSessionId(@Param("agnoSessionId") String agnoSessionId);
}
