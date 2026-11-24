package org.dromara.djs.warehouse.cross.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.cross.domain.vo.PigReceptionAgeVo;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/** 猪只入库、白条和追溯共用首次燎毛入库时间；耳号与出栏时间共同限定生命周期。 */
public interface PigReceptionMapper {
    /** 兼容旧流水缺 white_bar_id：用同租户产出行的白条流水号关联，包含已消费产出。 */
    String RECEIVE_TIME_SQL = "(SELECT MIN(f.flow_date) FROM t_warehouse_stock_flow f "
        + "WHERE f.tenant_id=b.tenant_id AND f.del_flag='0' "
        + "AND f.flow_type='slaughter_burn' AND f.inout_type='IN' "
        + "AND (f.white_bar_id=b.id OR (f.white_bar_id IS NULL AND b.id="
        + "(SELECT MAX(si.white_bar_id) FROM t_warehouse_product_inhouse si "
        + "WHERE si.tenant_id=f.tenant_id AND si.white_bar_no=f.white_bar_no))))";

    @Select("SELECT " + RECEIVE_TIME_SQL + " FROM t_warehouse_bar_info b "
        + "WHERE b.ear_no=#{earNo} AND b.marketing_time=#{marketingTime} "
        + "AND b.tenant_id='1001' AND b.del_flag='0' ORDER BY b.id DESC LIMIT 1")
    Date selectReceiveTime(@Param("earNo") String earNo, @Param("marketingTime") Date marketingTime);

    String RECEPTION_AGE_SQL = "SELECT b.id AS bar_info_id, " + RECEIVE_TIME_SQL + " AS receive_time, p.birth_date, "
        + "GREATEST(DATEDIFF(" + RECEIVE_TIME_SQL + ",p.birth_date),0) AS age_days "
        + "FROM t_warehouse_bar_info b LEFT JOIN t_farm_pig_marketing m ON m.id="
        + "(SELECT MAX(pm.id) FROM t_farm_pig_marketing pm WHERE pm.ear_no=b.ear_no "
        + "AND pm.marketing_date=b.marketing_time AND pm.tenant_id=b.tenant_id AND pm.del_flag='0') "
        + "LEFT JOIN t_farm_pig_info p ON p.id=m.pig_id AND p.tenant_id=m.tenant_id AND p.del_flag='0' "
        + "WHERE b.tenant_id='1001' AND b.del_flag='0' ";

    @Select("<script>" + RECEPTION_AGE_SQL + "AND b.id IN "
        + "<foreach collection='barIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<PigReceptionAgeVo> selectReceptionAges(@Param("barIds") Collection<Long> barIds);

    @Select(RECEPTION_AGE_SQL + "AND b.ear_no=#{earNo} AND b.marketing_time=#{marketingTime} ORDER BY b.id DESC LIMIT 1")
    PigReceptionAgeVo selectReceptionAge(@Param("earNo") String earNo, @Param("marketingTime") Date marketingTime);
}
