package org.dromara.djs.warehouse.veg.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.domain.query.PickDetailQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PickDetailVo;

import java.util.List;

/**
 * 毛菜处理流水 Mapper（WMS-VEG-001）。
 *
 * <p><b>聚合 SQL 租户隔离</b>：V1 未启全局 MP 多租户拦截器，{@code @Select} 原生 SQL 不会被自动注入
 * tenant 过滤 → 下列查询每张表都显式手写 {@code tenant_id='1001' AND del_flag='0'}。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
public interface HandleRecordMapper extends BaseMapperPlus<HandleRecord, HandleRecordVo> {

    /**
     * 采摘明细 WHERE + JOIN 共用片段（FIX-ADMIN-0721，分页 / 导出同口径）。
     *
     * <p>口径 = 仓库统计权威口径同源：record_type=1 采收过磅流水；采摘日期=DATE(handle_time)；
     * 采摘量=record_weight(kg)。关联链 r.handle_id → vegetable_handle → planting_record
     * 取冗余 crop_name / team_id / team_name；地块编号 r.plot_id → t_plant_plot_info.plot_code。
     * 全 LEFT JOIN NULL-safe（planting_record_id 可 NULL）。</p>
     */
    String PICK_DETAIL_SQL = """
        SELECT DATE(r.handle_time) AS pickDate,
               pr.crop_name        AS cropName,
               p.plot_code         AS plotCode,
               r.record_weight     AS pickWeight,
               pr.team_id          AS teamId,
               pr.team_name        AS teamName
          FROM t_warehouse_handle_record r
          LEFT JOIN t_warehouse_vegetable_handle h
                 ON h.id = r.handle_id AND h.del_flag = '0' AND h.tenant_id = '1001'
          LEFT JOIN t_warehouse_planting_record pr
                 ON pr.id = h.planting_record_id AND pr.del_flag = '0' AND pr.tenant_id = '1001'
          LEFT JOIN t_plant_plot_info p
                 ON p.id = r.plot_id AND p.del_flag = '0' AND p.tenant_id = '1001'
         WHERE r.record_type = 1
           AND r.del_flag = '0'
           AND r.tenant_id = '1001'
           <if test="q.pickDateBegin != null">
             AND DATE(r.handle_time) &gt;= #{q.pickDateBegin}
           </if>
           <if test="q.pickDateEnd != null">
             AND DATE(r.handle_time) &lt;= #{q.pickDateEnd}
           </if>
           <if test="q.cropName != null and q.cropName != ''">
             AND pr.crop_name LIKE CONCAT('%', #{q.cropName}, '%')
           </if>
           <if test="q.teamId != null">
             AND pr.team_id = #{q.teamId}
           </if>
         ORDER BY r.handle_time DESC, r.id DESC
        """;

    /**
     * 采摘明细分页（admin 只读列表）。
     *
     * @param page 分页参数（MP 分页拦截器接管）
     * @param q    筛选参数（日期范围 / 作物名模糊 / 班组）
     * @return 采摘明细分页行
     */
    @Select("<script>" + PICK_DETAIL_SQL + "</script>")
    Page<PickDetailVo> selectPickDetailPage(@Param("page") Page<?> page, @Param("q") PickDetailQuery q);

    /**
     * 采摘明细不分页（导出用，与 {@link #selectPickDetailPage} 同 WHERE）。
     *
     * @param q 筛选参数
     * @return 采摘明细全量行
     */
    @Select("<script>" + PICK_DETAIL_SQL + "</script>")
    List<PickDetailVo> selectPickDetailList(@Param("q") PickDetailQuery q);

}
