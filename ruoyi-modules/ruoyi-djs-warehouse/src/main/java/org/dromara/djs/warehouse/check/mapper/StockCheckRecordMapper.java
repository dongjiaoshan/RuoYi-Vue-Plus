package org.dromara.djs.warehouse.check.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.check.domain.StockCheckRecord;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckHeaderVo;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckRecordVo;

import java.util.List;

/**
 * 盘点记录 Mapper（WMS-STOCK-001）。
 *
 * @author djs
 * @since WMS-STOCK-001
 */
public interface StockCheckRecordMapper extends BaseMapperPlus<StockCheckRecord, StockCheckRecordVo> {

    /**
     * 库位级业务锁核心查询：指定库位下是否存在「进行中」（{@code check_status='in_progress'}）的盘点单头。
     *
     * <p>由 stock_flow 写入入口（{@code MatFlowServiceImpl} / {@code PigBurnRecordServiceImpl}）调用，
     * 命中则抛 ServiceException 拒绝出入库。只看 header 行（{@code is_header=1}）；
     * {@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入，应用层无需显式 WHERE。</p>
     *
     * @param locationId 库位 ID
     * @return &gt;0 表示该库位有进行中盘点单（被锁）
     */
    @Select("SELECT COUNT(1) FROM t_warehouse_check_record "
        + "WHERE location_id = #{locationId} "
        + "  AND is_header = 1 "
        + "  AND check_status = 'in_progress' "
        + "  AND del_flag = '0'")
    long countActiveByLocation(@Param("locationId") Long locationId);

    /**
     * 按盘点人姓名模糊查 user_id（admin 盘点单列表「盘点人」筛选用，姓名 → ID 反查）。
     *
     * <p>{@code sys_user} 是 ruoyi 全局表，注解 SQL 不走 MP 多租户拦截器；仅按 {@code nick_name}
     * LIKE 返回 user_id，供 header 列表按发起人 {@code create_by} 过滤。</p>
     *
     * @param nickName 盘点人姓名（模糊）
     * @return 命中的 user_id 列表（可能为空）
     */
    @Select("SELECT user_id FROM sys_user "
        + "WHERE del_flag = '0' AND nick_name LIKE CONCAT('%', #{nickName}, '%')")
    List<Long> selectUserIdsByNickName(@Param("nickName") String nickName);

    /**
     * 盘点记录列表（admin 只读）：统一读 {@code t_warehouse_stock_flow} 盘点流水
     * （{@code flow_type IN ('check_in','check_out')}），按「日期 + 库位 + 盘点人」聚合成一行盘点单。
     *
     * <p>覆盖两路盘点来源（统一数据源）：mp 工人自助盘点（{@code AppletStockSelfController.checkSubmit}）
     * 与 admin 完成盘点（{@code StockCheckServiceImpl.completeCheck}）都写 check_in/check_out 流水，
     * 故此查询即可显示小程序端提交的盘点（修 admin 盘点记录页只查 check_record 漏掉 mp 提交）。</p>
     *
     * <ul>
     *   <li>{@code checkId}：合成业务码 {@code yyyyMMdd-warehouseId-operatorId}（前端钻取明细用）。</li>
     *   <li>{@code lineCount}：当天盘点的**产品数**（去重 product_id，含正常 / 异常 / 计损；
     *       同一产品多条盘点流水只计 1）。</li>
     *   <li>{@code abnormalCount}：当天盘点结果为异常（{@code check_abnormal_out}）或计损
     *       （{@code check_out}）的**产品数**（同样去重 product_id）。</li>
     *   <li>{@code diffSum}：SUM(change_num)（净盈亏，盘盈正 / 盘亏负）。</li>
     *   <li>{@code checkBy}：operator_id（{@link org.dromara.common.translation.constant.TransConstant} 翻昵称）。</li>
     * </ul>
     */
    @Select("""
        <script>
        SELECT CONCAT(g.day, '-', g.location_id, '-', g.operator_id) AS checkId,
               g.location_id   AS locationId,
               g.check_date    AS checkDate,
               (
                   SELECT COUNT(DISTINCT COALESCE(
                              CAST(ls.product_id AS CHAR),
                              CONCAT('M:', CAST(ls.medicine_id AS CHAR))
                          ))
                     FROM t_warehouse_location_stock ls
                    WHERE ls.location_id = g.location_id
                      AND ls.tenant_id = '1001'
                      AND ls.del_flag = '0'
                      AND ls.product_stock &gt; 0
               ) AS stockProductCount,
               g.line_count    AS lineCount,
               g.abnormal_count AS abnormalCount,
               g.diff_sum      AS diffSum,
               g.operator_id   AS checkBy,
               g.create_time   AS createTime
          FROM (
            SELECT DATE_FORMAT(f.flow_date, '%Y%m%d')                 AS day,
                   f.warehouse_id                                     AS location_id,
                   f.operator_id                                      AS operator_id,
                   MAX(f.flow_date)                                   AS check_date,
                   COUNT(DISTINCT f.product_id)                       AS line_count,
                   COUNT(DISTINCT CASE WHEN f.flow_type IN ('check_out', 'check_abnormal_out')
                                       THEN f.product_id END)         AS abnormal_count,
                   SUM(f.change_num)                                  AS diff_sum,
                   MAX(f.create_time)                                 AS create_time,
                   MAX(f.flow_date)                                   AS sort_date
              FROM t_warehouse_stock_flow f
             WHERE f.flow_type IN ('check_in', 'check_out', 'check_abnormal_out')
               AND f.del_flag  = '0'
               AND f.tenant_id = '1001'
               <if test="locationIds != null and locationIds.size() > 0">
                 AND f.warehouse_id IN
                 <foreach collection="locationIds" item="lid" open="(" separator="," close=")">#{lid}</foreach>
               </if>
               <if test="(locationIds == null or locationIds.size() == 0) and locationId != null">
                 AND f.warehouse_id = #{locationId}
               </if>
               <if test="checkDateFrom != null">
                 AND f.flow_date &gt;= #{checkDateFrom}
               </if>
               <if test="checkDateTo != null">
                 AND f.flow_date &lt;= #{checkDateTo}
               </if>
               <if test="operatorIds != null and operatorIds.size() > 0">
                 AND f.operator_id IN
                 <foreach collection="operatorIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
               </if>
             GROUP BY DATE_FORMAT(f.flow_date, '%Y%m%d'), f.warehouse_id, f.operator_id
          ) g
         ORDER BY g.sort_date DESC, g.location_id DESC
        </script>
        """)
    IPage<StockCheckHeaderVo> selectFlowCheckHeaderPage(IPage<StockCheckHeaderVo> page,
                                                        @Param("locationId") Long locationId,
                                                        @Param("locationIds") List<Long> locationIds,
                                                        @Param("checkDateFrom") String checkDateFrom,
                                                        @Param("checkDateTo") String checkDateTo,
                                                        @Param("operatorIds") List<Long> operatorIds);

