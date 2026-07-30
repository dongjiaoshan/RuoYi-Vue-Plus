package org.dromara.djs.plant.farm.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.farm.domain.FarmRecordsTeam;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 农事记录·班组多选中间表 Mapper（G1-TEAMS-MULTISELECT）。
 *
 * @author djs
 * @since G1-TEAMS-MULTISELECT
 */
public interface FarmRecordsTeamMapper extends BaseMapperPlus<FarmRecordsTeam, FarmRecordsTeam> {

    /**
     * 批量查询若干农事记录的班组名（一次 SQL，enrich 用，禁 N+1）。
     *
     * @param recordIds {@code t_plant_farm_records.id} 集合（非空）
     * @return 每行 recordId(Long) / teamId(Long) / teamName(String)，按 team_id 升序
     */
    @Select("<script>" +
        "SELECT rt.record_id AS recordId, rt.team_id AS teamId, t.team_name AS teamName " +
        "FROM t_farm_records_team rt " +
        "LEFT JOIN t_plant_work_team t ON t.id = rt.team_id AND t.del_flag = '0' " +
        "WHERE rt.del_flag = '0' AND rt.record_id IN " +
        "<foreach collection='recordIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
        "ORDER BY rt.team_id ASC" +
        "</script>")
    List<Map<String, Object>> selectTeamNamesByRecordIds(@Param("recordIds") Collection<Long> recordIds);

    /**
     * 物理删除某农事记录的全部旧班组关联（sync 先删后插）。
     *
     * @param recordId {@code t_plant_farm_records.id}
     */
    @Delete("DELETE FROM t_farm_records_team WHERE record_id = #{recordId}")
    int physicalDeleteByRecordId(@Param("recordId") Long recordId);
}
