package org.dromara.djs.warehouse.stat.mapper;

/** 仓库日报与实时看板共用的 生产指标取数口径。 */
public final class WarehouseProductionStatSql {

    private WarehouseProductionStatSql() {
    }

    /** 按扇计，兼容历史未标半只的记录（原口径按整只处理）。 */
    public static final String CUT_SIDES = "CASE WHEN is_half = 1 THEN 1 ELSE 2 END";

    /** 优先按白条编号关联，双方无编号时按整猪白条 ID 关联。 */
    public static final String CUT_OUTPUT_PICKUP_JOIN = """
        FROM t_warehouse_stock_flow f
        LEFT JOIN t_warehouse_pig_cut_record c
          ON c.tenant_id = f.tenant_id
         AND ((f.white_bar_no <> '' AND c.white_bar_no = f.white_bar_no)
           OR ((f.white_bar_no IS NULL OR f.white_bar_no = '')
           AND (c.white_bar_no IS NULL OR c.white_bar_no = '') AND c.white_bar_id = f.white_bar_id))
         AND c.out_type = 'cut' AND c.del_flag = '0' AND c.pickup_time IS NOT NULL
        WHERE f.tenant_id = #{tenantId} AND f.flow_type = 'cut_out_in' AND f.inout_type = 'IN'
        """;

    public static final String EFFECTIVE_CUT_OUTPUT = " AND f.del_flag = '0' ";

    /** 毛菜有效入库流水，别名 f。 */
    public static final String RAW_VEG_INBOUND = """
        f.inout_type = 'IN' AND f.flow_type = 'veg_stock_in' AND f.change_quantity > 0
        """;

    /** 打包确认后方可计产，历史空状态保留。软删除由各查询统一过滤。 */
    public static final String EFFECTIVE_VEG_PACK = """
        (pp.pack_status IS NULL OR pp.pack_status IN ('', 'packed', 'shipped_out'))
        """;

    /** 每份打包有独立生产号。重复打印使用原号；老记录缺号时以主键识别。 */
    public static final String VEG_PORTION_KEY =
        "COALESCE(CONCAT('no:', NULLIF(TRIM(pp.produce_no), '')), CONCAT('id:', pp.id))";
    public static final String VEG_PORTIONS = "COUNT(DISTINCT " + VEG_PORTION_KEY + ")";

    public static final String PRODUCE_FROM = """
        FROM t_warehouse_product_production pp
        JOIN t_warehouse_product_info pi
          ON pi.id = pp.product_id AND pi.del_flag = '0' AND pi.tenant_id = pp.tenant_id
        """;

    public static final String VEG_PRODUCE_WHERE = """
        WHERE pp.del_flag = '0' AND pp.tenant_id = #{tenantId}
          AND pi.product_attr = 1 AND pi.belong_type = 'vegetable'
          AND
        """ + EFFECTIVE_VEG_PACK;
}
