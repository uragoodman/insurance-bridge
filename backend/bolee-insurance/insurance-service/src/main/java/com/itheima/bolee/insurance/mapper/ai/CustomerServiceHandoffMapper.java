package com.itheima.bolee.insurance.mapper.ai;

import com.itheima.bolee.insurance.pojo.ai.CustomerServiceChannelDO;
import com.itheima.bolee.insurance.pojo.ai.CustomerServiceRequestDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** Explicitly tenant-scoped persistence for customer-service handoff data. */
@Mapper
public interface CustomerServiceHandoffMapper {

    @Insert("INSERT IGNORE INTO tab_customer_service_request (id, request_no, company_no, owner_user_id, " +
            "client_request_id, request_hash, source_type, channel_id, request_type, reason_code, " +
            "preferred_contact_method, status, submitted_at, expires_at, create_time, update_time, " +
            "create_by, update_by, data_state) VALUES (#{r.id}, #{r.requestNo}, #{r.companyNo}, " +
            "#{r.ownerUserId}, #{r.clientRequestId}, #{r.requestHash}, #{r.sourceType}, #{r.channelId}, " +
            "#{r.requestType}, #{r.reasonCode}, #{r.preferredContactMethod}, 'SUBMITTED', #{r.submittedAt}, " +
            "#{r.expiresAt}, #{r.createTime}, #{r.updateTime}, #{r.createBy}, #{r.updateBy}, '0')")
    int insertRequest(@Param("r") CustomerServiceRequestDO request);

    @Select("SELECT * FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND owner_user_id = #{ownerUserId} AND client_request_id = #{clientRequestId} " +
            "AND data_state = '0' LIMIT 1")
    CustomerServiceRequestDO findByIdempotency(@Param("companyNo") String companyNo,
                                                @Param("ownerUserId") Long ownerUserId,
                                                @Param("clientRequestId") String clientRequestId);

    @Select("SELECT * FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND owner_user_id = #{ownerUserId} AND client_request_id = #{clientRequestId} " +
            "AND data_state = '0' LIMIT 1 FOR UPDATE")
    CustomerServiceRequestDO findByIdempotencyForUpdate(@Param("companyNo") String companyNo,
                                                         @Param("ownerUserId") Long ownerUserId,
                                                         @Param("clientRequestId") String clientRequestId);

    @Select("SELECT * FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND owner_user_id = #{ownerUserId} AND id = #{id} AND data_state = '0' LIMIT 1")
    CustomerServiceRequestDO findOwned(@Param("companyNo") String companyNo,
                                       @Param("ownerUserId") Long ownerUserId,
                                       @Param("id") Long id);

    @Select("SELECT * FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND id = #{id} AND data_state = '0' LIMIT 1")
    CustomerServiceRequestDO findQueueRequest(@Param("companyNo") String companyNo,
                                               @Param("id") Long id);

    @Select("SELECT * FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND owner_user_id = #{ownerUserId} AND data_state = '0' " +
            "ORDER BY submitted_at DESC, id DESC LIMIT #{limit}")
    List<CustomerServiceRequestDO> listOwned(@Param("companyNo") String companyNo,
                                             @Param("ownerUserId") Long ownerUserId,
                                             @Param("limit") int limit);

