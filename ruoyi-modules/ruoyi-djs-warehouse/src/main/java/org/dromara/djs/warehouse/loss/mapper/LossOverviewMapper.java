package org.dromara.djs.warehouse.loss.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.loss.domain.vo.LossOverviewDailyVo;
import org.dromara.djs.warehouse.loss.domain.vo.LossOverviewDetailVo;

import java.util.Date;
import java.util.List;

/**
 * 损耗总览聚合查询 Mapper（WMS-LOSS-OVERVIEW-001，仓库-admin 行63）。
 *
 * <p>compute-on-read over {@code t_warehouse_loss_flow}，不建汇总表。两个聚合 @Select：
 * 按日汇总（损耗品种数）+ 当日明细（LEFT JOIN product_info 取图）。
 * 自定义 @Select 含聚合，WHERE 显式带 {@code tenant_id}（多租户拦截器对聚合不保证注入）；
 * 雪花 id CAST AS CHAR 返前端防精度丢失。</p>
 *
 * @author djs
 * @since WMS-LOSS-OVERVIEW-001
 */
@Mapper
public interface LossOverviewMapper {

    /**
     * 按自然日汇总损耗：每日损耗品种数 = 当日明细按产品编码去重后的数量。
     *
     * <p>{@code COUNT(DISTINCT product_code) GROUP BY DATE(loss_date)}，按日期倒序。
     * 与详情弹窗同源同过滤条件，因此汇总值严格等于详情行 {@code productCode} 的 distinct 个数。
     * 燎毛损耗（{@code burn_loss}）按整猪记，流水不带 product_code，SQL 层 {@code COUNT(DISTINCT)}
     * 天然忽略 NULL，不计入品种数。</p>
     *
     * @param tenantId  租户（V1 固定 '1001'）
     * @param dateFrom  起始日期（含，可空）
     * @param dateTo    截止日期（含，可空）
     * @return 每日损耗汇总
     */
    @Select("""
        <script>
        SELECT DATE_FORMAT(loss_date, '%Y-%m-%d') AS lossDate,
               COUNT(DISTINCT product_code) AS productCount
        FROM t_warehouse_loss_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
        <if test="dateFrom != null"> AND loss_date &gt;= #{dateFrom} </if>
        <if test="dateTo != null"> AND loss_date &lt; DATE_ADD(#{dateTo}, INTERVAL 1 DAY) </if>
        GROUP BY DATE_FORMAT(loss_date, '%Y-%m-%d')
        ORDER BY DATE_FORMAT(loss_date, '%Y-%m-%d') DESC
        </script>
        """)
    List<LossOverviewDailyVo> selectDailyOverview(@Param("tenantId") String tenantId,
                                                  @Param("dateFrom") Date dateFrom,
                                                  @Param("dateTo") Date dateTo);

    /**
     * 当日损耗明细（行63 详情弹窗）：某自然日逐条损耗 + 产品图。
     *
     * <p>LEFT JOIN {@code t_warehouse_product_info}（loss_flow.product_id = product_info.id）取
     * {@code COALESCE(product_thumb, image_oss_id)} 作图片。搜索：产品名称模糊（loss_flow 冗余快照
     * product_name）/ 损耗类型精确。id CAST AS CHAR 返前端。</p>
     *
     * <p>燎毛损耗（{@code burn_loss}）按整猪记、流水不挂 product，product_unit 为 NULL，
     * 单位列在后端固定成 {@code kg}（燎毛损耗恒为重量口径），保证导出等其他消费方一致。</p>
     *
     * @param tenantId    租户（V1 固定 '1001'）
     * @param date        统计自然日（必传，yyyy-MM-dd 边界由 service 转成当日 00:00:00/23:59:59 或这里 DATE() 比较）
     * @param productName 产品名称模糊（可空）
     * @param lossType    损耗类型 djs_loss_type（可空）
     * @return 当日损耗明细
     */
    @Select("""
        <script>
        SELECT CAST(lf.id AS CHAR) AS id,
               lf.loss_date AS lossDate,
               COALESCE(pi.product_thumb, pi.image_oss_id) AS imageOssId,
               lf.product_code AS productCode,
               lf.product_name AS productName,
               CASE WHEN lf.loss_type = 'burn_loss' THEN 'kg' ELSE lf.product_unit END AS productUnit,
               lf.loss_type AS lossType,
               lf.loss_weight AS lossWeight
        FROM t_warehouse_loss_flow lf
        LEFT JOIN t_warehouse_product_info pi ON pi.id = lf.product_id AND pi.del_flag = '0'
        WHERE lf.del_flag = '0' AND lf.tenant_id = #{tenantId}
          AND DATE(lf.loss_date) = #{date}
        <if test="productName != null and productName != ''">
            AND lf.product_name LIKE CONCAT('%', #{productName}, '%')
        </if>
        <if test="lossType != null and lossType != ''">
            AND lf.loss_type = #{lossType}
        </if>
        ORDER BY lf.loss_date DESC, lf.id DESC
        </script>
        """)
    List<LossOverviewDetailVo> selectDailyDetail(@Param("tenantId") String tenantId,
                                                 @Param("date") String date,
                                                 @Param("productName") String productName,
                                                 @Param("lossType") String lossType);
}
