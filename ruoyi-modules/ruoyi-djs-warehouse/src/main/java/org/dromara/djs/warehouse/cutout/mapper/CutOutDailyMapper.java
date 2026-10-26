package org.dromara.djs.warehouse.cutout.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyDetailQuery;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyQuery;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyDetailVo;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyVo;

import java.util.List;

/**
 * 猪肉日分割出库统计 聚合 + 明细 Mapper（V7-R6）。
 *
 * <p><b>compute-on-read</b>：按天实时 GROUP BY 既有业务记录，不建汇总表、不加跑批
 * （与兄弟页「出入库统计」同做法）。统计的两类记录：</p>
 * <ol>
 *   <li><b>仓库出库</b>：【白条分割出库管理】选「仓库出库」写的出库流水
 *       （{@code t_warehouse_stock_flow}，按出库去向汇总），见 {@link #WAREHOUSE_WHERE}；</li>
 *   <li><b>门店需求</b>：【白条分割出库管理】选「门店需求」落的产出记录 + 白条直接发门店
 *       （【猪只入库管理】白条选门店 / 白条出库领用）落的产出记录
 *       （{@code t_warehouse_product_production}，按门店汇总），见 {@link #STORE_WHERE}。</li>
 * </ol>
 *
 * <p>自定义 {@code @Select} 含聚合，WHERE 显式带 {@code tenant_id} 与 {@code del_flag}
 * ——多租户拦截器对聚合不保证注入。</p>
 *
 * <h3>⚠️ 聚合与明细共用同一份 WHERE，不准复制多套</h3>
 * <p>甲方点「查看」是拿明细核对汇总行的，两条链路行集必须严格同集合：
 * 要改统计范围只能改 {@link #WAREHOUSE_WHERE} / {@link #STORE_WHERE} 常量本身，
 * 不准在某一个方法上单独加条件。于是「明细逐条求和 = 汇总行出库总重量」是构造上成立的。</p>
 *
 * @author djs
 * @since V7-R6
 */
@Mapper
public interface CutOutDailyMapper {

    /**
     * 仓库出库口径（聚合与明细共用）。
     *
     * <ul>
     *   <li>{@code flow_type='cut_room_out' AND inout_type='OT'}：分割车间直接出库的流水，
     *       唯一写入方是 {@code LocationStockServiceImpl.cutRoomOut}（由出入库工作台
     *       {@code InoutWorkbenchService.directOut} 调）。</li>
     *   <li>{@code white_bar_no} 为空：{@code directOut} 有两个调用方 ——
     *       白条分割出库（{@code submitCut} 去向=仓库出库，扣的是分割产出篮，篮子
     *       {@code white_bar_no} 恒空，见 {@code LocationStockMapper.addByProductLocationEarNo}）与
     *       猪只入库管理的副产直接出库（{@code submitBurn} 去向=仓库出库，扣的是燎毛库存行，
     *       燎毛每个产出行都生成白条流水号，{@code white_bar_no} 恒非空）。
     *       流水的 {@code white_bar_no} 抄自被扣的库存行，所以「为空」= 只留白条分割出库。</li>
     *   <li>量用绝对值列 {@code change_quantity}（不用带符号的 {@code change_num}）；
     *       分割产出按称重入篮，单位恒为 kg。</li>
     * </ul>
     */
    String WAREHOUSE_WHERE = """
        WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
          AND f.inout_type = 'OT'
          AND f.flow_type = 'cut_room_out'
          AND (f.white_bar_no IS NULL OR f.white_bar_no = '')
        """;

