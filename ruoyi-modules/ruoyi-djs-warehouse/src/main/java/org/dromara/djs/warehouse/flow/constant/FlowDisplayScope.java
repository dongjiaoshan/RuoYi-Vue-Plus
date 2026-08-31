package org.dromara.djs.warehouse.flow.constant;

import java.util.List;

/**
 * 出入库「展示口径」排除清单（djs_flow_type）。
 *
 * <p>入库记录 / 出库记录两页与出入库月汇总的入 / 出库汇总共用同一份排除清单 ——
 * 甲方拿汇总去对明细页，两边行集必须同集合，否则汇总里多出来的那批会被当成 bug 重报。</p>
 *
 * <p>被排除的流水本身仍照常写入（库存余额 / 损耗总览 / 盘点依赖），只是不进这两类页面的展示。</p>
 *
 * <p>⚠️ 勿复用 / 勿改 {@code MatFlowServiceImpl} 的权威键集——那是额度统计口径，与本展示口径无关。</p>
 *
 * @author djs
 * @since V6-R154
 */
public final class FlowDisplayScope {

    private FlowDisplayScope() {
    }

    /**
     * 入库展示排除：打包入库（pack_in）是「出库到发货月台」语义，不是真入库，
     * 不应出现在 admin 入库记录页 / 入库方式下拉 / 入库汇总里。
     */
    public static final List<String> IN_EXCLUDED = List.of("pack_in");

    /**
     * 出库展示排除：出库口径只保留库位领用 / 后台 / 盘点类真出库；
     * 生产发货（ship_out / pack_consume）与领用后损耗（loss）不展示。
     */
    public static final List<String> OUT_EXCLUDED = List.of("loss", "ship_out", "pack_consume");
}
