package org.dromara.djs.warehouse.flow.domain.query;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 出入库统计「查看详情」查询参数（V6-R186 入库明细 / 出库明细共用）。
 *
 * <p><b>继承 {@link InoutStatQuery}</b> 是刻意的：明细走的是与汇总<b>同一份</b> FROM / WHERE
 * （{@code InoutStatMapper.IN_WHERE} / {@code OUT_WHERE}），那份片段读的就是父类的字段。
 * 前端点「查看详情」时把列表当前的筛选条件原样带过来，明细就必然是那一行的真子集，
 * 甲方逐条加起来必然等于汇总行的量。</p>
 *
 * <p>本类只补两类字段：</p>
 * <ol>
 *   <li><b>分组键</b>（{@link #productCode} / {@link #flowType} / {@link #supplierName} /
 *       {@link #outDest}）——把明细钉到被点击的那一行；</li>
 *   <li><b>明细专属筛选</b>（{@link #operatorId} 记录人）。日期区间不另开字段，直接复用父类的
 *       {@code dateFrom / dateTo}：弹窗里的「入库日期」筛选本来就是把汇总的区间再收窄一次，
 *       两者同一个参数才不会出现「弹窗筛了日期但求和还按老区间」的错位。</li>
 * </ol>
 *
 * @author djs
 * @since V6-R186
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InoutStatDetailQuery extends InoutStatQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品编码（{@code t_warehouse_product_info.product_id} 业务码，如 P0001 / Y00099）。
     *
     * <p>分组键里唯一必填项：编码在租户内唯一（{@code uk_product_id}），
     * 名称 / 类型 / 规格 / 单位都由它函数决定，故不必再逐个传过来比对。</p>
     */
    @NotBlank(message = "产品编码不能为空")
    private String productCode;

    /**
     * 入库方式（{@code flow_type}；仅入库明细）。
     *
     * <p>列不可空，故这里也必须给值——不给就等于把该产品所有入库方式的流水混进一个汇总行的明细。</p>
     */
    private String flowType;

    /**
     * 供应商名称（仅入库明细）。
     *
     * <p>汇总按 {@code COALESCE(sp.supplier_name, '')} 分组，所以这里传的是<b>名称</b>不是 id。
     * 「无供应商」那一桶传 null 或空串都行，SQL 侧用 {@code COALESCE(#{...}, '')} 双向兜空。</p>
     */
    private String supplierName;

    /**
     * 出库去向（{@code stock_out_dest} 原始值；仅出库明细）。
     *
     * <p>「未指定」那一桶传 null 或空串都行，理由同 {@link #supplierName}。
     * 与父类的 {@code stockOutDests}（多选筛选）不是一回事：那个是列表的筛选条件，
     * 这个是被点击行的分组键。</p>
     */
    private String outDest;

    /**
     * 记录人（{@code t_warehouse_stock_flow.operator_id}）。
     *
     * <p>甲方 row186 的「入库记录人 / 出库记录人」筛选。取 {@code operator_id} 而不是
     * {@code create_by}：兄弟页「入库记录 / 出库记录」的「操作人」列与筛选走的就是它
     * （{@code StockFlowVo.operatorId}），甲方拿两边对人名必须对得上。</p>
     */
    private Long operatorId;
}