    /**
     * 门店需求口径（聚合与明细共用）。
     *
     * <p>{@code t_warehouse_product_production} 的写入方里，只有三处给产出记录写
     * {@code white_bar_no} / {@code white_bar_id}（均在 {@code ProductProductionServiceImpl}）：</p>
     * <ol>
     *   <li>{@code submitDryPackInternal} 的 cutStore 分支（= {@code submitCutStorePack}，
     *       白条分割出库管理选「门店需求」）—— 普通肉品打包（{@code submitDryPack}）走同一方法但不写；</li>
     *   <li>{@code submitWhiteBarOut}（白条 / 猪肉发门店）；</li>
     *   <li>{@code submitWarehouseOut}（后台出库，不发门店：{@code store_id} 恒空、
     *       {@code deliver_dest='warehouse_out'}）—— 由下面两条排除。</li>
     * </ol>
     * <p>果蔬 / 芹菜 / 礼盒 / 普通肉品与其他产品打包都不写这两列，
     * 所以「{@code white_bar_no} 或 {@code white_bar_id} 非空 + 有门店 + 非后台出库」= 恰好是 1、2 两类。
     * {@code white_bar_id} 补的是整猪领用分割单（无白条流水号）与外购白条这两种没有流水号的行。</p>
     *
     * <p>量用 {@code product_weight}：这两类都按实称重量落库（kg），与产品计价单位无关。</p>
     */
    String STORE_WHERE = """
        WHERE p.del_flag = '0' AND p.tenant_id = #{tenantId}
          AND p.store_id IS NOT NULL
          AND ((p.white_bar_no IS NOT NULL AND p.white_bar_no &lt;&gt; '') OR p.white_bar_id IS NOT NULL)
          AND (p.deliver_dest IS NULL OR p.deliver_dest &lt;&gt; 'warehouse_out')
        """;

    /**
     * 列表聚合 SQL：仓库出库按 <b>日 × 出库去向</b>、门店需求按 <b>日 × 门店</b>，UNION ALL 后统一排序。
     *
     * <p>两个分支由 service 按筛选条件决定开关（{@code withWarehouse / withStore}，至少开一个）：
     * 选了「门店需求」或选了门店 → 不出仓库出库行；选了某个字典去向 → 不出门店需求行。</p>
     *
     * <p>聚合放子查询、外层只联门店名 + 排序：MyBatis-Plus 自动 count 就是聚合后的行数。
     * 门店按主键 LEFT JOIN 不改变行数；不过滤门店 {@code del_flag}，门店后来被删，历史统计仍显示名称。
     * ORDER BY 拿整个行身份 {@code (statDate, destType, outDest, storeId)} 排，键唯一 ⇒ 翻页全序。</p>
     */
    String DAILY_SQL = "<script>"
        + """
        SELECT g.statDate, g.destType, g.outDest, g.storeId, s.store_name AS storeName, g.totalWeight
        FROM (
        """
        + "<if test=\"withWarehouse\">"
        + """
            SELECT DATE(f.flow_date) AS statDate,
                   'warehouse' AS destType,
                   COALESCE(f.stock_out_dest, '') AS outDest,
                   NULL AS storeId,
                   COALESCE(SUM(f.change_quantity), 0) AS totalWeight
            FROM t_warehouse_stock_flow f
        """
        + WAREHOUSE_WHERE
        + """
            <if test="query.beginDate != null">
              AND f.flow_date &gt;= #{query.beginDate}
            </if>
            <if test="query.endDate != null">
              AND f.flow_date &lt; DATE_ADD(#{query.endDate}, INTERVAL 1 DAY)
            </if>
            <if test="query.outDest != null and query.outDest != ''">
              AND f.stock_out_dest = #{query.outDest}
            </if>
            GROUP BY DATE(f.flow_date), COALESCE(f.stock_out_dest, '')
        """
        + "</if>"
        + "<if test=\"withWarehouse and withStore\"> UNION ALL </if>"
        + "<if test=\"withStore\">"
        + """
            SELECT DATE(p.produce_time) AS statDate,
                   'store' AS destType,
                   'store_demand' AS outDest,
                   p.store_id AS storeId,
                   COALESCE(SUM(p.product_weight), 0) AS totalWeight
            FROM t_warehouse_product_production p
        """
        + STORE_WHERE
        + """
            <if test="query.beginDate != null">
              AND p.produce_time &gt;= #{query.beginDate}
            </if>
            <if test="query.endDate != null">
              AND p.produce_time &lt; DATE_ADD(#{query.endDate}, INTERVAL 1 DAY)
            </if>
            <if test="query.storeId != null">
              AND p.store_id = #{query.storeId}
            </if>
            GROUP BY DATE(p.produce_time), p.store_id
        """
        + "</if>"
        + """
        ) g
        LEFT JOIN t_md_store s ON s.id = g.storeId
        ORDER BY g.statDate DESC, g.destType DESC, g.outDest, g.storeId
        """
        + "</script>";

