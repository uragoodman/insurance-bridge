package com.itheima.bolee.insurance.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;

import java.util.List;

/** Mapper for the derived, exact-byte product-name n-gram index. */
@Mapper
public interface InsuranceNameNgramMapper {

    @Select({
        "<script>",
        "SELECT gram_stat.ngram FROM tab_insurance_name_ngram_stat gram_stat ",
        "WHERE gram_stat.ngram IN (",
        "<foreach collection='ngrams' item='ngram' separator=','>",
        "#{ngram, jdbcType=VARBINARY}",
        "</foreach>",
        ") AND EXISTS (SELECT 1 FROM tab_insurance_name_ngram posting ",
        "FORCE INDEX (idx_ins_name_ngram_lookup) WHERE posting.ngram = gram_stat.ngram) ",
        "ORDER BY gram_stat.product_count, gram_stat.ngram LIMIT 1",
        "</script>"
    })
    @Results({
        @Result(column = "ngram", property = "ngram", javaType = byte[].class, jdbcType = JdbcType.VARBINARY)
    })
    NgramValue selectRarestNgram(@Param("ngrams") List<byte[]> ngrams);

    final class NgramValue {
        private byte[] ngram;

        public byte[] getNgram() {
            return ngram;
        }

        public void setNgram(byte[] ngram) {
            this.ngram = ngram;
        }
    }

    @Delete("DELETE FROM tab_insurance_name_ngram WHERE insurance_id = #{insuranceId}")
    int deleteByInsuranceId(@Param("insuranceId") Long insuranceId);

    @Delete("DELETE FROM tab_insurance_name_ngram_stat WHERE product_count = 0")
    int deleteEmptyStats();

    @org.apache.ibatis.annotations.Update({
        "UPDATE tab_insurance_name_ngram_stat stat",
        "JOIN (SELECT ngram, COUNT(*) AS removed_count FROM tab_insurance_name_ngram",
        "WHERE insurance_id = #{insuranceId} GROUP BY ngram) removed ON removed.ngram = stat.ngram",
        "SET stat.product_count = IF(stat.product_count > removed.removed_count,",
        "stat.product_count - removed.removed_count, 0)"
    })
    int decrementStatsByInsuranceId(@Param("insuranceId") Long insuranceId);

    @Delete({
        "<script>",
        "DELETE FROM tab_insurance_name_ngram WHERE insurance_id IN",
        "<foreach collection='insuranceIds' item='insuranceId' open='(' separator=',' close=')'>",
        "#{insuranceId}",
        "</foreach>",
        "</script>"
    })
    int deleteByInsuranceIds(@Param("insuranceIds") List<Long> insuranceIds);

    @org.apache.ibatis.annotations.Update({
        "<script>",
        "UPDATE tab_insurance_name_ngram_stat stat",
        "JOIN (SELECT ngram, COUNT(*) AS removed_count FROM tab_insurance_name_ngram WHERE insurance_id IN",
        "<foreach collection='insuranceIds' item='insuranceId' open='(' separator=',' close=')'>#{insuranceId}</foreach>",
        "GROUP BY ngram) removed ON removed.ngram = stat.ngram",
        "SET stat.product_count = IF(stat.product_count > removed.removed_count,",
        "stat.product_count - removed.removed_count, 0)",
        "</script>"
    })
    int decrementStatsByInsuranceIds(@Param("insuranceIds") List<Long> insuranceIds);

    @Insert({
        "<script>",
        "INSERT INTO tab_insurance_name_ngram (insurance_id, ngram) VALUES",
        "<foreach collection='ngrams' item='ngram' separator=','>",
        "(#{insuranceId}, #{ngram, jdbcType=VARBINARY})",
        "</foreach>",
        "</script>"
    })
    int insertNgrams(@Param("insuranceId") Long insuranceId, @Param("ngrams") List<byte[]> ngrams);

    @Insert({
        "<script>",
        "INSERT INTO tab_insurance_name_ngram_stat (ngram, product_count) VALUES",
        "<foreach collection='ngrams' item='ngram' separator=','>",
        "(#{ngram, jdbcType=VARBINARY}, 1)",
        "</foreach>",
        "ON DUPLICATE KEY UPDATE product_count = product_count + 1",
        "</script>"
    })
    int incrementStats(@Param("ngrams") List<byte[]> ngrams);
}
