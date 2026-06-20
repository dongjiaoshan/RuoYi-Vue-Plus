package org.dromara.djs.warehouse.flow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.common.supplier.api.SupplierDealVo;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;

import java.math.BigDecimal;
import java.util.List;

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

    /**
     * 按 {@code inout_type} + 今日 + 物资 {@code belongType}（JOIN product_info）SUM change_quantity。
     *
     * <p>全租户口径（不按操作人）——给 mp 包材库今日总览 KPI 用：今日入库件数 / 今日领用件数。
     * 租户隔离走 MP 拦截器在 final SQL 阶段注入 {@code tenant_id}，应用层不显式 WHERE。</p>
     *
     * @param inoutType IN=入库 / OT=出库
     * @param belongType 字典 {@code djs_belong_type}（包材为 {@code package}）
     * @return 当日该方向该归属 change_quantity 总和（无记录返 0）
     */
    @Select("SELECT COALESCE(SUM(f.change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow f "
        + "  JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0' "
        + " WHERE f.inout_type  = #{inoutType} "
        + "   AND DATE(f.flow_date) = CURDATE() "
        + "   AND p.belong_type = #{belongType} "
        + "   AND f.del_flag    = '0'")
    BigDecimal sumTodayByInoutBelongType(@Param("inoutType") String inoutType,
                                         @Param("belongType") String belongType);

    /**
     * 分割产出总重（按 {@code ear_no} 聚合 {@code cut_out_in} 流水的 change_quantity）。
     *
     * <p>白条分割统计源（doc/14 §1：分割→入冷库后，产出在 location_stock 篮子里会随领用/打包消耗，
     * 故「分割品总重 / 剩余可分割」改读不可变的 {@code cut_out_in} 流水——总产出量恒定，不随下游变动）。
     * ear_no 与白条 1:1（一头猪一白条），按 ear_no 聚合即该白条的分割总产出。</p>
     *
     * @param earNo 猪只耳号（= 白条标签）
     * @return 该耳号的分割产出总重（无记录返 0）
     */
    @Select("SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE flow_type = 'cut_out_in' "
        + "   AND ear_no    = #{earNo} "
        + "   AND del_flag  = '0'")
    BigDecimal sumCutOutByEarNo(@Param("earNo") String earNo);

    /**
     * 批量分割产出总重（按 {@code ear_no} IN 聚合 {@code cut_out_in} 流水），避免 N+1。
     *
     * @param earNos 猪只耳号集合（service 已去重 + 非空过滤）
     * @return 每行 {@code {earNo, totalWeight}}；无产出的耳号不在结果中
     */
    @Select("<script>"
        + "SELECT ear_no AS earNo, COALESCE(SUM(change_quantity), 0) AS totalWeight "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE flow_type = 'cut_out_in' "
        + "   AND del_flag  = '0' "
        + "   AND ear_no IN "
        + "   <foreach collection='earNos' item='e' open='(' separator=',' close=')'>#{e}</foreach> "
        + " GROUP BY ear_no"
        + "</script>")
    java.util.List<java.util.Map<String, Object>> sumCutOutGroupByEarNo(@Param("earNos") java.util.Collection<String> earNos);

    /**
     * 按供应商聚合物资入库流水（DJS-FIX-ADMIN-W22-005 供应商交易明细 facade，read-only）。
     *
     * <p>只取入库方向（{@code inout_type='IN'}）且带供应商的流水（外购入库才写 {@code supplier_id}；
     * 当前 V1 写入路径暂未回填 supplier_id，本查询设计上就绪，待 WMS-PURCHASE 落地后自动出数据）。
     * {@code t_warehouse_stock_flow} 无产品名 / 单位，经 {@code product_id} LEFT JOIN
     * {@code t_warehouse_product_info} 取 {@code product_name / product_unit}。仅本租户 {@code '1001'}
     * 未软删行，按业务日期倒序。</p>
     *
     * @param supplierId 供应商 ID（{@code t_md_supplier.id}）
     * @return 交易明细行（{@code sourceType='material'}）；无数据返空 list
     */
    @Select("SELECT DATE(f.flow_date) AS dealDate, "
        + "       p.product_name AS dealProduct, "
        + "       f.change_quantity AS dealQuantity, "
        + "       p.product_unit AS dealUnit, "
        + "       'material' AS sourceType "
        + "  FROM t_warehouse_stock_flow f "
        + "  LEFT JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0' "
        + " WHERE f.supplier_id = #{supplierId} "
        + "   AND f.inout_type  = 'IN' "
        + "   AND f.del_flag    = '0' "
        + "   AND f.tenant_id   = '1001' "
        + " ORDER BY f.flow_date DESC")
    List<SupplierDealVo> selectSupplierDeals(@Param("supplierId") Long supplierId);

    /**
     * 按入/出库人姓名模糊查 user_id（admin 入库 / 出库记录页「入库人 / 出库人」筛选用，姓名 → ID 反查）。
     *
     * <p>{@code sys_user} 是 ruoyi 全局表，注解 SQL 不走 MP 多租户拦截器；仅按 {@code nick_name}
     * LIKE 返回 user_id，供流水列表按 {@code operator_id} 过滤。</p>
     *
     * @param nickName 入/出库人姓名（模糊）
     * @return 命中的 user_id 列表（可能为空）
     */
    @Select("SELECT user_id FROM sys_user "
        + "WHERE del_flag = '0' AND nick_name LIKE CONCAT('%', #{nickName}, '%')")
    List<Long> selectUserIdsByNickName(@Param("nickName") String nickName);

    /**
     * 果蔬日损耗 compute-on-read 聚合（V4，果疏产品全流程处理.docx）：按自然日（{@code flow_date}）+
     * 指定 {@code flow_type}，对 {@code belong_type='vegetable'} 产品流水 SUM change_quantity。
     *
     * <p>日损耗公式（service 端组装，不建汇总表）：
     * {@code 日损耗 = 领用(pick_out) − 打包(pack_in) − 退回(return_in) − 饲喂}（口径校正见 findings 步14）。
     * 领用取物资领用出库 {@code pick_out}（{@code MatFlowServiceImpl.pick} 写）；
     * 打包取果蔬打包入库 {@code pack_in}（{@code submitVegPack} 写，不写 pack_consume）；
     * 退回取 {@code return_in}。饲喂项物资领用 V1 无果蔬饲喂操作，service 端直接置 0（不经本方法查询）。
     * 各分量经本方法按 {@code flowType} 各查一次（V1 数据量小，单日按 flow_type 分桶聚合开销可忽略）。</p>
     *
     * <p>口径约束：本聚合仅消费 stock_flow 中实际存在的果蔬流水类型，对应 flow_type 当日无流水时该分量为 0
     * （优雅降级，不抛、不阻塞）。{@code pick_out} 经 JOIN {@code product_info WHERE belong_type='vegetable'}
     * 仅统计果蔬产品的领用——外购果蔬 pick 带 veg product_id；自产果蔬 pick 须带可解析的 veg product_id
     * 才被计入（跨 findings 步11 领用地块维度改造，自产果蔬领用须落 resolvable veg product_id 流水）。</p>
     *
     * <p>{@code flowDate} 传 {@code null} 时按当天（{@code CURDATE()}）聚合。租户隔离：
     * 显式 {@code tenant_id='1001'}（V1 单租户，与 LocationStockMapper 同口径）。</p>
     *
     * @param flowType 流水类型（pick_out / pack_in / return_in）
     * @param flowDate 自然日（{@code null}=当天）
     * @return 该自然日该 flow_type 果蔬流水 change_quantity 合计（无记录返 0，永不 null）
     */
    @Select("SELECT COALESCE(SUM(f.change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow f "
        + "  JOIN t_warehouse_product_info p "
        + "    ON p.id = f.product_id AND p.del_flag = '0' AND p.tenant_id = f.tenant_id "
        + " WHERE f.flow_type   = #{flowType} "
        + "   AND p.belong_type = 'vegetable' "
        + "   AND DATE(f.flow_date) = COALESCE(DATE(#{flowDate}), CURDATE()) "
        + "   AND f.del_flag    = '0' "
        + "   AND f.tenant_id   = '1001'")
    BigDecimal sumVegFlowByTypeAndDate(@Param("flowType") String flowType,
                                       @Param("flowDate") java.util.Date flowDate);

}