    /**
     * 盘点记录明细 line 列表（admin 详情钻取）：读某盘点单组（日期 + 库位 + 盘点人）的逐条盘点流水。
     *
     * <p><b>盘点前库存 / 实盘量按库存台账回溯，不能用 {@code change_quantity} 推。</b>
     * {@code change_quantity} 的写入语义是分支的（{@code StockSelfServiceImpl}）：正常盘点存实盘量、
     * 计损 / 异常存<b>损失量</b>。旧实现统一套 {@code change_quantity - change_num}，对计损行会算出
     * 「盘点前库存 = 损失量 × 2、实盘量 = 损失量」这种编造值（扇子骨真实 12.500 显示成 4.000）。</p>
     *
     * <p>正确口径：{@code 实盘量 = 该库位该产品当前库存合计 − 本行之后所有流水的 change_num 之和}
     * （沿库存台账回溯到盘点那一刻，含篮子行故对 location_stock 求 SUM）；
     * {@code 盘点前库存 = 实盘量 − change_num}。</p>
     *
     * <p>{@code checkResultType}：check_out→3 计损 / check_abnormal_out→2 异常 / change_num=0→1 正常 / 否则→2。
     * 产品代码 / 名称 / 单位 LEFT JOIN product_info。</p>
     */
    @Select("""
        SELECT f.id                                  AS id,
               f.warehouse_id                        AS locationId,
               p.product_id                          AS productCode,
               f.product_id                          AS productId,
               p.product_name                        AS productName,
               p.product_unit                        AS productUnit,
               (
                   SELECT COALESCE(SUM(ls.product_stock), 0)
                     FROM t_warehouse_location_stock ls
                    WHERE ls.location_id = f.warehouse_id
                      AND ls.product_id  = f.product_id
                      AND ls.tenant_id   = f.tenant_id
                      AND ls.del_flag    = '0'
               ) - (
                   SELECT COALESCE(SUM(f2.change_num), 0)
                     FROM t_warehouse_stock_flow f2
                    WHERE f2.tenant_id    = f.tenant_id
                      AND f2.del_flag     = '0'
                      AND f2.warehouse_id = f.warehouse_id
                      AND f2.product_id   = f.product_id
                      AND f2.id           > f.id
               ) - f.change_num                      AS sysStock,
               (
                   SELECT COALESCE(SUM(ls.product_stock), 0)
                     FROM t_warehouse_location_stock ls
                    WHERE ls.location_id = f.warehouse_id
                      AND ls.product_id  = f.product_id
                      AND ls.tenant_id   = f.tenant_id
                      AND ls.del_flag    = '0'
               ) - (
                   SELECT COALESCE(SUM(f2.change_num), 0)
                     FROM t_warehouse_stock_flow f2
                    WHERE f2.tenant_id    = f.tenant_id
                      AND f2.del_flag     = '0'
                      AND f2.warehouse_id = f.warehouse_id
                      AND f2.product_id   = f.product_id
                      AND f2.id           > f.id
               )                                     AS checkStock,
               f.change_num                          AS diffStock,
               CASE
                   WHEN f.flow_type = 'check_out' THEN 3
                   WHEN f.flow_type = 'check_abnormal_out' THEN 2
                   WHEN f.change_num = 0          THEN 1
                   ELSE 2
               END                                   AS checkResultType,
               f.remark                              AS diffReason,
               f.operator_id                         AS checkBy,
               f.flow_date                           AS checkDate,
               f.create_time                         AS createTime
          FROM t_warehouse_stock_flow f
          LEFT JOIN t_warehouse_product_info p
            ON p.id = f.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = f.tenant_id
         WHERE f.flow_type IN ('check_in', 'check_out', 'check_abnormal_out')
           AND f.del_flag  = '0'
           AND f.tenant_id = '1001'
           AND DATE_FORMAT(f.flow_date, '%Y%m%d') = #{day}
           AND f.warehouse_id = #{locationId}
           AND f.operator_id  = #{operatorId}
         ORDER BY f.id DESC
        """)
    List<StockCheckRecordVo> selectFlowCheckLines(@Param("day") String day,
                                                  @Param("locationId") Long locationId,
                                                  @Param("operatorId") Long operatorId);

}
