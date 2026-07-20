package org.dromara.djs.warehouse.loss.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.domain.vo.LossFlowVo;

import java.util.Date;
import java.util.List;

/**
 * 统一损耗流水台账 Mapper（WMS-LOSS-001）。
 *
 * <p>聚合查询（按日汇总 / 当日明细 / 按产品按日求和）由 {@code LossOverviewMapper} 承载，
 * 本 Mapper 仅提供 {@link BaseMapperPlus} 标准写入（损耗 hook 双写入口）+ 库存查询详情「损耗记录」tab
 * 按产品取明细（行57）。</p>
 *
 * @author djs
 * @since WMS-LOSS-001
 */
public interface LossFlowMapper extends BaseMapperPlus<LossFlow, LossFlow> {

    /**
     * 按产品取损耗明细（库存查询详情「损耗记录」tab，行57）。
     *
     * <p>自定义 @Select，WHERE 显式带 {@code tenant_id}（多租户拦截器对自定义 SQL 不保证注入）。
     * 雪花 id / product_id / location_id / operator_id 物理列为 BIGINT，VO 字段 Long 由 MyBatis
     * 自动映射；不直出给前端做精度敏感运算，按日期倒序返明细。</p>
     *
     * @param tenantId  租户（V1 固定 '1001'）
     * @param productId 产品 ID（FK → t_warehouse_product_info.id）
     * @param dateFrom  起始时间（含，可空）
     * @param dateTo    截止时间（含，可空）
     * @return 该产品损耗明细（按损耗时间倒序）
     */
    @Select("""
        <script>
        SELECT id, loss_date AS lossDate, product_id AS productId, product_code AS productCode,
               product_name AS productName, product_unit AS productUnit, loss_type AS lossType,
               loss_weight AS lossWeight, location_id AS locationId, location_name AS locationName,
               operator_id AS operatorId, remark
        FROM t_warehouse_loss_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId} AND product_id = #{productId}
        <if test="dateFrom != null"> AND loss_date &gt;= #{dateFrom} </if>
        <if test="dateTo != null"> AND loss_date &lt;= #{dateTo} </if>
        ORDER BY loss_date DESC, id DESC
        </script>
        """)
    List<LossFlowVo> selectByProduct(@Param("tenantId") String tenantId,
                                     @Param("productId") Long productId,
                                     @Param("dateFrom") Date dateFrom,
                                     @Param("dateTo") Date dateTo);

}
