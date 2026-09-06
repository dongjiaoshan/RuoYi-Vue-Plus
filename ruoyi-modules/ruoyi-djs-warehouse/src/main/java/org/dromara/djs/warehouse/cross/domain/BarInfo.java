package org.dromara.djs.warehouse.cross.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 白条主表 minimal 实体（跨域共享：WMS-PIG-002 / CROSS-FLOW-001 / 燎毛 / 分割）。
 *
 * <p>对应表 {@code t_warehouse_bar_info}（V202606071100 WMS-PIG-002 重建以对齐 doc/11 §2.8）。</p>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>WMS-PIG-001 燎毛：写入 {@code arriveTime / arriveWeight / inWeight / inTime / inMethod / status}</li>
 *   <li>WMS-PIG-002 分割：cutDone 阶段写入 {@code outTime / outWeight / outMethod /
 *       acidRemoveTime / acidRemoveLoss}</li>
 *   <li>D10 CROSS-FLOW-001 跨域协同：生成 {@code barId} 业务码 + 状态推进</li>
 * </ul>
 *
 * <p>D9 closing Group B 已从 {@code cut/domain} 挪到 {@code cross/domain}，因白条是跨业务工序
 * 的共享主数据（燎毛 / 排酸 / 分割 / 发货），归 {@code cut} 包语义偏窄。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_bar_info")
public class BarInfo extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 业务码 {@code BAR+yyMMdd+4}（白条编号，CROSS-FLOW-001 生成）。
     */
    private String barId;

    /**
     * 出栏时间。
     */
    private Date marketingTime;

    /**
     * 出栏重量 kg。
     */
    private BigDecimal marketingWeight;

    /**
     * 燎毛间到场时间。
     */
    private Date arriveTime;

    /**
     * 到场重量 kg（燎毛前过磅）。
     */
    private BigDecimal arriveWeight;

    /**
     * 猪只耳号（关联 {@code t_farm_pig_info.ear_no}；外购无耳号）。
     */
    private String earNo;

    /**
     * 燎毛后入库重量 kg。
     */
    private BigDecimal inWeight;

    /**
     * 入库时间。
     *
     * <p>⚠️ 不是任何一个稳定的时间锚：称重、每次产品逐项入库、处理完成三处都会覆写它，
     * 终值才是「处理完成」那一刻。要按「处理完成」分桶请用 {@link #finishTime}。</p>
     */
    private Date inTime;

    /**
     * 处理完成时间（V6-R172）：燎毛处理完成（{@code finishBurn}）那一刻，只写一次、之后不变。
     *
     * <p>与 {@link #inTime} 的区别见后者注释。{@code arriveTime}（完成称重）+ 本列（完成处理）
     * 一起构成仓库日指标可复现分桶所需的两个不可变锚。</p>
     */
    private Date finishTime;

    /**
     * 入库方式：1=燎毛间 / 2=分割间（字典 {@code djs_bar_in_method}）。
     */
    private Integer inMethod;

    /**
     * 出白条库时间（WMS-PIG-002 cutDone 阶段写入）。
     */
    private Date outTime;

    /**
     * 出库重量 kg（WMS-PIG-002 cutDone 阶段写入）。
     */
    private BigDecimal outWeight;

    /**
     * 出库方式：1=发货领用 / 2=分割间（字典 {@code djs_bar_out_method}；本 ticket 写 2）。
     */
    private Integer outMethod;

    /**
     * 排酸时长（分钟，本 ticket cutDone 阶段计算写入）。
     */
    private Integer acidRemoveTime;

    /**
     * 排酸损耗 kg（= dripLoss，本 ticket cutDone 阶段写入）。
     */
    private BigDecimal acidRemoveLoss;

    /**
     * 分割产品重量 kg（白条分割成产品后总重 = Σ cut_out_in by white_bar_id，cutDone 阶段落库，邓博 row8）。
     */
    private BigDecimal cutProductWeight;

    /**
     * 分割损耗 kg（= 出库重量 − 分割产品重量，cutDone 阶段落库，邓博 row8）。
     */
    private BigDecimal cutLoss;

    /**
     * 采购日期（仅外购）。
     */
    private Date buyDate;

    /**
     * 采购重量 kg（仅外购）。
     */
    private BigDecimal buyWeight;

    /**
     * 供应商 ID（仅外购）。
     */
    private Long supplierId;

    /**
     * 标识号。
     */
    private String markId;

    /**
     * 状态：字典 {@code djs_bar_status} 7 态
     * （{@code pending_singe / singing / singed / in_stock / pending_cut / cutting / cut_done}）。
     */
    private String status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
