package org.dromara.djs.warehouse.flow.constant;

import java.util.List;

/**
 * 损耗 / 饲喂类流水的 flow_type（djs_flow_type），按「动不动库存篮」区分。
 *
 * <p>规则：带库位的流水必须与 {@code t_warehouse_location_stock} 的变动一一对应 ——
 * 库存日汇总 / 月汇总按库位回放流水算期末，流水与库存篮对不上就会算错。</p>
 * <ul>
 *   <li>{@link #LOSS} / {@link #SETTLE_LOSS}：库存篮真实扣减的损耗（白条残量 / 地块收口与果蔬出库完成的剩余结转），
 *       计入库位的「损耗量」；</li>
 *   <li>{@link #PICK_LOSS} / {@link #PICK_FEED}：物资领用之后对已领出的货登记的损耗 / 饲喂。
 *       领用时库存篮已经扣过、流水也已记过一次出库，这两类只用于「今日领用剩余」聚合，
 *       <b>不参与</b>库位库存回放；</li>
 *   <li>{@link #FEED_OUT}：历史上按篮扣过库存篮的饲料出库（新写入一律 {@link #PICK_FEED}）。</li>
 * </ul>
 *
 * @author djs
 */
public final class StockFlowTypes {

    private StockFlowTypes() {
    }

    /** 损耗（扣库存篮）：白条残量转损耗。 */
    public static final String LOSS = "loss";

    /** 剩余结转损耗（扣库存篮）：毛菜处理地块收口 / 果蔬出库完成，把剩余库存结转为损耗。 */
    public static final String SETTLE_LOSS = "settle_loss";

    /** 领用后损耗（不扣库存篮）：物资领用后在领用剩余里登记的损耗，含药品损耗。 */
    public static final String PICK_LOSS = "pick_loss";

    /** 领用后饲喂（不扣库存篮）：物资领用后在领用剩余里登记的饲喂。 */
    public static final String PICK_FEED = "pick_feed";

    /** 饲料出库（历史，扣过库存篮）。 */
    public static final String FEED_OUT = "feed_out";

    /** 「今日领用剩余」里算作损耗的类型。 */
    public static final List<String> TODAY_LOSS = List.of(LOSS, PICK_LOSS);

    /** 「今日领用剩余」里算作饲喂的类型。 */
    public static final List<String> TODAY_FEED = List.of(FEED_OUT, PICK_FEED);
}
