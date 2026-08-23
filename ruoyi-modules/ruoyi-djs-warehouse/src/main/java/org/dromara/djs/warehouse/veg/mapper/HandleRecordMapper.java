package org.dromara.djs.warehouse.veg.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.domain.query.PickDetailQuery;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.HandleProductNetRow;
import org.dromara.djs.warehouse.veg.domain.vo.PickDetailVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegHandleRecordVo;

import java.util.Collection;
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
     * 采摘明细 SQL 共用片段（FIX-ADMIN-0721 + row12 并入采摘活动 + row42/44，分页 / 导出同口径）。
     *
     * <p>两支 UNION ALL（各带 statSource：1=毛菜处理间 2=采摘活动，row44）：</p>
     * <ul>
     *   <li>毛菜处理采收过磅（仓库统计权威口径同源）：record_type=1；采摘日期=DATE(handle_time)；
     *       采摘量=record_weight(kg)；地块编号 JOIN t_plant_plot_info；
     *       班组=采收记录实际班组集合（多选中间表 t_warehouse_handle_record_team，多班组顿号拼接展示；
     *       无中间表行时兜底旧单列 r.team_id 对应班组名，row42——不再取计划班组 planting_record）。</li>
     *   <li>采摘活动流水 t_plant_plant_activity：仅 pick_dest 非空行（排除旧农事路径行防与过磅双算）；
     *       班组=地块采收班组集合（t_plant_details_team role='harvest'，多班组顿号拼接展示）；
     *       班组筛选按集合命中（EXISTS）。销售未结算行无地块，地块/班组显空。</li>
     * </ul>
     *
     * <p>外层派生表按 statSource 精确过滤（row44 统计来源下拉）。</p>
     *
     * <p>V6 row106「绩效百分比 / 备注」两列只有过磅支有值（{@code r.perf_percent} / {@code r.remark}
     * 是录入弹窗当场填的）；采摘活动支没有这两个维度，NULL 占位、展示端显 '-'。</p>
     */
    String PICK_DETAIL_SQL = """
        SELECT t.pickDate, t.cropName, t.productName, t.plotCode, t.statSource, t.pickWeight,
               t.perfPercent, t.remark, t.teamId, t.teamName
          FROM (
        SELECT DATE(r.handle_time) AS pickDate,
               pr.crop_name        AS cropName,
               pi.product_name     AS productName,
               p.plot_code         AS plotCode,
               '1'                 AS statSource,
               r.record_weight     AS pickWeight,
               r.perf_percent      AS perfPercent,
               r.remark            AS remark,
               r.team_id           AS teamId,
               COALESCE(
                 (SELECT GROUP_CONCAT(DISTINCT wt.team_name ORDER BY wt.team_name SEPARATOR '、')
                    FROM t_warehouse_handle_record_team rt
                    JOIN t_plant_work_team wt
                           ON wt.id = rt.team_id AND wt.del_flag = '0'
                   WHERE rt.record_id = r.id AND rt.del_flag = '0' AND rt.tenant_id = '1001'),
                 (SELECT wt2.team_name
                    FROM t_plant_work_team wt2
                   WHERE wt2.id = r.team_id AND wt2.del_flag = '0')
               )                   AS teamName,
               r.handle_time       AS sortTime,
               r.id                AS sortId
          FROM t_warehouse_handle_record r
          LEFT JOIN t_warehouse_vegetable_handle h
                 ON h.id = r.handle_id AND h.del_flag = '0' AND h.tenant_id = '1001'
          LEFT JOIN t_warehouse_planting_record pr
                 ON pr.id = h.planting_record_id AND pr.del_flag = '0' AND pr.tenant_id = '1001'
          LEFT JOIN t_plant_plot_info p
                 ON p.id = r.plot_id AND p.del_flag = '0' AND p.tenant_id = '1001'
          LEFT JOIN t_warehouse_product_info pi
                 ON pi.id = r.product_id AND pi.del_flag = '0' AND pi.tenant_id = '1001'
         WHERE r.record_type = 1
           AND r.del_flag = '0'
           AND r.tenant_id = '1001'
           <!-- V6 row28 起可提交 0 kg 采摘记录给地块收口（「已称完但没人点完成」）。
                这类记录不是一次真实采摘，不该出现在采摘明细里，也不该进导出。
                同一个 UNION ALL 的采摘活动分支本来就带 a.daily_weight &gt; 0，这里补齐口径。 -->
           AND r.record_weight &gt; 0
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
             AND (EXISTS (SELECT 1
                            FROM t_warehouse_handle_record_team rt2
                           WHERE rt2.record_id = r.id AND rt2.del_flag = '0' AND rt2.tenant_id = '1001'
                             AND rt2.team_id = #{q.teamId})
               OR r.team_id = #{q.teamId})
           </if>
        UNION ALL
        SELECT a.activity_date    AS pickDate,
               ci.crop_name       AS cropName,
               NULL               AS productName,
               p2.plot_code       AS plotCode,
               '2'                AS statSource,
               a.daily_weight     AS pickWeight,
               NULL               AS perfPercent,
               NULL               AS remark,
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
         <where>
           <if test="q.statSource != null and q.statSource != ''">
             AND t.statSource = #{q.statSource}
           </if>
         </where>
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

    /**
     * 毛菜间处理记录 SQL 共用片段（FIX-ADMIN-R130，分页 / 导出同口径）。
     *
     * <p>三支 UNION ALL，「处理方式」统一到字典 {@code djs_pick_dest}：</p>
     * <ul>
     *   <li>statSource=1 毛菜处理间·处理流水：{@code t_warehouse_handle_record} record_type=2，
     *       handle_target 1/2/3 → veg_fresh / platform / feed。
     *       <b>必须 JOIN {@code t_warehouse_vegetable_handle} 且 planting_record_id IS NOT NULL</b>——
     *       采摘活动「果蔬月台」去向会镜像写一行 handle_record(handle_target=2)（见
     *       {@code VegetableHandleServiceImpl#insertPickPlatform}），那些行的汇总行 planting_record_id 为空，
     *       不排除会与第三支采摘活动行双算。</li>
     *   <li>statSource=1 毛菜处理间·结算损耗：{@code t_warehouse_vegetable_handle.loss_weight}（已摘−处理−饲喂），
     *       无 per-record 流水，按汇总行出一条 loss 记录，日期取结算时间。</li>
     *   <li>statSource=2 采摘活动：{@code t_plant_plant_activity} per-event 行，
     *       pick_dest 即处理方式（NULL=历史销售 → sale）。</li>
     * </ul>
     *
     * <p>自定义 @Select 含 UNION / JOIN，各表 WHERE 显式带 {@code tenant_id}（拦截器对自定义 SQL 不保证注入）。
     * 记录人直接 JOIN {@code sys_user} 取 nick_name 而非注解翻译，使「记录人模糊搜索」能下推到 SQL。</p>
     *
     * <p><b>产品名称（V6 row49）三支取数各不相同</b>：</p>
     * <ul>
     *   <li>处理流水支：{@code r.product_id} JOIN {@code t_warehouse_product_info}，与「采摘明细」页
     *       {@link #PICK_DETAIL_SQL} 同一条取数路径 —— 这是录入人当场选定的处理产品，逐行准确。</li>
     *   <li>结算损耗支：<b>恒 NULL</b>。{@code loss_weight = picked − stockIn − sendPlatform − feed}
     *       是<b>地块级</b>一次性结算，跨该地块所有产品，库里不存在按产品的拆分；汇总行上的
     *       {@code h.product_id} 是建行时按遗留字段 {@code crop.related_product} 解析的单产品，
     *       V6 row16 起产品配置已迁到 {@code t_plant_crop_product}（一作物多产品），拿它填这一列
     *       对多产品作物就是编一个名字出来。宁可空着。</li>
     *   <li>采摘活动支：<b>恒 NULL</b>。{@code t_plant_plant_activity} 没有产品维度，
     *       {@link #PICK_DETAIL_SQL} 的同源分支也是 {@code NULL AS productName}。</li>
     * </ul>
     */
    String VEG_HANDLE_RECORD_SQL = """
        SELECT t.handleDate, t.cropName, t.productName, t.statSource, t.handleMethod,
               t.plotCode, t.handleWeight, t.recorderName
          FROM (
        SELECT r.handle_time AS handleDate,
               c.crop_name   AS cropName,
               pi.product_name AS productName,
               '1'           AS statSource,
               CASE r.handle_target WHEN 1 THEN 'veg_fresh' WHEN 2 THEN 'platform' WHEN 3 THEN 'feed' END AS handleMethod,
               p.plot_code   AS plotCode,
               r.record_weight AS handleWeight,
               COALESCE(u.nick_name, u.user_name) AS recorderName,
               r.handle_time AS sortTime,
               r.id          AS sortId
          FROM t_warehouse_handle_record r
          JOIN t_warehouse_vegetable_handle h
                 ON h.id = r.handle_id AND h.del_flag = '0' AND h.tenant_id = #{tenantId}
                AND h.planting_record_id IS NOT NULL
          LEFT JOIN t_plant_crop_info c
                 ON c.id = r.crop_id AND c.del_flag = '0'
          LEFT JOIN t_plant_plot_info p
                 ON p.id = r.plot_id AND p.del_flag = '0'
          LEFT JOIN t_warehouse_product_info pi
                 ON pi.id = r.product_id AND pi.del_flag = '0' AND pi.tenant_id = #{tenantId}
          LEFT JOIN sys_user u
                 ON u.user_id = COALESCE(r.handle_user, r.create_by) AND u.del_flag = '0'
         WHERE r.del_flag = '0' AND r.tenant_id = #{tenantId}
           AND r.record_type = 2 AND r.handle_target IN (1, 2, 3)
           <!-- V6 row29 起可提交 0 kg 处理记录给地块收口，它不是一次真实处理，不进处理记录列表。
                本 UNION 的另外两支已各自带 h.loss_weight &gt; 0 / COALESCE(a.pick_weight,a.daily_weight) &gt; 0，这里补齐。 -->
           AND r.record_weight &gt; 0
        UNION ALL
        SELECT COALESCE(h.update_time, h.create_time) AS handleDate,
               c.crop_name   AS cropName,
               NULL          AS productName,
               '1'           AS statSource,
               'loss'        AS handleMethod,
               p.plot_code   AS plotCode,
               h.loss_weight AS handleWeight,
               COALESCE(u.nick_name, u.user_name) AS recorderName,
               COALESCE(h.update_time, h.create_time) AS sortTime,
               h.id          AS sortId
          FROM t_warehouse_vegetable_handle h
          LEFT JOIN t_plant_crop_info c
                 ON c.id = h.crop_id AND c.del_flag = '0'
          LEFT JOIN t_plant_plot_info p
                 ON p.id = h.plot_id AND p.del_flag = '0'
          LEFT JOIN sys_user u
                 ON u.user_id = COALESCE(h.update_by, h.create_by) AND u.del_flag = '0'
         WHERE h.del_flag = '0' AND h.tenant_id = #{tenantId}
           AND h.planting_record_id IS NOT NULL
           AND h.loss_weight &gt; 0
        UNION ALL
        SELECT CAST(a.activity_date AS DATETIME) AS handleDate,
               ci.crop_name AS cropName,
               NULL         AS productName,
               '2'          AS statSource,
               COALESCE(NULLIF(a.pick_dest, ''), 'sale') AS handleMethod,
               p2.plot_code AS plotCode,
               COALESCE(a.pick_weight, a.daily_weight) AS handleWeight,
               COALESCE(u2.nick_name, u2.user_name) AS recorderName,
               a.create_time AS sortTime,
               a.id          AS sortId
          FROM t_plant_plant_activity a
          LEFT JOIN t_plant_crop_info ci
                 ON ci.id = a.crop_id AND ci.del_flag = '0'
          LEFT JOIN t_plant_plot_info p2
                 ON p2.id = a.plot_id AND p2.del_flag = '0'
          LEFT JOIN sys_user u2
                 ON u2.user_id = COALESCE(a.recorder_id, a.create_by) AND u2.del_flag = '0'
         WHERE a.del_flag = '0' AND a.tenant_id = #{tenantId}
           AND COALESCE(a.pick_weight, a.daily_weight) &gt; 0
          ) t
         <where>
           <if test="q.dateFrom != null">
             AND t.handleDate &gt;= #{q.dateFrom}
           </if>
           <if test="q.dateTo != null">
             AND t.handleDate &lt; DATE_ADD(#{q.dateTo}, INTERVAL 1 DAY)
           </if>
           <if test="q.cropName != null and q.cropName != ''">
             AND t.cropName LIKE CONCAT('%', #{q.cropName}, '%')
           </if>
           <if test="q.statSource != null and q.statSource != ''">
             AND t.statSource = #{q.statSource}
           </if>
           <if test="q.handleMethod != null and q.handleMethod != ''">
             AND t.handleMethod = #{q.handleMethod}
           </if>
           <if test="q.plotCode != null and q.plotCode != ''">
             AND t.plotCode LIKE CONCAT('%', #{q.plotCode}, '%')
           </if>
           <if test="q.recorderName != null and q.recorderName != ''">
             AND t.recorderName LIKE CONCAT('%', #{q.recorderName}, '%')
           </if>
         </where>
         ORDER BY t.handleDate DESC, t.sortTime DESC, t.sortId DESC
        """;

    /**
     * 毛菜间处理记录分页（FIX-ADMIN-R130，admin 只读列表）。
     *
     * @param page     分页参数（MP 分页拦截器接管）
     * @param tenantId 租户（V1 固定 '1001'）
     * @param q        筛选参数（日期范围 / 作物名 / 统计来源 / 处理方式 / 地块编号 / 记录人）
     * @return 毛菜间处理记录分页行（按处理日期倒序）
     */
    @Select("<script>" + VEG_HANDLE_RECORD_SQL + "</script>")
    Page<VegHandleRecordVo> selectVegHandleRecordPage(@Param("page") Page<?> page,
                                                      @Param("tenantId") String tenantId,
                                                      @Param("q") VegHandleRecordQuery q);

    /**
     * 毛菜间处理记录不分页（导出用，与 {@link #selectVegHandleRecordPage} 同 WHERE）。
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @param q        筛选参数
     * @return 毛菜间处理记录全量行（按处理日期倒序）
     */
    @Select("<script>" + VEG_HANDLE_RECORD_SQL + "</script>")
    List<VegHandleRecordVo> selectVegHandleRecordList(@Param("tenantId") String tenantId,
                                                      @Param("q") VegHandleRecordQuery q);

    /**
     * 按「汇总行 × 产品」聚合采收量与处理量（V6 row18 信息框逐产品剩余重量）。
     *
     * <p>净额 {@code netWeight = Σ采收(record_type=1) − Σ处理(record_type=2)}，处理侧含入库 / 月台 / 饲料
     * 三个去向 —— 与地块级 {@code remainWeight = picked − handled − feed} 同口径，各产品加起来正好是地块总剩余。</p>
     *
     * <p>{@code product_id} 为 NULL 的存量流水按 key {@code NULL} 单独归一行，service 层折进该作物的
     * 首个配置产品，不让改造前录的重量凭空消失。</p>
     *
     * @param handleIds 汇总行 id 集合（已 dedupe，非空）
     * @return 行（handleId / productId / netWeight）；无流水的汇总行不出现
     */
    @Select("""
        <script>
        SELECT r.handle_id AS handleId,
               r.product_id AS productId,
               ROUND(SUM(CASE WHEN r.record_type = 1 THEN r.record_weight ELSE -r.record_weight END), 3) AS netWeight
          FROM t_warehouse_handle_record r
         WHERE r.tenant_id = '1001'
           AND r.del_flag = '0'
           AND r.handle_id IN
           <foreach collection='handleIds' item='hid' open='(' separator=',' close=')'>#{hid}</foreach>
         GROUP BY r.handle_id, r.product_id
        </script>
        """)
    List<HandleProductNetRow> selectProductNetByHandleIds(@Param("handleIds") Collection<Long> handleIds);

}
