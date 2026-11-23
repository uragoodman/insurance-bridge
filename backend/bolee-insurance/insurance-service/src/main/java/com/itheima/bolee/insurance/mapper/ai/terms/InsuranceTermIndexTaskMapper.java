package com.itheima.bolee.insurance.mapper.ai.terms;

import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermIndexTaskDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InsuranceTermIndexTaskMapper {

    @Insert("INSERT INTO tab_insurance_term_index_task "
            + "(id, source_id, action, source_sha256, task_status, attempts, next_attempt_at, create_time, update_time) "
            + "VALUES (#{task.id}, #{task.sourceId}, #{task.action}, #{task.sourceSha256}, 'PENDING', 0, "
            + "#{task.nextAttemptAt}, #{task.createTime}, #{task.updateTime})")
    int insert(@Param("task") InsuranceTermIndexTaskDO task);

    @Select("SELECT id, source_id, action, source_sha256, task_status, attempts, next_attempt_at, lease_until, "
            + "last_error, lease_token, create_time, update_time FROM tab_insurance_term_index_task "
            + "WHERE ((task_status IN ('PENDING', 'RETRY') AND next_attempt_at <= #{now}) "
            + "OR (task_status = 'PROCESSING' AND lease_until < #{now})) ORDER BY id ASC LIMIT 1")
    @Results(id = "termIndexTaskResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "source_id", property = "sourceId"),
            @Result(column = "action", property = "action"),
            @Result(column = "source_sha256", property = "sourceSha256"),
            @Result(column = "task_status", property = "taskStatus"),
            @Result(column = "attempts", property = "attempts"),
            @Result(column = "next_attempt_at", property = "nextAttemptAt"),
            @Result(column = "lease_until", property = "leaseUntil"),
            @Result(column = "lease_token", property = "leaseToken"),
            @Result(column = "last_error", property = "lastError"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    InsuranceTermIndexTaskDO findNextClaimable(@Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_index_task SET task_status = 'PROCESSING', attempts = attempts + 1, "
            + "lease_until = #{leaseUntil}, lease_token = #{leaseToken}, update_time = #{now} WHERE id = #{id} "
            + "AND ((task_status IN ('PENDING', 'RETRY') AND next_attempt_at <= #{now}) "
            + "OR (task_status = 'PROCESSING' AND lease_until < #{now}))")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil, @Param("leaseToken") String leaseToken);

    @Select("SELECT id, source_id, action, source_sha256, task_status, attempts, next_attempt_at, lease_until, "
            + "last_error, lease_token, create_time, update_time FROM tab_insurance_term_index_task WHERE id = #{id} LIMIT 1")
    @Results(id = "termIndexTaskReadResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "source_id", property = "sourceId"),
            @Result(column = "action", property = "action"),
            @Result(column = "source_sha256", property = "sourceSha256"),
            @Result(column = "task_status", property = "taskStatus"),
            @Result(column = "attempts", property = "attempts"),
            @Result(column = "next_attempt_at", property = "nextAttemptAt"),
            @Result(column = "lease_until", property = "leaseUntil"),
            @Result(column = "lease_token", property = "leaseToken"),
            @Result(column = "last_error", property = "lastError"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    InsuranceTermIndexTaskDO findById(@Param("id") Long id);

    @Select("SELECT id, source_id, action, source_sha256, task_status, attempts, next_attempt_at, lease_until, "
            + "last_error, lease_token, create_time, update_time FROM tab_insurance_term_index_task "
            + "WHERE id = #{id} LIMIT 1 FOR UPDATE")
    @ResultMap("termIndexTaskReadResultMap")
    InsuranceTermIndexTaskDO findByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE tab_insurance_term_index_task SET task_status = 'DONE', lease_until = NULL, "
            + "last_error = NULL, update_time = #{now} WHERE id = #{id} AND task_status = 'PROCESSING' "
            + "AND lease_token = #{leaseToken} AND lease_until >= #{now}")
    int complete(@Param("id") Long id, @Param("leaseToken") String leaseToken, @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_index_task SET task_status = #{status}, lease_until = NULL, "
            + "next_attempt_at = #{nextAttemptAt}, last_error = #{reason}, lease_token = NULL, update_time = #{now} "
            + "WHERE id = #{id} AND task_status = 'PROCESSING' AND lease_token = #{leaseToken} AND lease_until >= #{now}")
    int fail(@Param("id") Long id, @Param("status") String status,
             @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
             @Param("reason") String reason, @Param("leaseToken") String leaseToken,
             @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_index_task SET lease_until = #{leaseUntil}, update_time = #{now} "
            + "WHERE id = #{id} AND task_status = 'PROCESSING' AND lease_token = #{leaseToken} "
            + "AND lease_until >= #{now}")
    int heartbeat(@Param("id") Long id, @Param("leaseToken") String leaseToken,
                  @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Select("SELECT id, source_id, action, source_sha256, task_status, attempts, next_attempt_at, lease_until, "
            + "last_error, lease_token, create_time, update_time FROM tab_insurance_term_index_task "
            + "WHERE task_status = 'PROCESSING' AND lease_until < #{now} AND attempts >= #{maxAttempts} "
            + "ORDER BY id ASC LIMIT 1")
    @ResultMap("termIndexTaskReadResultMap")
    InsuranceTermIndexTaskDO findNextExhaustedLease(@Param("now") LocalDateTime now,
                                                    @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE tab_insurance_term_index_task SET task_status = 'FAILED', lease_until = NULL, "
            + "lease_token = NULL, last_error = #{reason}, update_time = #{now} WHERE id = #{id} "
            + "AND task_status = 'PROCESSING' AND lease_until < #{now} AND attempts >= #{maxAttempts}")
    int failExpiredLease(@Param("id") Long id, @Param("now") LocalDateTime now,
                         @Param("maxAttempts") int maxAttempts, @Param("reason") String reason);
}