    @Update("UPDATE tab_customer_service_request SET status = 'CANCELLED', cancelled_at = #{now}, " +
            "update_time = #{now}, update_by = #{ownerUserId} WHERE company_no = #{companyNo} " +
            "AND owner_user_id = #{ownerUserId} AND id = #{id} AND status = 'SUBMITTED' AND data_state = '0'")
    int cancelOwned(@Param("companyNo") String companyNo, @Param("ownerUserId") Long ownerUserId,
                    @Param("id") Long id, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND status = #{status} AND data_state = '0' ORDER BY submitted_at ASC, id ASC LIMIT #{limit}")
    List<CustomerServiceRequestDO> listQueueByStatus(@Param("companyNo") String companyNo,
                                                     @Param("status") String status,
                                                     @Param("limit") int limit);

    @Select("SELECT * FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND data_state = '0' ORDER BY submitted_at ASC, id ASC LIMIT #{limit}")
    List<CustomerServiceRequestDO> listQueue(@Param("companyNo") String companyNo,
                                             @Param("limit") int limit);

    @Update("UPDATE tab_customer_service_request SET status = #{toStatus}, " +
            "assigned_operator_id = CASE WHEN #{toStatus} = 'ACCEPTED' THEN #{operatorId} " +
            "ELSE assigned_operator_id END, accepted_at = CASE WHEN #{toStatus} = 'ACCEPTED' THEN #{now} " +
            "ELSE accepted_at END, resolved_at = CASE WHEN #{toStatus} = 'RESOLVED' THEN #{now} " +
            "ELSE resolved_at END, update_time = #{now}, update_by = #{operatorId} " +
            "WHERE company_no = #{companyNo} AND id = #{id} AND status = #{fromStatus} AND data_state = '0'")
    int transition(@Param("companyNo") String companyNo, @Param("id") Long id,
                   @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                   @Param("operatorId") Long operatorId, @Param("now") LocalDateTime now);

    @Insert("INSERT INTO tab_customer_service_request_event (id, company_no, request_id, owner_user_id, " +
            "actor_type, actor_user_id, event_code, from_status, to_status, create_time, create_by, data_state) " +
            "VALUES (#{id}, #{companyNo}, #{requestId}, #{ownerUserId}, #{actorType}, #{actorUserId}, " +
            "#{eventCode}, #{fromStatus}, #{toStatus}, #{now}, #{actorUserId}, '0')")
    int insertEvent(@Param("id") Long id, @Param("companyNo") String companyNo,
                    @Param("requestId") Long requestId, @Param("ownerUserId") Long ownerUserId,
                    @Param("actorType") String actorType, @Param("actorUserId") Long actorUserId,
                    @Param("eventCode") String eventCode, @Param("fromStatus") String fromStatus,
                    @Param("toStatus") String toStatus, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM tab_customer_service_channel WHERE company_no = #{companyNo} " +
            "AND status = 'ENABLED' AND data_state = '0' " +
            "AND (effective_from IS NULL OR NOT (effective_from > #{now})) " +
            "AND (effective_until IS NULL OR effective_until > #{now}) ORDER BY sort_no, id")
    List<CustomerServiceChannelDO> listAvailableChannels(@Param("companyNo") String companyNo,
                                                         @Param("now") LocalDateTime now);

    @Select("SELECT * FROM tab_customer_service_channel WHERE company_no = #{companyNo} " +
            "AND id = #{id} AND status = 'ENABLED' AND data_state = '0' " +
            "AND (effective_from IS NULL OR NOT (effective_from > #{now})) " +
            "AND (effective_until IS NULL OR effective_until > #{now}) LIMIT 1")
    CustomerServiceChannelDO findAvailableChannel(@Param("companyNo") String companyNo,
                                                   @Param("id") Long id,
                                                   @Param("now") LocalDateTime now);

    @Select("SELECT * FROM tab_customer_service_channel WHERE company_no = #{companyNo} " +
            "AND data_state = '0' ORDER BY sort_no, id")
    List<CustomerServiceChannelDO> listChannelsForAdmin(@Param("companyNo") String companyNo);

    @Insert("INSERT INTO tab_customer_service_channel (id, company_no, channel_code, channel_type, " +
            "display_name, target_value, service_hours_desc, timezone_id, status, sort_no, create_time, " +
            "update_time, create_by, update_by, data_state) VALUES (#{c.id}, #{c.companyNo}, #{c.channelCode}, " +
            "#{c.channelType}, #{c.displayName}, #{c.targetValue}, #{c.serviceHoursDesc}, #{c.timezoneId}, " +
            "#{c.status}, #{c.sortNo}, #{c.createTime}, #{c.updateTime}, #{c.createBy}, #{c.updateBy}, '0') " +
            "ON DUPLICATE KEY UPDATE channel_type = VALUES(channel_type), display_name = VALUES(display_name), " +
            "target_value = VALUES(target_value), service_hours_desc = VALUES(service_hours_desc), " +
            "timezone_id = VALUES(timezone_id), status = VALUES(status), sort_no = VALUES(sort_no), " +
            "update_time = VALUES(update_time), update_by = VALUES(update_by)")
    int upsertChannel(@Param("c") CustomerServiceChannelDO channel);

    @Select("SELECT id, company_no AS companyNo FROM tab_customer_service_request " +
            "WHERE NOT (expires_at > #{now}) AND data_state = '0' ORDER BY expires_at, id LIMIT #{limit}")
    List<CustomerServiceRequestDO> findExpired(@Param("now") LocalDateTime now,
                                               @Param("limit") int limit);

    @org.apache.ibatis.annotations.Delete("DELETE FROM tab_customer_service_request_event WHERE company_no = #{companyNo} " +
            "AND request_id IN (SELECT id FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND NOT (expires_at > #{now}) AND data_state = '0')")
    int deleteExpiredEvents(@Param("companyNo") String companyNo, @Param("now") LocalDateTime now);

    @org.apache.ibatis.annotations.Delete("DELETE FROM tab_customer_service_request WHERE company_no = #{companyNo} " +
            "AND NOT (expires_at > #{now}) AND data_state = '0'")
    int deleteExpiredRequests(@Param("companyNo") String companyNo, @Param("now") LocalDateTime now);
}
