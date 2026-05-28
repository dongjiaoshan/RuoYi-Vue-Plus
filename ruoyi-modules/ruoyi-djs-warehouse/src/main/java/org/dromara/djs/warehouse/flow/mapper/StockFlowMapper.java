package org.dromara.djs.warehouse.flow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;

import java.math.BigDecimal;

/**
 * 出入库流水 Mapper。
 *
 * <p>BaseMapperPlus 自带 {@code selectVoPage / selectVoList}（StockFlowVo 走 AutoMapper 转换）。
 * 自定义方法集中"今日领用 / 退回 / 损耗 SUM 校验"用于 mp 端 myToday 接口 + 退回 / 损耗的额度校验。</p>
 *
 * @author djs
 * @since WMS-PIG-001（D9 WMS-MAT-001 扩自定义 SQL）
 */
public interface StockFlowMapper extends BaseMapperPlus<StockFlow, StockFlowVo> {

    /**
     * 按当前用户 + 产品 + 流水类型 + 今日（{@code DATE(flow_date)=CURDATE()}）SUM change_quantity。
     *
     * <p>给"今日已领 / 已退 / 已损"做基础聚合：</p>
     * <pre>
     *   SELECT COALESCE(SUM(change_quantity), 0) FROM t_warehouse_stock_flow
     *    WHERE operator_id = #{userId} AND product_id = #{productId}
     *      AND flow_type = #{flowType} AND DATE(flow_date) = CURDATE()
     *      AND del_flag = '0'
     * </pre>
     *
     * <p>tenant_id 由 MP 拦截器在 final SQL 阶段注入，应用层不显式 WHERE。</p>
     *
     * @param userId    当前操作人 user_id
     * @param productId 产品 ID
     * @param flowType  流水类型（pick_out / return_in / loss）
     * @return 当日该类型该产品 change_quantity 总和（无记录返 0）
     */
    @Select("SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE operator_id = #{userId} "
        + "   AND product_id  = #{productId} "
        + "   AND flow_type   = #{flowType} "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag    = '0'")
    BigDecimal sumTodayByUserProductType(@Param("userId") Long userId,
                                         @Param("productId") Long productId,
                                         @Param("flowType") String flowType);

    /**
     * 按当前用户 + 流水类型 + 今日 + 可选物资类型 SUM（前端"今日数据"卡片用，不限定 productId）。
     *
     * <p>matType 可选：若非空则 JOIN product_info 按 belong_type 过滤。MyBatis {@code <if>} 用注解
     * SQL 不便表达，service 层拼接两条独立 SQL，故此处只提供"全部产品"汇总。matType 维度走
     * {@link #sumTodayByUserMatType} 接力。</p>
     */
    @Select("SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE operator_id = #{userId} "
        + "   AND flow_type   = #{flowType} "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag    = '0'")
    BigDecimal sumTodayByUserType(@Param("userId") Long userId,
                                  @Param("flowType") String flowType);

    /**
     * 按当前用户 + 流水类型 + 今日 + 物资 belongType（JOIN product_info）SUM。
     *
     * <p>给"今日数据卡片"按 matType 维度聚合用。</p>
     */
    @Select("SELECT COALESCE(SUM(f.change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow f "
        + "  JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0' "
        + " WHERE f.operator_id = #{userId} "
        + "   AND f.flow_type   = #{flowType} "
        + "   AND DATE(f.flow_date) = CURDATE() "
        + "   AND p.belong_type = #{belongType} "
        + "   AND f.del_flag    = '0'")
    BigDecimal sumTodayByUserMatType(@Param("userId") Long userId,
                                     @Param("flowType") String flowType,
                                     @Param("belongType") String belongType);

}
