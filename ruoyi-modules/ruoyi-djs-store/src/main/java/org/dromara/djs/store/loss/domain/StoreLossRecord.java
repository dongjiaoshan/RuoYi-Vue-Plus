package org.dromara.djs.store.loss.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 门店损耗记录实体（{@code t_store_loss_record}，DENGBO-R15）。
 *
 * <p>由 {@code StoreLossJob} 每晚定时任务落盘，两类损耗共表（{@link #lossType}）：</p>
 * <ol>
 *   <li><b>门店日损耗</b>（{@code store_daily_loss}）：读 {@code t_store_daily_ledger} 当日各行 {@code loss_qty}
 *       逐产品搬入，{@link #productId} 填对应产品，{@link #lossQty} 原样含负（盘盈不钳 0）。</li>
 *   <li><b>白条分割损耗</b>（{@code white_bar_split_loss}）：按门店汇总一行，{@link #productId}=哨兵 0，
 *       {@link #lossQty} = {@link #whiteBarArriveWeight}（当日白条到店重）− {@link #whiteBarSplitWeight}
 *       （当日白条退回产品入库重）钳 0；当天无白条到店则不记录。</li>
 * </ol>
 *
 * <p>幂等：定时任务先按 {@code loss_date} 物理删该日旧行再重插。唯一键
 * {@code (tenant_id, store_id, product_id, loss_date, loss_type)} 兜底。</p>
 *
 * @author djs
 * @since DENGBO-R15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_loss_record")
public class StoreLossRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花）。 */
    @TableId
    private Long id;

    /** 门店 FK → {@code t_md_store.id}。 */
    private Long storeId;

    /** 产品 FK → {@code t_warehouse_product_info.id}（门店日损耗填产品；白条分割损耗汇总行=哨兵 0）。 */
    private Long productId;

    /** 产品单位（门店日损耗行由 {@code t_warehouse_product_info.product_unit} 补；白条分割损耗行 NULL）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String productUnit;

    /** 损耗量（门店日损耗=ledger.loss_qty 原样含负；白条分割损耗=到店重−退回入库重 钳 0）。 */
    private BigDecimal lossQty;

    /** 损耗日期（到天）。 */
    private LocalDate lossDate;

    /** 损耗类型：{@code store_daily_loss} 门店日损耗 / {@code white_bar_split_loss} 白条分割损耗。 */
    private String lossType;

    /** 白条到店重量kg（仅白条分割损耗行有值；门店日损耗行 NULL）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal whiteBarArriveWeight;

    /** 白条退回入库重量kg（仅白条分割损耗行有值；门店日损耗行 NULL）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal whiteBarSplitWeight;

    /** 备注。 */
    private String remark;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;
}
