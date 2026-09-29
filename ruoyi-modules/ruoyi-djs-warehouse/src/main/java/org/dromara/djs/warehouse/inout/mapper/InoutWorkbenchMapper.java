package org.dromara.djs.warehouse.inout.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import org.dromara.djs.warehouse.inout.domain.vo.CutWorkbenchBarVo;
import org.dromara.djs.warehouse.inout.domain.vo.RecentOutDestVo;
import org.dromara.djs.warehouse.inout.domain.vo.CutStoreDemandVo;
import java.time.LocalDate;
import java.util.List;

public interface InoutWorkbenchMapper {
    @Select("""
        WITH eligible AS (
          SELECT p.tenant_id, dm.store_id, s.store_name, p.id AS product_id, p.product_name, p.product_unit,
                 p.material_num AS configured_measure_weight,
                 dm.demand_quantity-COALESCE(dm.shipped_count,0) AS remaining
          FROM t_warehouse_product_info p
          JOIN t_warehouse_demand_manage dm ON dm.product_id=p.id AND dm.tenant_id=p.tenant_id
          JOIN t_md_store s ON s.id=dm.store_id AND s.tenant_id=dm.tenant_id AND s.del_flag='0'
          WHERE p.product_material=#{materialProductId} AND p.product_attr=1
            AND p.is_material_sold=1 AND p.is_delivery=1 AND p.product_status=0
            AND p.belong_type='pork' AND p.del_flag='0' AND p.tenant_id='1001'
            AND dm.demand_date=#{today} AND dm.del_flag='0'
            AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED')
            AND dm.demand_quantity>COALESCE(dm.shipped_count,0)
        )
        SELECT store_id,store_name,product_id,product_name,product_unit,
               SUM(remaining) AS demand_quantity,
               CASE WHEN LOWER(TRIM(product_unit)) IN ('kg','公斤') THEN SUM(remaining)
                 ELSE COALESCE(configured_measure_weight,0) END AS measure_weight,
               CASE WHEN LOWER(TRIM(product_unit)) IN ('kg','公斤') THEN SUM(remaining)
                 ELSE COALESCE(configured_measure_weight,0) END AS minimum_weight
        FROM eligible
        GROUP BY store_id,store_name,product_id,product_name,product_unit,configured_measure_weight
        ORDER BY store_name,product_name,product_id,store_id
        """)
    List<CutStoreDemandVo> selectCutStoreDemands(@Param("materialProductId") Long materialProductId,
                                               @Param("today") LocalDate today);

    /** 列表与完成判定共用原始入库重：原入库事实→同半扇源行→领用时冻结重量+预冷损耗。 */
    String CUT_ORIGINAL_IN_WEIGHT_SQL = "COALESCE("
        + "(SELECT SUM(bf.change_quantity) FROM t_warehouse_stock_flow bf "
        + "WHERE bf.white_bar_no=c.white_bar_no AND (bf.white_bar_id=c.white_bar_id OR bf.white_bar_id IS NULL) "
        + "AND bf.tenant_id=c.tenant_id AND bf.flow_type='slaughter_burn' AND bf.inout_type='IN' AND bf.del_flag='0'), "
        + "(SELECT MAX(si.product_weight) FROM t_warehouse_product_inhouse si "
        + "WHERE si.white_bar_no=c.white_bar_no AND si.white_bar_id=c.white_bar_id "
        + "AND si.tenant_id=c.tenant_id AND si.product_weight>0), "
        + "CASE WHEN c.drip_loss IS NOT NULL AND c.drip_loss>=0 THEN c.pickup_weight+c.drip_loss END, "
        + "CASE WHEN c.white_bar_no IS NULL THEN b.in_weight END)";

