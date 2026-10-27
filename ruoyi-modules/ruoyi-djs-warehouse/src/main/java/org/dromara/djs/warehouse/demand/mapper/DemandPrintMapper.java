package org.dromara.djs.warehouse.demand.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSheetVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 需求单打印 Mapper（V7-R8，admin 仓库 › 需求管理 › 需求单打印）。
 *
 * <p><b>哪些需求行算数</b>：与需求管理汇总列表 {@link DemandManageMapper#selectDemandGroupList} 同口径——
 * 门店需求（{@code store_id} 非空）且挂了产品（{@code product_id} 非空），排除已取消 / 已删除，
 * 待确认（SUBMITTED）也计入。打印单是给仓库按需求单备货、线下勾「是否已备完货」用的，
 * 打出来的内容必须就是需求管理页上看得到的那批需求，不能另立一套口径。</p>
 *
 * <p><b>列表与明细共用 {@link #EFFECTIVE_DEMAND_WHERE}</b>：列表的「需求品类数」= 明细行数，
 * 两处 WHERE 只要漂一边，弹框里写 21 个品类、打出来却是 20 行。</p>
 *
 * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与 {@link DemandManageMapper} 范式一致）。</p>
 *
 * @author djs
 * @since V7-R8
 */
@Mapper
public interface DemandPrintMapper {

    /** 有效需求行谓词（锚主表别名 {@code dm}；列表与明细共用，口径同需求管理汇总列表）。 */
    String EFFECTIVE_DEMAND_WHERE = """
          dm.product_id IS NOT NULL
          AND dm.store_id IS NOT NULL
          AND dm.demand_status NOT IN ('CANCELLED','DELETED')
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
        """;

    /**
     * 订货单列表：按（需求日期 × 门店）聚合，需求品类数 = 去重产品数。
     *
     * <p>排序：需求日期升序（今日在前、明日在后，按备货先后），同日按门店名升序。</p>
     *
     * @param page      分页（MP 对 GROUP BY 查询自动包 COUNT 子查询求总数）
     * @param beginDate 需求日期起（含，null 不过滤）
     * @param endDate   需求日期止（含，null 不过滤）
     * @param storeIds  门店多选（空不过滤）
     * @return 订货单分页
     */
    @Select("""
        <script>
        SELECT dm.demand_date                 AS demandDate,
               dm.store_id                    AS storeId,
               MAX(s.store_name)              AS storeName,
               COUNT(DISTINCT dm.product_id)  AS categoryCount
        FROM t_warehouse_demand_manage dm
        LEFT JOIN t_md_store s
               ON s.id = dm.store_id AND s.tenant_id = '1001'
        WHERE """ + EFFECTIVE_DEMAND_WHERE + """
          <if test="beginDate != null">
            AND dm.demand_date &gt;= #{beginDate}
          </if>
          <if test="endDate != null">
            AND dm.demand_date &lt;= #{endDate}
          </if>
          <if test="storeIds != null and storeIds.size() > 0">
            AND dm.store_id IN
            <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
          </if>
        GROUP BY dm.demand_date, dm.store_id
        ORDER BY dm.demand_date ASC, MAX(s.store_name) ASC, dm.store_id ASC
        </script>
        """)
    Page<DemandPrintSheetVo> selectSheetPage(IPage<DemandPrintSheetVo> page,
                                             @Param("beginDate") LocalDate beginDate,
                                             @Param("endDate") LocalDate endDate,
                                             @Param("storeIds") List<Long> storeIds);

    /**
     * 某门店某日订货单明细：同产品多条需求合并成一行（需求量求和、备注去重拼接）。
     *
     * <p>合并键 = {@code product_id}，与需求管理汇总列表的分组键一致；名称 / 规格 / 单位取需求行定格的冗余列。</p>
     *
     * <p>排序按产品类别成组：白条 → 猪肉 → 鸡蛋 → 干货 → 礼盒 → 其他 → 果蔬，组内按产品编码升序
     * （甲方样张即此顺序：白条、猪下水在前，蔬菜在后），无编码再按产品名。</p>
     *
     * @param demandDate 需求日期
     * @param storeId    门店
     * @return 明细行
     */
    @Select("""
        SELECT dm.product_id                AS productId,
               MAX(dm.product_name)         AS productName,
               MAX(dm.product_spec)         AS productSpec,
               SUM(dm.demand_quantity)      AS demandQuantity,
               MAX(dm.product_unit)         AS productUnit,
               MAX(pi.belong_type)          AS belongType
        FROM t_warehouse_demand_manage dm
        LEFT JOIN t_warehouse_product_info pi
               ON pi.id = dm.product_id AND pi.del_flag = '0' AND pi.tenant_id = '1001'
        WHERE dm.demand_date = #{demandDate}
          AND dm.store_id = #{storeId}
          AND """ + EFFECTIVE_DEMAND_WHERE + """
        GROUP BY dm.product_id
        ORDER BY CASE MAX(pi.belong_type)
                   WHEN 'white_bar' THEN 1
                   WHEN 'pork'      THEN 2
                   WHEN 'egg'       THEN 3
                   WHEN 'dry_good'  THEN 4
                   WHEN 'gift_box'  THEN 5
                   WHEN 'vegetable' THEN 7
                   ELSE 6 END,
                 MAX(pi.product_id) ASC,
                 MAX(dm.product_name) ASC
        """)
    List<DemandPrintItemVo> selectSheetItems(@Param("demandDate") LocalDate demandDate,
                                             @Param("storeId") Long storeId);

    /** 完整备注按需求行顺序读取，由服务层去重拼接，避免 GROUP_CONCAT 的字节上限截断备货要求。 */
    @Select("""
        SELECT dm.product_id AS productId, TRIM(dm.demand_remark) AS demandRemark
        FROM t_warehouse_demand_manage dm
        WHERE dm.demand_date = #{demandDate}
          AND dm.store_id = #{storeId}
          AND NULLIF(TRIM(dm.demand_remark), '') IS NOT NULL
          AND """ + EFFECTIVE_DEMAND_WHERE + """
        ORDER BY dm.id ASC
        """)
    List<DemandPrintItemVo> selectSheetRemarks(@Param("demandDate") LocalDate demandDate,
                                               @Param("storeId") Long storeId);

    /**
     * 门店名（明细抬头用；门店已删也照取，单据上不能空着门店）。
     *
     * @param storeId 门店
     * @return 门店名；门店行不存在返回 null
     */
    @Select("SELECT store_name FROM t_md_store WHERE id = #{storeId} AND tenant_id = '1001' LIMIT 1")
    String selectStoreName(@Param("storeId") Long storeId);
}
