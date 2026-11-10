package org.dromara.djs.warehouse.demand.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSheetVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSummaryItemVo;

import java.time.LocalDate;
import java.util.List;

/** 需求单打印：列表列出已提交的门店需求，普通明细与汇总只读取已确认及其后续履约状态。 */
@Mapper
public interface DemandPrintMapper {

    /** 已确认需求谓词（锚主表别名 {@code dm}；列表、明细及备注读取共用）。 */
    String EFFECTIVE_DEMAND_WHERE = """
          dm.product_id IS NOT NULL
          AND dm.store_id IS NOT NULL
          AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
        """;

    /** 已提交或已确认并继续履约的有效门店需求，排除草稿/取消/删除。 */
    String SUBMITTED_DEMAND_WHERE = """
          dm.product_id IS NOT NULL
          AND dm.store_id IS NOT NULL
          AND dm.demand_status IN ('SUBMITTED','CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
        """;

    /**
     * 订货单列表：按（需求日期 × 门店）聚合，需求品类数 = 去重产品数。
     *
     * <p>排序：需求日期降序，同日按门店名、门店 ID 升序。</p>
     *
     * @param page      分页（MP 对 GROUP BY 查询自动包 COUNT 子查询求总数）
     * @param demandDate 所选需求日期
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
        WHERE """ + SUBMITTED_DEMAND_WHERE + """
          AND dm.demand_date = #{demandDate}
          <if test="storeIds != null and storeIds.size() > 0">
            AND dm.store_id IN
            <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
          </if>
        GROUP BY dm.demand_date, dm.store_id
        ORDER BY dm.demand_date DESC, COUNT(DISTINCT dm.product_id) DESC, MAX(s.store_name) ASC, dm.store_id ASC
        </script>
        """)
    Page<DemandPrintSheetVo> selectSheetPage(IPage<DemandPrintSheetVo> page,
                                             @Param("demandDate") LocalDate demandDate,
                                             @Param("storeIds") List<Long> storeIds);

    /**
     * 某门店某日订货单明细：同产品同备注的需求合并成一行，备注不同分行（不拼接或截断备注）。
     *
     * <p>合并键 = {@code product_id + TRIM(demand_remark)}（空备注同组）；名称 / 规格 / 单位取需求行定格的冗余列。</p>
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
               MAX(pi.belong_type)          AS belongType,
               COALESCE(TRIM(dm.demand_remark), '') COLLATE utf8mb4_bin AS demandRemark
        FROM t_warehouse_demand_manage dm
        LEFT JOIN t_warehouse_product_info pi
               ON pi.id = dm.product_id AND pi.del_flag = '0' AND pi.tenant_id = '1001'
        WHERE dm.demand_date = #{demandDate}
          AND dm.store_id = #{storeId}
          AND """ + EFFECTIVE_DEMAND_WHERE + """
        GROUP BY dm.product_id, COALESCE(TRIM(dm.demand_remark), '') COLLATE utf8mb4_bin
        ORDER BY CASE MAX(pi.belong_type)
                   WHEN 'white_bar' THEN 1
                   WHEN 'pork'      THEN 2
                   WHEN 'egg'       THEN 3
                   WHEN 'dry_good'  THEN 4
                   WHEN 'gift_box'  THEN 5
                   WHEN 'vegetable' THEN 7
                   ELSE 6 END,
                 MAX(pi.product_id) ASC,
                 MAX(dm.product_name) ASC, demandRemark ASC
        """)
    List<DemandPrintItemVo> selectSheetItems(@Param("demandDate") LocalDate demandDate,
                                             @Param("storeId") Long storeId);

    /** 汇总数据保留名称/规格/单位/原料分量，服务层按名称合并并校验计量单位一致。 */
    @Select("""
        <script>
        SELECT COALESCE(NULLIF(TRIM(dm.product_name), ''), pi.product_name, '') AS productName,
               dm.product_spec AS productSpec,
               dm.demand_quantity AS demandQuantity,
               dm.product_unit AS productUnit,
               pi.belong_type AS belongType,
               pm.product_name AS rawMaterialName
        FROM t_warehouse_demand_manage dm
        LEFT JOIN t_warehouse_product_info pi
               ON pi.id = dm.product_id AND pi.del_flag = '0' AND pi.tenant_id = '1001'
        LEFT JOIN t_warehouse_product_info pm
               ON pm.id = pi.product_material AND pm.del_flag = '0' AND pm.tenant_id = '1001'
        WHERE dm.demand_date = #{demandDate}
          AND """ + EFFECTIVE_DEMAND_WHERE + """
          <if test="storeIds != null and storeIds.size() > 0">
            AND dm.store_id IN
            <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
          </if>
        ORDER BY productName ASC, dm.id ASC
        </script>
        """)
    List<DemandPrintSummaryItemVo> selectSummaryItems(@Param("demandDate") LocalDate demandDate,
                                                      @Param("storeIds") List<Long> storeIds);

    /**
     * 门店名（明细抬头用；门店已删也照取，单据上不能空着门店）。
     *
     * @param storeId 门店
     * @return 门店名；门店行不存在返回 null
     */
    @Select("SELECT store_name FROM t_md_store WHERE id = #{storeId} AND tenant_id = '1001' LIMIT 1")
    String selectStoreName(@Param("storeId") Long storeId);
}