    @Select("SELECT " + CUT_ORIGINAL_IN_WEIGHT_SQL + " FROM t_warehouse_pig_cut_record c "
        + "JOIN t_warehouse_bar_info b ON b.id=c.white_bar_id AND b.tenant_id=c.tenant_id AND b.del_flag='0' "
        + "WHERE c.id=#{cutRecordId} AND c.del_flag='0'")
    BigDecimal selectCutOriginalInWeight(@Param("cutRecordId") Long cutRecordId);

    @Select("SELECT i.white_bar_id AS bar_info_id, i.id AS inhouse_id, i.white_bar_no, i.ear_no, "
        + "p.product_name, COALESCE(i.produce_time,b.in_time) AS in_time, i.product_weight AS in_weight, "
        + "i.product_weight AS remaining_weight, 'in_stock' AS cut_status "
        + "FROM t_warehouse_product_inhouse i JOIN t_warehouse_product_info p "
        + "ON p.id=i.product_id AND p.tenant_id=i.tenant_id AND p.del_flag='0' AND p.belong_type='white_bar' "
        + "JOIN t_warehouse_bar_info b ON b.id=i.white_bar_id AND b.tenant_id=i.tenant_id AND b.del_flag='0' "
        + "WHERE i.del_flag='0' AND i.product_weight>0 AND COALESCE(i.pickup_status,0)=0 "
        + "AND b.status IN ('in_stock','pending_cut','cutting') AND EXISTS "
        + "(SELECT 1 FROM t_warehouse_location_stock s WHERE s.white_bar_no=i.white_bar_no "
        + "AND s.product_id=i.product_id AND s.location_id=i.location_id AND s.tenant_id=i.tenant_id "
        + "AND s.del_flag='0' AND s.product_stock>0) ORDER BY in_time DESC, i.id DESC")
    List<CutWorkbenchBarVo> selectUnpickedBars();

    @Select("SELECT c.white_bar_id AS bar_info_id, c.id AS cut_record_id, c.white_bar_no, c.ear_no, "
        + "COALESCE(i.produce_time,b.in_time) AS in_time, " + CUT_ORIGINAL_IN_WEIGHT_SQL + " AS in_weight, "
        + "c.pickup_time AS operate_time, c.cut_status, i.product_name, c.pickup_weight - COALESCE("
        + "(SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f "
        + "WHERE f.flow_type='cut_out_in' AND f.inout_type='IN' AND f.del_flag='0' AND f.tenant_id=c.tenant_id "
        + "AND ((c.white_bar_no IS NOT NULL AND f.white_bar_no=c.white_bar_no) "
        + "OR (c.white_bar_no IS NULL AND f.white_bar_id=c.white_bar_id))),0) AS remaining_weight "
        + "FROM t_warehouse_pig_cut_record c JOIN t_warehouse_bar_info b "
        + "ON b.id=c.white_bar_id AND b.tenant_id=c.tenant_id AND b.del_flag='0' "
        + "LEFT JOIN t_warehouse_product_inhouse i ON i.white_bar_no=c.white_bar_no AND i.tenant_id=c.tenant_id "
        + "AND i.white_bar_id=c.white_bar_id AND i.material_id IS NULL "
        + "WHERE c.del_flag='0' AND c.out_type='cut' AND c.cut_status IN ('picked','cutting') "
        + "ORDER BY in_time DESC, c.id DESC")
    List<CutWorkbenchBarVo> selectPickedBars();

    @Select("SELECT stock_out_dest AS value, COUNT(*) AS count FROM t_warehouse_stock_flow "
        + "WHERE del_flag='0' AND flow_type='cut_room_out' AND inout_type='OT' "
        + "AND flow_date >= DATE_SUB(NOW(), INTERVAL 30 DAY) AND flow_date <= NOW() "
        + "AND stock_out_dest IS NOT NULL GROUP BY stock_out_dest "
        + "ORDER BY COUNT(*) DESC, MAX(flow_date) DESC, stock_out_dest ASC LIMIT 10")
    List<RecentOutDestVo> selectRecentOutDests();
}
