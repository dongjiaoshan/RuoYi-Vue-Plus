package org.dromara.djs.warehouse.flow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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
     * 【三期】总入库 / 总出库合计（V6 row92；按 {@code third_phase=1} + 出入方向聚合 {@code change_num}）。
     *
     * <p>甲方口径：「通过三期标识可以统计三期的总入库和总出库」——判据就是流水行上的
     * {@code third_phase}，不依赖地块（三期没有真实地块）。</p>
     *
     * <p><b>取 {@code ABS(change_num)}</b>：{@code change_num} 是带符号的（出库流水由
     * {@code LocationStockServiceImpl#productOut} 写成负值），直接 SUM 会让「总出库」显示成负数。
     * 合计要的是量而不是方向，方向已经由 {@code inout_type} 参数限定死了。</p>
     *
     * <p><b>租户隔离靠 MP 拦截器</b>在 final SQL 阶段注入 {@code tenant_id}，应用层不显式 WHERE ——
     * 与同族的「按方向 / 按人聚合」兄弟（{@link #sumTodayByUserProductType} /
     * {@link #sumTodayByInoutBelongType}）同一口径，不是这条漏写。</p>
     *
     * <p>本文件确实还有另一半查询显式写死 {@code tenant_id = '1001'}（如
     * {@link #sumTodayNetPickedByBasket}）—— 两种写法在 V1 单租户下等价，历史上按写的人分成了两派。
     * <b>不要为了"统一"去改任何一条</b>：显式 {@code '1001'} 改成靠拦截器，会在拦截器未生效的调用路径上
     * 直接放开跨租户；靠拦截器的改成显式 {@code '1001'}，等于把 V2 多租户的开关焊死在 SQL 里。
     * 要收敛也是 V2 启用拦截器时整批收敛，不在单条查询上零敲碎打。</p>
     *
     * @param inoutType 出入方向（IN 入库 / OT 出库）
     * @return 该方向的三期合计量（无记录返 0）
     */
    @Select("SELECT COALESCE(SUM(ABS(change_num)), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE third_phase = 1 "
        + "   AND inout_type  = #{inoutType} "
        + "   AND del_flag    = '0'")
    BigDecimal sumThirdPhaseByInout(@Param("inoutType") String inoutType);

    /**
     * V6-R43 燎毛间产品入库重量调整：改写这条燎毛入库流水的重量。
     *
     * <p><b>覆盖而不是按差额加减</b>：这条流水就是「本次燎毛入库」这一个事件本身，
     * {@code change_num / change_quantity} 只由这一次入库产生，没有别人往上叠加。
     * 甲方口径是「相当于产品是按修改完成后的重量入的库」= 改写这笔历史，不补冲正流水。
     * 库存日 / 月汇总是回放 {@code stock_flow} 的 compute-on-read，改了这里汇总自动跟着对。</p>
     *
     * <p>{@code flow_type='slaughter_burn' AND inout_type='IN'} 限定只命中燎毛入库那一条 ——
     * 同一个 {@code white_bar_no} 后续还会有领用出库 / 分割等其它流水，绝不能被一起改。</p>
     *
     * @return affectedRows（1=成功；0=流水不存在 / 已软删）
     */
    @Update("UPDATE t_warehouse_stock_flow "
        + "   SET change_num = #{weight},"
        + "       change_quantity = #{weight},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE white_bar_no = #{whiteBarNo} "
        + "   AND product_id = #{productId} "
        + "   AND flow_type = 'slaughter_burn' "
        + "   AND inout_type = 'IN' "
        + "   AND del_flag = '0'")
    int updateBurnInFlowWeight(@Param("whiteBarNo") String whiteBarNo,
                               @Param("productId") Long productId,
                               @Param("weight") BigDecimal weight,
                               @Param("userId") Long userId);

    /**
     * 按<b>篮子维度</b>（product + location + ear_no + white_bar_no + plot_id，NULL-safe 匹配）算今日「领用剩余」净额：
     * {@code 领用(pick_out) − 退回(return_in) − 损耗(loss) − 饲喂(feed_out)}。
     *
     * <p>row38：退回入库 / 当日损耗按篮子校验用——只能退/损该篮子今日实际领用剩余的量，防止退回到「没领用过的
     * 篮子/库位」（如领用耳号 A 却退到库位 B）。返 ≤ 0 = 该篮今日无可退/可损量，调用方拦截。</p>
     *
     * @return 该篮子今日领用净剩余（无流水返 0）
     */
    @Select("SELECT COALESCE(SUM(CASE "
        + "   WHEN flow_type IN ('prod_pick_out','dept_pick_out','pick_out') THEN change_quantity "
        + "   WHEN flow_type IN ('prod_return_in','pick_return_in','store_return_in','loss','feed_out') THEN -change_quantity "
        + "   ELSE 0 END), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE product_id   = #{productId} "
        + "   AND warehouse_id = #{locationId} "
        + "   AND (ear_no = #{earNo} OR (ear_no IS NULL AND #{earNo} IS NULL)) "
        + "   AND (white_bar_no = #{whiteBarNo} OR (white_bar_no IS NULL AND #{whiteBarNo} IS NULL)) "
        + "   AND (plot_id = #{plotId} OR (plot_id IS NULL AND #{plotId} IS NULL)) "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag = '0' AND tenant_id = '1001'")
    BigDecimal sumTodayNetPickedByBasket(@Param("productId") Long productId,
                                         @Param("locationId") Long locationId,
                                         @Param("earNo") String earNo,
                                         @Param("whiteBarNo") String whiteBarNo,
                                         @Param("plotId") Long plotId);

    /**
     * 按「产品 null-ear 组」维度（product + {@code ear_no IS NULL}，跨库位 / 跨白条号）算今日「领用剩余」净额：
     * {@code 领用(pick_out 三键) − 退回(return_in 三键) − 损耗(loss) − 饲喂(feed_out)}。
     *
     * <p>admin 猪肉聚合行（{@code selectAdminMatPorkProducts} GROUP BY ear_no，NULL 归一组跨库位 SUM）与 mp
     * 猪肉「退货卡」的 {@code batchId} 语义 = 「该产品 null-ear 组」而非单篮：组级 FIFO 领用写的流水按首篮标签
     * 落库（white_bar / warehouse 取首篮），该组的退回 / 损耗校验须按同组维度聚合，按篮 keyed
     * （{@link #sumTodayNetPickedByBasket}）会因「领用记在首篮标签、退损选中篮对不上」粒度错位误拦。
     * flow_type 键集与 {@link #sumTodayNetPickedByBasket} 完全一致；过滤维度与聚合行「今日四量」子查询一致
     * （仅 product + ear_no IS NULL，不限库位 / 白条号 / 地块）。</p>
     *
     * @return 该产品 null-ear 组今日领用净剩余（无流水返 0）
     */
    @Select("SELECT COALESCE(SUM(CASE "
        + "   WHEN flow_type IN ('prod_pick_out','dept_pick_out','pick_out') THEN change_quantity "
        + "   WHEN flow_type IN ('prod_return_in','pick_return_in','store_return_in','loss','feed_out') THEN -change_quantity "
        + "   ELSE 0 END), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE product_id = #{productId} "
        + "   AND ear_no IS NULL "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag = '0' AND tenant_id = '1001'")
    BigDecimal sumTodayNetPickedByNullEarGroup(@Param("productId") Long productId);

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
     * 分割产出总重（按 {@code white_bar_id} 聚合 {@code cut_out_in} 流水的 change_quantity）。
     *
     * <p>白条分割「剩余可分割重量 / 超量校验」统一口径：自养（有耳号）/ 外购（{@code ear_no=NULL}）都按
     * {@code white_bar_id}（= bar_info.id = cut_record.white_bar_id）聚合，不依赖 ear_no，
     * 修复外购白条剩余重量恒 null 的问题。white_bar_id 与白条 1:1，按 white_bar_id 聚合即该白条的分割总产出。</p>
     *
     * @param whiteBarId 白条 ID（= {@code t_warehouse_bar_info.id}）
     * @return 该白条的分割产出总重（无记录返 0）
     */
    @Select("SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE flow_type    = 'cut_out_in' "
        + "   AND white_bar_id = #{whiteBarId} "
        + "   AND del_flag     = '0'")
    BigDecimal sumCutOutByWhiteBarId(@Param("whiteBarId") Long whiteBarId);

    /**
     * 分割产出总重（按半只 {@code white_bar_no} 聚合 {@code cut_out_in} 流水的 change_quantity）。
     *
     * <p>邓博 row13/row14 按半只 surface 后，剩余可分割 / 超量校验按半只 white_bar_no 聚合
     * （一头猪多半只各自独立分割，避免按 white_bar_id 整猪聚合互相串扣）。white_bar_no 空的旧记录
     * 仍走 {@link #sumCutOutByWhiteBarId}（整猪，向后兼容）。</p>
     *
     * @param whiteBarNo 半只白条流水号（cut_record.white_bar_no）
     * @return 该半只的分割产出总重（无记录返 0）
     */
    @Select("SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE flow_type     = 'cut_out_in' "
        + "   AND white_bar_no  = #{whiteBarNo} "
        + "   AND del_flag      = '0'")
    BigDecimal sumCutOutByWhiteBarNo(@Param("whiteBarNo") String whiteBarNo);

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
     * 批量按供应商聚合物资入库流水统计（次数 + 入库量），供供应商列表页一次性回填，避免 N+1。
     *
     * <p>严格镜像 {@link #selectSupplierDeals} 的 FROM / LEFT JOIN / WHERE（含 {@code p.del_flag='0'} JOIN 条件、
     * {@code f.inout_type='IN'}、{@code f.del_flag='0'}、{@code f.tenant_id='1001'}），仅把投影换成 {@code COUNT(*)} /
     * {@code COALESCE(SUM(f.change_quantity),0)}、按 {@code f.supplier_id} GROUP BY、去掉 ORDER BY，并把单值
     * {@code = #{supplierId}} 改成 {@code IN (...)}。product_info 走 LEFT JOIN 且 id 为 PK（1:1），不影响 COUNT/SUM
     * 行数，口径与逐个查询完全一致。</p>
     *
     * @param ids 供应商 ID 集合（service 端保证非空非 null）
     * @return 每行 {@code {supplierId, dealCount, purchaseQty}}；无入库的供应商不在结果中
     */
    @Select("<script>"
        + "SELECT f.supplier_id AS supplierId, "
        + "       COUNT(*) AS dealCount, "
        + "       COALESCE(SUM(f.change_quantity), 0) AS purchaseQty "
        + "  FROM t_warehouse_stock_flow f "
        + "  LEFT JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0' "
        + " WHERE f.inout_type  = 'IN' "
        + "   AND f.del_flag    = '0' "
        + "   AND f.tenant_id   = '1001' "
        + "   AND f.supplier_id IN "
        + "   <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
        + " GROUP BY f.supplier_id"
        + "</script>")
    List<java.util.Map<String, Object>> selectSupplierDealStats(@Param("ids") java.util.Collection<Long> ids);

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

    /**
     * 按当前用户 + 产品 + 今日 + 流水类型集合（IN）SUM change_quantity（FIX-WMS-FLOWDICT-003）。
     *
     * <p>领用 / 退回 flow_type 按来源拆分（prod_pick_out / dept_pick_out / prod_return_in / pick_return_in）后，
     * 今日额度统计「已领 / 已退」须跨多键聚合，单键版 {@link #sumTodayByUserProductType} 会漏算。
     * flowTypes 空集合不会出现（service 端常量非空），mapper 不做空集兜底。</p>
     *
     * @param userId    当前操作人 user_id
     * @param productId 产品 ID
     * @param flowTypes 流水类型集合（如领用两键 + 历史 pick_out，或退回两键）
     * @return 当日这些类型该产品 change_quantity 总和（无记录返 0）
     */
    @Select("<script>"
        + "SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE operator_id = #{userId} "
        + "   AND product_id  = #{productId} "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag    = '0' "
        + "   AND flow_type IN "
        + "   <foreach collection='flowTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach> "
        + "</script>")
    BigDecimal sumTodayByUserProductTypes(@Param("userId") Long userId,
                                          @Param("productId") Long productId,
                                          @Param("flowTypes") java.util.Collection<String> flowTypes);

    /**
     * 全部人版（不按 operator 过滤）：某产品今日多键流水 SUM（邓博 row44 + Q3：物资额度按全部人全量，
     * admin/mp 同一池，PC 录入也计入）。多键聚合，与 {@link #sumTodayByUserProductTypes} 同口径仅去掉 operator。
     */
    @Select("<script>"
        + "SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE product_id  = #{productId} "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag    = '0' "
        + "   AND flow_type IN "
        + "   <foreach collection='flowTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach> "
        + "</script>")
    BigDecimal sumTodayByProductTypes(@Param("productId") Long productId,
                                      @Param("flowTypes") java.util.Collection<String> flowTypes);

    /** 全部人版单键：某产品今日某 flow_type SUM（不按 operator 过滤，row44/Q3）。 */
    @Select("SELECT COALESCE(SUM(change_quantity), 0) FROM t_warehouse_stock_flow "
        + " WHERE product_id = #{productId} AND DATE(flow_date) = CURDATE() AND del_flag = '0' "
        + "   AND flow_type = #{flowType}")
    BigDecimal sumTodayByProductType(@Param("productId") Long productId,
                                     @Param("flowType") String flowType);

    /**
     * 按当前用户 + 今日 + 流水类型集合（IN）SUM（不限定 productId；全产品「今日数据」卡片用）。
     *
     * @see #sumTodayByUserProductTypes 多键聚合背景
     */
    @Select("<script>"
        + "SELECT COALESCE(SUM(change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow "
        + " WHERE operator_id = #{userId} "
        + "   AND DATE(flow_date) = CURDATE() "
        + "   AND del_flag    = '0' "
        + "   AND flow_type IN "
        + "   <foreach collection='flowTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach> "
        + "</script>")
    BigDecimal sumTodayByUserTypes(@Param("userId") Long userId,
                                   @Param("flowTypes") java.util.Collection<String> flowTypes);

    /**
     * 按当前用户 + 今日 + 物资 belongType（JOIN product_info）+ 流水类型集合（IN）SUM。
     *
     * @see #sumTodayByUserProductTypes 多键聚合背景
     */
    @Select("<script>"
        + "SELECT COALESCE(SUM(f.change_quantity), 0) "
        + "  FROM t_warehouse_stock_flow f "
        + "  JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0' "
        + " WHERE f.operator_id = #{userId} "
        + "   AND DATE(f.flow_date) = CURDATE() "
        + "   AND p.belong_type = #{belongType} "
        + "   AND f.del_flag    = '0' "
        + "   AND f.flow_type IN "
        + "   <foreach collection='flowTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach> "
        + "</script>")
    BigDecimal sumTodayByUserMatTypes(@Param("userId") Long userId,
                                      @Param("flowTypes") java.util.Collection<String> flowTypes,
                                      @Param("belongType") String belongType);

}
