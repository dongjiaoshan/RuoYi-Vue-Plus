package org.dromara.djs.warehouse.selfcheck.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.selfcheck.domain.vo.CheckRecordVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.InoutFlowVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.PendingCheckVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.StockStoreEntryVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.StoreProductVo;

import java.util.List;

/**
 * 库存盘点自助子系统 mp 查询 Mapper（SELFCHECK）。
 *
 * <p>读端点聚合查询集中于此，避免在已有 {@code LocationStockMapper / StockFlowMapper} 上堆叠
 * 跨子域语义。租户单租户显式 {@code tenant_id='1001'} + {@code del_flag='0'}（未启全局 MP 拦截器）。</p>
 *
 * <p>{@code check_result} code（{@code djs_check_result}）：1=正常 / 2=异常 / 3=计损；
 * service 转中文文案。{@code lastCheckResult} 用 SQL CASE 直接出中文。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Mapper
public interface StockSelfMapper {

    /**
     * 各库入口聚合（库存盘点首页）：按库位聚合，每库 distinct 产品维度计数
     * + MAX(latest_check_time) + 最近一次盘点结果。
     *
     * <p>LEFT JOIN location_stock，保留无库存行的库位（productKinds=0）。最近盘点结果取该库位
     * {@code latest_check_time} 最大那行的 {@code check_result}（子查询）。仅启用库位
     * {@code location_status=1}。</p>
     */
    @Select("""
        SELECT l.id                                  AS locationId,
               l.location_name                       AS locationName,
               l.location_type                       AS locationType,
               COUNT(DISTINCT s.product_id)          AS productKinds,
               DATE_FORMAT(MAX(s.latest_check_time), '%Y-%m-%d') AS lastCheckDate,
               CASE (SELECT s2.check_result
                       FROM t_warehouse_location_stock s2
                      WHERE s2.location_id = l.id
                        AND s2.del_flag = '0'
                        AND s2.tenant_id = '1001'
                        AND s2.latest_check_time IS NOT NULL
                      ORDER BY s2.latest_check_time DESC, s2.id DESC
                      LIMIT 1)
                   WHEN 1 THEN '正常'
                   WHEN 2 THEN '异常'
                   WHEN 3 THEN '计损'
                   ELSE NULL
               END                                   AS lastCheckResult
          FROM t_warehouse_location_info l
          LEFT JOIN t_warehouse_location_stock s
            ON s.location_id = l.id
           AND s.del_flag   = '0'
           AND s.tenant_id  = l.tenant_id
           AND s.product_stock > 0
         WHERE l.del_flag        = '0'
           AND l.tenant_id       = '1001'
           AND l.location_status = 1
         GROUP BY l.id, l.location_name, l.location_type
         ORDER BY l.location_name ASC
        """)
    List<StockStoreEntryVo> selectStoreEntries();

    /**
     * 库详情标准库产品列表：location_stock JOIN product_info，仅 {@code product_stock > 0}。
     *
     * <p>{@code keyword} LIKE product_name；{@code sort='stock_asc'} 库存升序，否则降序。
     * {@code plotName} 本期留空（不 JOIN 种植域）。{@code thumbUrl} 留 null。</p>
     */
    @Select("""
        <script>
        SELECT s.product_id                          AS productId,
               p.product_id                          AS productCode,
               s.product_name                        AS productName,
               s.product_stock                       AS stock,
               s.product_unit                        AS productUnit,
               p.product_spec                        AS productSpec,
               DATE_FORMAT(s.latest_check_time, '%Y-%m-%d') AS lastCheckDate,
               CASE s.check_result
                   WHEN 1 THEN '正常'
                   WHEN 2 THEN '异常'
                   WHEN 3 THEN '计损'
                   ELSE NULL
               END                                   AS lastCheckResult
          FROM t_warehouse_location_stock s
          LEFT JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
         WHERE s.location_id  = #{locationId}
           AND s.del_flag     = '0'
           AND s.tenant_id    = '1001'
           AND s.product_stock > 0
           <if test="keyword != null and keyword != ''">
             AND s.product_name LIKE CONCAT('%', #{keyword}, '%')
           </if>
        <choose>
          <when test="sort == 'stock_asc'">
            ORDER BY s.product_stock ASC, s.product_name ASC
          </when>
          <otherwise>
            ORDER BY s.product_stock DESC, s.product_name ASC
          </otherwise>
        </choose>
        </script>
        """)
    List<StoreProductVo> selectStoreProducts(@Param("locationId") Long locationId,
                                             @Param("keyword") String keyword,
                                             @Param("sort") String sort);

    /**
     * 待盘点产品列表（库存盘点 tab）：location_stock 该库位 {@code product_stock > 0}，
     * 按 {@code latest_check_time ASC}（久未盘排前，NULL 视作最久）。
     *
     * <p>{@code plotOrEarNo = COALESCE(ear_no, plot_code)}：猪肉走 {@code ear_no} 耳号，
     * 果蔬 LEFT JOIN {@code t_plant_plot_info} 解出 {@code plot_code} 地块编号
     * （少数脏数据 plot_id 在 plot_info 无匹配行 → 仍 NULL，可接受）。
     * {@code lastCheckResult} 由 check_result CASE 转中文；{@code inboundDate} 取库存行建账 create_time。</p>
     */
    @Select("""
        <script>
        SELECT s.product_id                          AS productId,
               s.product_name                        AS productName,
               s.product_stock                       AS stock,
               s.product_unit                        AS productUnit,
               COALESCE(s.ear_no, pl.plot_code)      AS plotOrEarNo,
               CASE s.check_result
                   WHEN 1 THEN '正常'
                   WHEN 2 THEN '异常'
                   WHEN 3 THEN '计损'
                   ELSE NULL
               END                                   AS lastCheckResult,
               DATE_FORMAT(s.latest_check_time, '%Y-%m-%d %H:%i:%s') AS lastCheckTime,
               DATE_FORMAT(s.create_time, '%Y-%m-%d') AS inboundDate
          FROM t_warehouse_location_stock s
          LEFT JOIN t_plant_plot_info pl
            ON pl.id        = s.plot_id
           AND pl.del_flag  = '0'
           AND pl.tenant_id = '1001'
         WHERE s.location_id  = #{locationId}
           AND s.del_flag     = '0'
           AND s.tenant_id    = '1001'
           AND s.product_stock > 0
           <if test="keyword != null and keyword != ''">
             AND s.product_name LIKE CONCAT('%', #{keyword}, '%')
           </if>
         ORDER BY s.latest_check_time IS NULL DESC, s.latest_check_time ASC, s.id ASC
        </script>
        """)
    List<PendingCheckVo> selectPendingChecks(@Param("locationId") Long locationId,
                                             @Param("keyword") String keyword);

    /**
     * 盘点记录分页（盘点记录 tab）：stock_flow 中 {@code flow_type IN ('check_in','check_out','check_abnormal_out')}。
     *
     * <ul>
     *   <li>{@code checkResult} 直接按 flow_type 三分：{@code check_in} → normal（盘盈/无差异）；
     *       {@code check_out} → loss（计损出库）；{@code check_abnormal_out} → abnormal（异常出库）。
     *       异常走独立 flow_type（FIX-WMS-FLOWDICT-001），旧实现漏过滤 + 用 change_num 推断导致计损/异常恒判正常（R82）。</li>
     *   <li>{@code stock} = change_quantity（正常=实盘量；计损/异常=损失量）；{@code diffQuantity} = ABS(change_num)（计损/异常量）。</li>
     *   <li>{@code checkResultLabel} 同步 SQL CASE 出中文（正常/计损/异常）。</li>
     *   <li>产品名 / 单位 LEFT JOIN product_info；{@code plotOrEarNo} 取 ear_no。</li>
     * </ul>
     */
    @Select("""
        <script>
        SELECT f.id                                  AS id,
               DATE_FORMAT(f.flow_date, '%Y-%m-%d %H:%i:%s') AS checkTime,
               p.product_name                        AS productName,
               f.change_quantity                     AS stock,
               p.product_unit                        AS productUnit,
               CASE f.flow_type
                   WHEN 'check_out'          THEN 'loss'
                   WHEN 'check_abnormal_out' THEN 'abnormal'
                   ELSE 'normal'
               END                                   AS checkResult,
               CASE f.flow_type
                   WHEN 'check_out'          THEN '计损'
                   WHEN 'check_abnormal_out' THEN '异常'
                   ELSE '正常'
               END                                   AS checkResultLabel,
               ABS(f.change_num)                     AS diffQuantity,
               f.ear_no                              AS plotOrEarNo,
               f.remark                              AS remark,
               f.operator_id                         AS operatorId
          FROM t_warehouse_stock_flow f
          LEFT JOIN t_warehouse_product_info p
            ON p.id = f.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = f.tenant_id
         WHERE f.warehouse_id = #{locationId}
           AND f.flow_type    IN ('check_in', 'check_out', 'check_abnormal_out')
           AND f.del_flag     = '0'
           AND f.tenant_id    = '1001'
           <if test="checkDate != null and checkDate != ''">
             AND DATE(f.flow_date) = #{checkDate}
           </if>
           <if test="keyword != null and keyword != ''">
             AND p.product_name LIKE CONCAT('%', #{keyword}, '%')
           </if>
           <if test="checkResult == 'loss'">
             AND f.flow_type = 'check_out'
           </if>
           <if test="checkResult == 'normal'">
             AND f.flow_type = 'check_in'
           </if>
           <if test="checkResult == 'abnormal'">
             AND f.flow_type = 'check_abnormal_out'
           </if>
         ORDER BY f.flow_date DESC, f.id DESC
        </script>
        """)
    IPage<CheckRecordVo> selectCheckRecordsPage(IPage<CheckRecordVo> page,
                                                @Param("locationId") Long locationId,
                                                @Param("checkDate") String checkDate,
                                                @Param("keyword") String keyword,
                                                @Param("checkResult") String checkResult);

    /**
     * 进出库流水分页（入库记录 / 出库记录 tab）：stock_flow 中 {@code inout_type=#{inoutType}}（IN/OT）。
     *
     * <p>盘点产生的流水（盘盈入库 check_in / 盘点计损出库 check_out / 盘点异常出库 check_abnormal_out）
     * 本身就是真实的入 / 出库动作，按 {@code inout_type} 天然归入对应 tab：
     * check_in → 入库记录，check_out / check_abnormal_out → 出库记录（r195：盘点计损/盘亏出库
     * 之前被 {@code NOT IN(...)} 过滤丢失，导致出库记录看不到盘点损耗，已放开）。盘点记录 tab 另有
     * {@code selectCheckRecordsPage} 单独聚合这三类，两处口径互不冲突。</p>
     *
     * <p>产品名 / 单位 LEFT JOIN product_info；供应商名 LEFT JOIN {@code t_md_supplier}；
     * {@code flowType} 原值出给 VO，由注解翻译为 {@code inoutTypeLabel}。mp 传的业务子类型 inoutType
     * 筛选本期忽略（无法精确映射 djs_inout_type → flow_type）。</p>
     */
    @Select("""
        <script>
        SELECT f.id                                  AS id,
               f.flow_no                             AS flowNo,
               DATE_FORMAT(f.flow_date, '%Y-%m-%d %H:%i:%s') AS flowTime,
               p.product_name                        AS productName,
               f.change_quantity                     AS quantity,
               p.product_unit                        AS productUnit,
               f.flow_type                           AS flowType,
               f.operator_id                         AS operatorId,
               f.stock_out_dest                      AS stockOutDest,
               sup.supplier_name                     AS supplierName
          FROM t_warehouse_stock_flow f
          LEFT JOIN t_warehouse_product_info p
            ON p.id = f.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = f.tenant_id
          LEFT JOIN t_md_supplier sup
            ON sup.id = f.supplier_id
           AND sup.del_flag = '0'
           AND sup.tenant_id = f.tenant_id
         WHERE f.warehouse_id = #{locationId}
           AND f.inout_type   = #{inoutType}
           AND f.del_flag     = '0'
           AND f.tenant_id    = '1001'
           <if test="startDate != null and startDate != ''">
             AND DATE(f.flow_date) >= #{startDate}
           </if>
           <if test="endDate != null and endDate != ''">
             AND #{endDate} >= DATE(f.flow_date)
           </if>
           <if test="keyword != null and keyword != ''">
             AND p.product_name LIKE CONCAT('%', #{keyword}, '%')
           </if>
         ORDER BY f.flow_date DESC, f.id DESC
        </script>
        """)
    IPage<InoutFlowVo> selectInoutFlowsPage(IPage<InoutFlowVo> page,
                                            @Param("locationId") Long locationId,
                                            @Param("inoutType") String inoutType,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate,
                                            @Param("keyword") String keyword);

}
