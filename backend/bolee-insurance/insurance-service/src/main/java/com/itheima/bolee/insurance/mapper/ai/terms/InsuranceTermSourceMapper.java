package com.itheima.bolee.insurance.mapper.ai.terms;

import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermProductVersionVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InsuranceTermSourceMapper {

    @Select("SELECT product_version FROM tab_ai_product_profile_version "
            + "WHERE id = #{profileVersionId} AND product_id = #{insuranceId} AND data_state = '0' LIMIT 1")
    Integer findProductVersion(@Param("insuranceId") Long insuranceId,
                               @Param("profileVersionId") Long profileVersionId);

    @Insert("INSERT INTO tab_insurance_term_source "
            + "(id, insurance_id, profile_version_id, product_version, source_type, title, source_uri, "
            + "original_file_name, content_type, file_size, sha256, storage_provider, bucket_name, object_key, "
            + "status, effective_from, effective_to, uploaded_by, create_time, update_time, create_by, update_by, data_state) "
            + "VALUES (#{source.id}, #{source.insuranceId}, #{source.profileVersionId}, #{source.productVersion}, "
            + "#{source.sourceType}, #{source.title}, #{source.sourceUri}, #{source.originalFileName}, "
            + "#{source.contentType}, #{source.fileSize}, #{source.sha256}, #{source.storageProvider}, "
            + "#{source.bucketName}, #{source.objectKey}, #{source.status}, #{source.effectiveFrom}, "
            + "#{source.effectiveTo}, #{source.uploadedBy}, #{source.createTime}, #{source.updateTime}, "
            + "#{source.createBy}, #{source.updateBy}, '0')")
    int insertSource(@Param("source") InsuranceTermSourceDO source);

    @Select("SELECT * FROM tab_insurance_term_source WHERE id = #{id} AND data_state = '0' LIMIT 1")
    @Results(id = "insuranceTermSourceResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "insurance_id", property = "insuranceId"),
            @Result(column = "profile_version_id", property = "profileVersionId"),
            @Result(column = "product_version", property = "productVersion"),
            @Result(column = "source_type", property = "sourceType"),
            @Result(column = "title", property = "title"),
            @Result(column = "source_uri", property = "sourceUri"),
            @Result(column = "original_file_name", property = "originalFileName"),
            @Result(column = "content_type", property = "contentType"),
            @Result(column = "file_size", property = "fileSize"),
            @Result(column = "sha256", property = "sha256"),
            @Result(column = "storage_provider", property = "storageProvider"),
            @Result(column = "bucket_name", property = "bucketName"),
            @Result(column = "object_key", property = "objectKey"),
            @Result(column = "status", property = "status"),
            @Result(column = "effective_from", property = "effectiveFrom"),
            @Result(column = "effective_to", property = "effectiveTo"),
            @Result(column = "uploaded_by", property = "uploadedBy"),
            @Result(column = "submitted_by", property = "submittedBy"),
            @Result(column = "submitted_at", property = "submittedAt"),
            @Result(column = "reviewed_by", property = "reviewedBy"),
            @Result(column = "reviewed_at", property = "reviewedAt"),
            @Result(column = "review_note", property = "reviewNote"),
            @Result(column = "published_by", property = "publishedBy"),
            @Result(column = "published_at", property = "publishedAt"),
            @Result(column = "withdrawn_by", property = "withdrawnBy"),
            @Result(column = "withdrawn_at", property = "withdrawnAt"),
            @Result(column = "withdrawal_reason", property = "withdrawalReason"),
            @Result(column = "index_status", property = "indexStatus"),
            @Result(column = "indexed_sha256", property = "indexedSha256"),
            @Result(column = "index_error", property = "indexError"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "create_by", property = "createBy"),
            @Result(column = "update_by", property = "updateBy"),
            @Result(column = "data_state", property = "dataState")
    })
    InsuranceTermSourceDO findById(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM tab_insurance_term_source WHERE data_state = '0' "
            + "AND (#{insuranceId} IS NULL OR insurance_id = #{insuranceId}) "
            + "AND (#{profileVersionId} IS NULL OR profile_version_id = #{profileVersionId}) "
            + "AND (#{status} IS NULL OR status = #{status})")
    long countSources(@Param("insuranceId") Long insuranceId,
                      @Param("profileVersionId") Long profileVersionId,
                      @Param("status") String status);

    @Select("SELECT * FROM tab_insurance_term_source WHERE data_state = '0' "
            + "AND (#{insuranceId} IS NULL OR insurance_id = #{insuranceId}) "
            + "AND (#{profileVersionId} IS NULL OR profile_version_id = #{profileVersionId}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "ORDER BY create_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    @ResultMap("insuranceTermSourceResultMap")
    List<InsuranceTermSourceDO> listSources(@Param("insuranceId") Long insuranceId,
                                            @Param("profileVersionId") Long profileVersionId,
                                            @Param("status") String status,
                                            @Param("limit") int limit,
                                            @Param("offset") long offset);

    @Update("UPDATE tab_insurance_term_source SET status = 'PENDING_REVIEW', submitted_by = #{userId}, "
            + "submitted_at = #{now}, update_by = #{userId}, update_time = #{now} "
            + "WHERE id = #{id} AND status IN ('DRAFT', 'REJECTED') AND data_state = '0'")
    int submitForReview(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET status = #{nextStatus}, reviewed_by = #{userId}, "
            + "reviewed_at = #{now}, review_note = #{note}, update_by = #{userId}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'PENDING_REVIEW' AND data_state = '0'")
    int review(@Param("id") Long id, @Param("nextStatus") String nextStatus,
               @Param("note") String note, @Param("userId") Long userId,
               @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET status = 'PUBLISHING', index_status = 'INDEXING', "
            + "index_error = NULL, published_by = #{userId}, published_at = #{now}, "
            + "update_by = #{userId}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'APPROVED' AND data_state = '0' "
            + "AND EXISTS (SELECT 1 FROM tab_ai_product_profile_version pv "
            + "WHERE pv.id = tab_insurance_term_source.profile_version_id "
            + "AND pv.product_id = tab_insurance_term_source.insurance_id AND pv.status = 'PUBLISHED' "
            + "AND pv.data_state = '0' AND (pv.effective_from IS NULL OR pv.effective_from <= #{now}) "
            + "AND (pv.effective_to IS NULL OR pv.effective_to >= #{now}))")
    int publish(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET status = 'WITHDRAWN', index_status = 'REMOVAL_PENDING', "
            + "withdrawn_by = #{userId}, "
            + "withdrawn_at = #{now}, withdrawal_reason = #{reason}, update_by = #{userId}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'PUBLISHED' AND data_state = '0'")
    int withdraw(@Param("id") Long id, @Param("reason") String reason,
                 @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET title = #{title}, source_uri = #{sourceUri}, "
            + "effective_from = #{effectiveFrom}, effective_to = #{effectiveTo}, update_by = #{userId}, "
            + "update_time = #{now} WHERE id = #{id} AND status IN ('DRAFT', 'REJECTED') AND data_state = '0'")
    int updateDraft(@Param("id") Long id, @Param("title") String title,
                    @Param("sourceUri") String sourceUri, @Param("effectiveFrom") java.time.LocalDate effectiveFrom,
                    @Param("effectiveTo") java.time.LocalDate effectiveTo,
                    @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET data_state = '1', update_by = #{userId}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'DELETING' AND data_state = '0'")
    int softDeleteDraft(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET status = 'DELETING', update_by = #{userId}, update_time = #{now} "
            + "WHERE id = #{id} AND status IN ('DRAFT', 'REJECTED') AND data_state = '0'")
    int reserveDraftDeletion(@Param("id") Long id, @Param("userId") Long userId,
                             @Param("now") LocalDateTime now);

    @Select("SELECT * FROM tab_insurance_term_source WHERE status = 'DELETING' AND data_state = '0' "
            + "ORDER BY update_time ASC, id ASC LIMIT #{limit}")
    @ResultMap("insuranceTermSourceResultMap")
    List<InsuranceTermSourceDO> findDeleting(@Param("limit") int limit);

    @Select("SELECT ts.* FROM tab_insurance_term_source ts "
            + "JOIN tab_ai_product_profile_version pv ON pv.id = ts.profile_version_id "
            + "AND pv.product_id = ts.insurance_id AND pv.status = 'PUBLISHED' AND pv.data_state = '0' "
            + "AND (pv.effective_from IS NULL OR pv.effective_from <= CURRENT_TIMESTAMP) "
            + "AND (pv.effective_to IS NULL OR pv.effective_to >= CURRENT_TIMESTAMP) "
            + "WHERE ts.insurance_id = #{insuranceId} "
            + "AND (#{profileVersionId} IS NULL OR ts.profile_version_id = #{profileVersionId}) "
            + "AND ts.status = 'PUBLISHED' AND (ts.source_type = 'OFFICIAL_PRODUCT_TERM' "
            + "OR (#{includeSynthetic} = 1 AND ts.source_type = 'SYNTHETIC')) AND ts.data_state = '0' "
            + "AND (ts.effective_from IS NULL OR ts.effective_from <= CURRENT_DATE()) "
            + "AND (ts.effective_to IS NULL OR ts.effective_to >= CURRENT_DATE()) "
            + "AND ts.index_status = 'INDEXED' AND ts.indexed_sha256 = ts.sha256 "
            + "ORDER BY ts.profile_version_id DESC, ts.id DESC")
    @ResultMap("insuranceTermSourceResultMap")
    List<InsuranceTermSourceDO> listActivePublished(@Param("insuranceId") Long insuranceId,
                                                    @Param("profileVersionId") Long profileVersionId,
                                                    @Param("includeSynthetic") boolean includeSynthetic);

    @Select("SELECT DISTINCT pv.id AS profile_version_id, pv.product_version, "
            + "pv.effective_from, pv.effective_to FROM tab_ai_product_profile_version pv "
            + "JOIN tab_insurance_term_source ts ON ts.profile_version_id = pv.id "
            + "AND ts.insurance_id = pv.product_id AND ts.status = 'PUBLISHED' AND ts.data_state = '0' "
            + "AND (ts.source_type = 'OFFICIAL_PRODUCT_TERM' "
            + "OR (#{includeSynthetic} = 1 AND ts.source_type = 'SYNTHETIC')) "
            + "AND ts.index_status = 'INDEXED' AND ts.indexed_sha256 = ts.sha256 "
            + "AND (ts.effective_from IS NULL OR ts.effective_from <= CURRENT_DATE()) "
            + "AND (ts.effective_to IS NULL OR ts.effective_to >= CURRENT_DATE()) "
            + "WHERE pv.product_id = #{insuranceId} AND pv.status = 'PUBLISHED' AND pv.data_state = '0' "
            + "AND (pv.effective_from IS NULL OR pv.effective_from <= CURRENT_TIMESTAMP) "
            + "AND (pv.effective_to IS NULL OR pv.effective_to >= CURRENT_TIMESTAMP) "
            + "ORDER BY pv.product_version DESC")
    @Results(id = "insuranceTermProductVersionResultMap", value = {
            @Result(column = "profile_version_id", property = "profileVersionId", id = true),
            @Result(column = "product_version", property = "productVersion"),
            @Result(column = "effective_from", property = "effectiveFrom"),
            @Result(column = "effective_to", property = "effectiveTo")
    })
    List<InsuranceTermProductVersionVO> listActivePublishedVersions(@Param("insuranceId") Long insuranceId,
                                                                    @Param("includeSynthetic") boolean includeSynthetic);

    @Select("SELECT COUNT(1) FROM tab_insurance_term_source ts "
            + "JOIN tab_ai_product_profile_version pv ON pv.id = ts.profile_version_id "
            + "AND pv.product_id = ts.insurance_id AND pv.status = 'PUBLISHED' AND pv.data_state = '0' "
            + "AND (pv.effective_from IS NULL OR pv.effective_from <= CURRENT_TIMESTAMP) "
            + "AND (pv.effective_to IS NULL OR pv.effective_to >= CURRENT_TIMESTAMP) "
            + "WHERE ts.id = #{sourceId} AND ts.insurance_id = #{insuranceId} "
            + "AND ts.profile_version_id = #{profileVersionId} AND ts.status = 'PUBLISHED' "
            + "AND (ts.source_type = 'OFFICIAL_PRODUCT_TERM' "
            + "OR (#{includeSynthetic} = 1 AND ts.source_type = 'SYNTHETIC')) AND ts.data_state = '0' "
            + "AND ts.index_status = 'INDEXED' AND ts.indexed_sha256 = ts.sha256 "
            + "AND (ts.effective_from IS NULL OR ts.effective_from <= CURRENT_DATE()) "
            + "AND (ts.effective_to IS NULL OR ts.effective_to >= CURRENT_DATE())")
    int isActivePublished(@Param("sourceId") Long sourceId, @Param("insuranceId") Long insuranceId,
                          @Param("profileVersionId") Long profileVersionId,
                          @Param("includeSynthetic") boolean includeSynthetic);

    @Update("UPDATE tab_insurance_term_source SET index_status = 'FAILED', index_error = #{reason}, "
            + "update_time = #{now} WHERE id = #{sourceId} AND status = 'PUBLISHING' AND sha256 = #{sha256} AND data_state = '0'")
    int markIndexFailed(@Param("sourceId") Long sourceId, @Param("sha256") String sha256,
                        @Param("reason") String reason, @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET index_status = 'REMOVED', index_error = NULL, update_time = #{now} "
            + "WHERE id = #{sourceId} AND status = 'WITHDRAWN' AND sha256 = #{sha256} AND data_state = '0'")
    int markIndexRemoved(@Param("sourceId") Long sourceId, @Param("sha256") String sha256,
                         @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET index_status = 'FAILED', index_error = #{reason}, "
            + "update_time = #{now} WHERE id = #{sourceId} AND status = 'WITHDRAWN' AND sha256 = #{sha256} AND data_state = '0'")
    int markIndexRemovalFailed(@Param("sourceId") Long sourceId, @Param("sha256") String sha256,
                               @Param("reason") String reason, @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET index_status = CASE WHEN status = 'PUBLISHING' "
            + "THEN 'INDEXING' ELSE 'REMOVAL_PENDING' END, index_error = NULL, update_by = #{userId}, "
            + "update_time = #{now} WHERE id = #{id} AND status IN ('PUBLISHING', 'WITHDRAWN') "
            + "AND index_status = 'FAILED' AND data_state = '0'")
    int retryIndexSync(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT ts.* FROM tab_insurance_term_source ts "
            + "JOIN tab_ai_product_profile_version pv ON pv.id = ts.profile_version_id "
            + "AND pv.product_id = ts.insurance_id WHERE ts.status IN ('PUBLISHING', 'PUBLISHED') "
            + "AND ts.data_state = '0' AND ts.index_status IN ('INDEXING', 'INDEXED', 'FAILED') "
            + "AND (ts.effective_to IS NOT NULL AND ts.effective_to < DATE(#{now}) "
            + "OR pv.status <> 'PUBLISHED' OR pv.data_state <> '0' "
            + "OR (pv.effective_to IS NOT NULL AND pv.effective_to < #{now})) "
            + "ORDER BY ts.id ASC LIMIT #{limit}")
    @ResultMap("insuranceTermSourceResultMap")
    List<InsuranceTermSourceDO> findSourcesNeedingExpiryCleanup(@Param("now") LocalDateTime now,
                                                                @Param("limit") int limit);

    @Update("UPDATE tab_insurance_term_source SET status = 'WITHDRAWN', "
            + "index_status = 'REMOVAL_PENDING', withdrawn_at = #{now}, "
            + "withdrawal_reason = 'Effective period ended', update_time = #{now} "
            + "WHERE id = #{id} AND status IN ('PUBLISHING', 'PUBLISHED') AND data_state = '0' "
            + "AND (effective_to IS NOT NULL AND effective_to < DATE(#{now}) OR NOT EXISTS "
            + "(SELECT 1 FROM tab_ai_product_profile_version pv WHERE pv.id = tab_insurance_term_source.profile_version_id "
            + "AND pv.product_id = tab_insurance_term_source.insurance_id AND pv.status = 'PUBLISHED' "
            + "AND pv.data_state = '0' AND (pv.effective_to IS NULL OR pv.effective_to >= #{now})))")
    int withdrawExpired(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET status = 'PUBLISHED', index_status = 'INDEXED', "
            + "indexed_sha256 = #{sha256}, index_error = NULL, update_time = #{now} "
            + "WHERE id = #{sourceId} AND status = 'PUBLISHING' AND sha256 = #{sha256} AND data_state = '0' "
            + "AND (effective_to IS NULL OR effective_to >= DATE(#{now})) "
            + "AND EXISTS (SELECT 1 FROM tab_ai_product_profile_version pv "
            + "WHERE pv.id = tab_insurance_term_source.profile_version_id "
            + "AND pv.product_id = tab_insurance_term_source.insurance_id AND pv.status = 'PUBLISHED' "
            + "AND pv.data_state = '0' AND (pv.effective_to IS NULL OR pv.effective_to >= #{now}))")
    int markIndexedIfValid(@Param("sourceId") Long sourceId, @Param("sha256") String sha256,
                           @Param("now") LocalDateTime now);

    @Update("UPDATE tab_insurance_term_source SET status = 'WITHDRAWN', index_status = 'REMOVAL_PENDING', "
            + "withdrawn_at = #{now}, withdrawal_reason = 'Effective period ended during indexing', "
            + "update_time = #{now} WHERE id = #{sourceId} AND status = 'PUBLISHING' AND sha256 = #{sha256} "
            + "AND data_state = '0' AND (effective_to IS NOT NULL AND effective_to < DATE(#{now}) OR NOT EXISTS "
            + "(SELECT 1 FROM tab_ai_product_profile_version pv WHERE pv.id = tab_insurance_term_source.profile_version_id "
            + "AND pv.product_id = tab_insurance_term_source.insurance_id AND pv.status = 'PUBLISHED' "
            + "AND pv.data_state = '0' AND (pv.effective_to IS NULL OR pv.effective_to >= #{now})))")
    int withdrawInvalidPublishing(@Param("sourceId") Long sourceId, @Param("sha256") String sha256,
                                  @Param("now") LocalDateTime now);
}
