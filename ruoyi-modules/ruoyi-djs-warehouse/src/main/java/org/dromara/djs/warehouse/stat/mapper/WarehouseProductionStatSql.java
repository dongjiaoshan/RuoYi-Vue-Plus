package org.dromara.djs.warehouse.stat.mapper;

/** 仓库日报与实时看板共用的 V1.1 取数口径。 */
public final class WarehouseProductionStatSql {

    private WarehouseProductionStatSql() {
    }

    /** 按扇计，兼容历史未标半只的记录（原口径按整只处理）。 */
    public static final String CUT_SIDES = "CASE WHEN is_half = 1 THEN 1 ELSE 2 END";

    /** 毛菜有效入库流水，别名 f。 */
    public static final String RAW_VEG_INBOUND = """
        f.inout_type = 'IN' AND f.flow_type = 'veg_stock_in' AND f.change_quantity > 0
        """;

    /** 打包确认后方可计产，历史空状态保留。软删除由各查询统一过滤。 */
    public static final String EFFECTIVE_VEG_PACK = """
        (pp.pack_status IS NULL OR pp.pack_status IN ('', 'packed', 'shipped_out'))
        """;

    /** 每份打包有独立生产号。重复打印使用原号；老记录缺号时以主键识别。 */
    public static final String VEG_PORTIONS = """
        COUNT(DISTINCT COALESCE(CONCAT('no:', NULLIF(TRIM(pp.produce_no), '')), CONCAT('id:', pp.id)))
        """;

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
