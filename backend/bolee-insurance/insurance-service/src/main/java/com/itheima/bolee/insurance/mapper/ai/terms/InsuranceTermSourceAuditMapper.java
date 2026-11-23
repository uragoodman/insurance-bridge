package com.itheima.bolee.insurance.mapper.ai.terms;

import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermSourceAuditVO;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceAuditDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InsuranceTermSourceAuditMapper {
    @Select("SELECT COUNT(1) FROM tab_insurance_term_source_audit WHERE source_id = #{sourceId}")
    int countBySourceId(@Param("sourceId") Long sourceId);

    @Insert("INSERT INTO tab_insurance_term_source_audit "
            + "(id, source_id, action, from_status, to_status, actor_id, note, create_time) "
            + "VALUES (#{audit.id}, #{audit.sourceId}, #{audit.action}, #{audit.fromStatus}, #{audit.toStatus}, "
            + "#{audit.actorId}, #{audit.note}, #{audit.createTime})")
    int insert(@Param("audit") InsuranceTermSourceAuditDO audit);

    @Select("SELECT id, action, from_status, to_status, actor_id, note, create_time "
            + "FROM tab_insurance_term_source_audit WHERE source_id = #{sourceId} "
            + "ORDER BY create_time ASC, id ASC")
    @Results(id = "insuranceTermSourceAuditResultMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "action", property = "action"),
            @Result(column = "from_status", property = "fromStatus"),
            @Result(column = "to_status", property = "toStatus"),
            @Result(column = "actor_id", property = "actorId"),
            @Result(column = "note", property = "note"),
            @Result(column = "create_time", property = "createTime")
    })
    List<InsuranceTermSourceAuditVO> listBySourceId(@Param("sourceId") Long sourceId);
}
