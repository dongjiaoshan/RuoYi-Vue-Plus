package org.dromara.djs.warehouse.veg.domain;

/**
 * 有机饲喂的饲喂动物（字典 {@code djs_feed_animal}，{@code t_warehouse_feed_log.feed_animal} /
 * {@code t_warehouse_feed_daily_confirm.feed_animal}）。
 *
 * <p>与 {@code feed_type}（饲喂来源：毛菜间 / 仓库）是两个独立维度。只有出库去向「家禽饲料」
 * （{@link #OUT_DEST_POULTRY}）写 {@link #POULTRY}；毛菜处理、物资领用饲喂等没有家禽入口的路径一律 {@link #PIG}。</p>
 *
 * @author djs
 */
public final class FeedAnimal {

    /** 字典类型。 */
    public static final String DICT_TYPE = "djs_feed_animal";

    /** 猪只饲料。 */
    public static final String PIG = "pig";

    /** 家禽饲料。 */
    public static final String POULTRY = "poultry";

    /** 出库去向（djs_stock_out_dest）：猪只饲料。 */
    public static final String OUT_DEST_PIG = "feed";

    /** 出库去向（djs_stock_out_dest）：家禽饲料。 */
    public static final String OUT_DEST_POULTRY = "poultry_feed";

    private FeedAnimal() {
    }

    /**
     * 出库去向 → 饲喂动物；不是饲料去向返回 {@code null}（不写有机饲喂记录）。
     */
    public static String ofOutDest(String outDest) {
        if (OUT_DEST_PIG.equals(outDest)) {
            return PIG;
        }
        if (OUT_DEST_POULTRY.equals(outDest)) {
            return POULTRY;
        }
        return null;
    }

    /** 是否合法的饲喂动物值。 */
    public static boolean isValid(String animal) {
        return PIG.equals(animal) || POULTRY.equals(animal);
    }
}
