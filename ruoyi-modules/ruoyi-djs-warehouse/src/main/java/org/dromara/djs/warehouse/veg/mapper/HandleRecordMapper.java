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
     * 采摘明细 SQL 共用片段（FIX-ADMIN-0721 + row12 并入采摘活动，分页 / 导出同口径）。
     *
     * <p>两支 UNION ALL：</p>
     * <ul>
     *   <li>毛菜处理采收过磅（仓库统计权威口径同源）：record_type=1；采摘日期=DATE(handle_time)；
     *       采摘量=record_weight(kg)；班组=planting_record 冗余 team；地块编号 JOIN t_plant_plot_info。</li>
     *   <li>采摘活动流水 t_plant_plant_activity：仅 pick_dest 非空行（排除旧农事路径行防与过磅双算）；
     *       班组=地块采收班组集合（t_plant_details_team role='harvest'，多班组顿号拼接展示）；
     *       班组筛选按集合命中（EXISTS）。销售未结算行无地块，地块/班组显空。</li>
     * </ul>
     */
    String PICK_DETAIL_SQL = """
        SELECT t.pickDate, t.cropName, t.plotCode, t.pickWeight, t.teamId, t.teamName
          FROM (
        SELECT DATE(r.handle_time) AS pickDate,
               pr.crop_name        AS cropName,
               p.plot_code         AS plotCode,
               r.record_weight     AS pickWeight,
               pr.team_id          AS teamId,
               pr.team_name        AS teamName,
               r.handle_time       AS sortTime,
               r.id                AS sortId
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
        UNION ALL
        SELECT a.activity_date    AS pickDate,
               ci.crop_name       AS cropName,
               p2.plot_code       AS plotCode,
               a.daily_weight     AS pickWeight,
               NULL               AS teamId,
               COALESCE(
                 (SELECT GROUP_CONCAT(DISTINCT wt.team_name ORDER BY wt.team_name SEPARATOR '、')
                    FROM t_plant_activity_team at
                    JOIN t_plant_work_team wt
                           ON wt.id = at.team_id AND wt.del_flag = '0'
                   WHERE at.activity_id = a.id AND at.del_flag = '0' AND at.tenant_id = '1001'),
                 (SELECT GROUP_CONCAT(DISTINCT wt.team_name ORDER BY wt.team_name SEPARATOR '、')
                    FROM t_plant_plant_details d
                    JOIN t_plant_details_team dt
                           ON dt.detail_id = d.id AND dt.role = 'harvest' AND dt.del_flag = '0'
                    JOIN t_plant_work_team wt
                           ON wt.id = dt.team_id AND wt.del_flag = '0'
                   WHERE d.plot_id = a.plot_id AND d.crop_id = a.crop_id AND d.del_flag = '0')
               )                  AS teamName,
               a.create_time      AS sortTime,
               a.id               AS sortId
          FROM t_plant_plant_activity a
          LEFT JOIN t_plant_crop_info ci
                 ON ci.id = a.crop_id AND ci.del_flag = '0' AND ci.tenant_id = '1001'
          LEFT JOIN t_plant_plot_info p2
                 ON p2.id = a.plot_id AND p2.del_flag = '0' AND p2.tenant_id = '1001'
         WHERE a.del_flag = '0'
           AND a.tenant_id = '1001'
           AND a.pick_dest IS NOT NULL
           AND a.daily_weight &gt; 0
           <if test="q.pickDateBegin != null">
             AND a.activity_date &gt;= #{q.pickDateBegin}
           </if>
           <if test="q.pickDateEnd != null">
             AND a.activity_date &lt;= #{q.pickDateEnd}
           </if>
           <if test="q.cropName != null and q.cropName != ''">
             AND ci.crop_name LIKE CONCAT('%', #{q.cropName}, '%')
           </if>
           <if test="q.teamId != null">
             AND (EXISTS (SELECT 1
                            FROM t_plant_activity_team at2
                           WHERE at2.activity_id = a.id AND at2.del_flag = '0' AND at2.tenant_id = '1001'
                             AND at2.team_id = #{q.teamId})
               OR EXISTS (SELECT 1
                            FROM t_plant_plant_details d2
                            JOIN t_plant_details_team dt2
                                   ON dt2.detail_id = d2.id AND dt2.role = 'harvest' AND dt2.del_flag = '0'
                           WHERE d2.plot_id = a.plot_id AND d2.crop_id = a.crop_id AND d2.del_flag = '0'
                             AND dt2.team_id = #{q.teamId}))
           </if>
          ) t
         ORDER BY t.sortTime DESC, t.sortId DESC
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
