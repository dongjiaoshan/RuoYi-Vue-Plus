package org.dromara.djs.store.stat.mapper;

/** 门店退回日指标、月卡及下钻共用的登记口径。调用方提供 r（退回）和 p（产品档案）别名。 */
public final class StoreReturnStatSql {

    private StoreReturnStatSql() {
    }

    public static final String PORK = "p.belong_type IN ('pork', 'white_bar')";
    public static final String VEGETABLE = "p.belong_type = 'vegetable'";
    private static final String PRODUCT_UNIT = "LOWER(TRIM(p.product_unit))";
    private static final String KG = PRODUCT_UNIT + " IN ('kg','公斤','千克')";
    public static final String VEGETABLE_KG = "(" + VEGETABLE + " AND " + KG + ")";
    // 份、把、盒等计件单位直接累加；其他重量单位缺少份数换算依据。
    public static final String VEGETABLE_PORTION = "(" + VEGETABLE + " AND " + PRODUCT_UNIT
        + " NOT IN ('kg','公斤','千克','g','克','斤','市斤','磅','lb','lbs','吨','t','mg','毫克'))";

    private static final String PACKAGE_SUFFIX = "[[:space:]]*装?([[:space:]]*[/／][[:space:]]*(份|包|袋|盒|件|个))?";
    private static final String TWO_JIN = "(2[[:space:]]*斤|两[[:space:]]*斤|二[[:space:]]*斤)";
    private static final String ONE_KILO_PACK = "(1[[:space:]]*(kg|公斤|千克)|1000[[:space:]]*(g|克))";

    /**
     * 单位明确写成 2斤装或 2斤/份可直接识别。普通包装单位需搭配 2 斤、1 kg 或 1000 g 单包规格。
     * 规格整串匹配，排除 12 斤、多包组合及说明不完整的历史规格。
     */
    private static final String PACK = "(REGEXP_LIKE(TRIM(p.product_unit), '^" + TWO_JIN + PACKAGE_SUFFIX + "$')"
        + " OR (" + PRODUCT_UNIT + " IN ('份','包','袋','盒','件','个','portion','pack')"
        + " AND REGEXP_LIKE(TRIM(p.product_spec), '^(" + TWO_JIN + "|" + ONE_KILO_PACK + ")"
        + PACKAGE_SUFFIX + "$', 'i')))";

    private static final String ELIGIBLE = "(NOT (" + PORK + ") OR " + KG + " OR " + PACK + ")"
        + " AND (NOT (" + VEGETABLE + ") OR " + VEGETABLE_KG + " OR " + VEGETABLE_PORTION + ")";
    public static final String UNIT = "CASE WHEN " + PORK + " THEN 'kg' WHEN " + VEGETABLE_PORTION
        + " THEN '份' ELSE p.product_unit END";
    public static final String FROM = """
         FROM t_store_return r
         JOIN t_warehouse_product_info p ON p.id = r.product_id AND p.tenant_id = r.tenant_id
        """;
    public static final String WHERE = """
         WHERE r.tenant_id = #{tenantId} AND r.del_flag = '0' AND p.del_flag = '0'
           AND r.return_direction = 'store_to_warehouse' AND r.store_id IS NOT NULL
           AND
        """ + ELIGIBLE + " ";
}