    /**
     * 仓库出库明细：某天某个出库去向的逐条出库流水（与 {@link #DAILY_SQL} 仓库分支同一份 WHERE）。
     *
     * <p>日期用半开区间 {@code [statDate, statDate + 1 天)}，与聚合的 {@code DATE(flow_date)} 同集合；
     * 出库去向 {@code COALESCE(...) = COALESCE(#{...}, '')} 双向兜空，「未指定」那一桶传 null / 空串都能对上。
     * 产品按 id LEFT JOIN 且不过滤档案 {@code del_flag}：产品档案后来被删，明细行也不能少，否则求和对不上汇总。</p>
     */
    String WAREHOUSE_DETAIL_SQL = "<script>"
        + """
        SELECT pi.product_id      AS productCode,
               pi.product_name    AS productName,
               f.change_quantity  AS quantity,
               f.ear_no           AS earNo,
               f.flow_date        AS outTime
        FROM t_warehouse_stock_flow f
        LEFT JOIN t_warehouse_product_info pi ON pi.id = f.product_id
        """
        + WAREHOUSE_WHERE
        + """
          AND f.flow_date &gt;= #{query.statDate}
          AND f.flow_date &lt; DATE_ADD(#{query.statDate}, INTERVAL 1 DAY)
          AND COALESCE(f.stock_out_dest, '') = COALESCE(#{query.outDest}, '')
        ORDER BY f.flow_date DESC, f.id DESC
        """
        + "</script>";

    /**
     * 门店需求明细：某天送往某门店的逐条产出记录（与 {@link #DAILY_SQL} 门店分支同一份 WHERE）。
     *
     * <p>产品名取产出记录自己的 {@code product_name}（即发出去的那个名字），编码联产品档案取业务码。</p>
     */
    String STORE_DETAIL_SQL = "<script>"
        + """
        SELECT pi.product_id     AS productCode,
               p.product_name    AS productName,
               p.product_weight  AS quantity,
               p.ear_no          AS earNo,
               p.produce_time    AS outTime
        FROM t_warehouse_product_production p
        LEFT JOIN t_warehouse_product_info pi ON pi.id = p.product_id
        """
        + STORE_WHERE
        + """
          AND p.produce_time &gt;= #{query.statDate}
          AND p.produce_time &lt; DATE_ADD(#{query.statDate}, INTERVAL 1 DAY)
          AND p.store_id = #{query.storeId}
        ORDER BY p.produce_time DESC, p.id DESC
        """
        + "</script>";

    /**
     * 列表分页。
     *
     * @param page          分页参数
     * @param tenantId      租户（V1 固定 '1001'）
     * @param query         筛选条件（日期两端可空 = 不限）
     * @param withWarehouse 是否出仓库出库行
     * @param withStore     是否出门店需求行（与 withWarehouse 至少一个为 true）
     * @return 列表行（日期倒序）
     */
    @Select(DAILY_SQL)
    IPage<CutOutDailyVo> selectDailyPage(IPage<CutOutDailyVo> page,
                                         @Param("tenantId") String tenantId,
                                         @Param("query") CutOutDailyQuery query,
                                         @Param("withWarehouse") boolean withWarehouse,
                                         @Param("withStore") boolean withStore);

    /**
     * 仓库出库明细（抽屉列表与导出共用）。
     *
     * @param tenantId 租户
     * @param query    statDate + outDest
     * @return 明细行（出库时间倒序）
     */
    @Select(WAREHOUSE_DETAIL_SQL)
    List<CutOutDailyDetailVo> selectWarehouseDetail(@Param("tenantId") String tenantId,
                                                    @Param("query") CutOutDailyDetailQuery query);

    /**
     * 门店需求明细（抽屉列表与导出共用）。
     *
     * @param tenantId 租户
     * @param query    statDate + storeId
     * @return 明细行（出库时间倒序）
     */
    @Select(STORE_DETAIL_SQL)
    List<CutOutDailyDetailVo> selectStoreDetail(@Param("tenantId") String tenantId,
                                                @Param("query") CutOutDailyDetailQuery query);
}
