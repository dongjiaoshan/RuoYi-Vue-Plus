package org.dromara.djs.warehouse.veg.domain;

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
 * 毛菜处理汇总实体（WMS-VEG-001）。
 *
 * <p>对应表 {@code t_warehouse_vegetable_handle}（V202606071200）。</p>
 *
 * <h3>状态机</h3>
 * <ul>
 *   <li>{@code pending}：首次创建后（首次 record_type=1 采收前）</li>
 *   <li>{@code processing}：有 ≥1 条 handle_record 后</li>
 *   <li>{@code done}：mp 显式提交 finish 时</li>
 * </ul>
 *
 * <h3>跨表事务</h3>
 * <p>由 {@link org.dromara.djs.warehouse.veg.service.impl.VegetableHandleServiceImpl#submitHarvest}
 * （采摘录入：handle_record INSERT + 建毛菜保鲜库地块篮 + veg_stock_in 流水 + 汇总聚合 UPDATE）与
 * {@link org.dromara.djs.warehouse.veg.service.impl.VegetableHandleServiceImpl#submitProcess}
 * （处理录入：FIFO 扣篮 + veg_stock_out 流水 + 收口结转损耗）维护，两者都同步推进
 * planting_record.handle_status。</p>
 *
 * <p><b>{@code handled_weight}（果蔬处理重量）只有 {@code submitProcess} 一个写入方</b>，语义 = 毛菜处理录入的
 * 月台 + 饲喂。毛菜间出库管理（{@code VegOutServiceImpl}）<b>不写这一列</b> —— 甲方 2026-08-19 定
 * {@code loss = 地块入库量 − 果蔬月台 − 有机饲喂 − 出库}，它是公式里与月台、饲喂<b>并列</b>的「出库」项，
 * 并列项不能同时算进其中一项；它扣实物库存，收口按剩余库存结转损耗时自然已把这批 kg 减掉。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_vegetable_handle")
public class VegetableHandle extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 上游种植记录 FK → {@code t_warehouse_planting_record.id}。
     */
    private Long plantingRecordId;

    /**
     * 地块 FK → {@code t_plant_plot_info.id}（冗余）。
     */
    private Long plotId;

    /**
     * 作物 FK → {@code t_plant_crop_info.id}（冗余）。
     */
    private Long cropId;

    /**
     * 产品 FK → {@code t_warehouse_product_info.id}（belong_type=vegetable）。
     */
    private Long productId;

    /**
     * 采摘开始时间（首次 record_type=1 采收时写）。
     */
    private Date pickStartTime;

    /**
     * 采摘结束时间。
     */
    private Date pickEndTime;

    /**
     * 已摘重量(kg)。
     */
    private BigDecimal pickedWeight;

    /**
     * 处理后重量(kg) = 月台 + 入库。
     */
    private BigDecimal handledWeight;

    /**
     * 饲料饲喂(kg)。
     */
    private BigDecimal feedWeight;

    /**
     * 发往蔬菜月台(kg)。
     */
    private BigDecimal sendPlatformWeight;

    /**
     * 入库(kg)。
     */
    private BigDecimal stockInWeight;

    /**
     * 损耗(kg) = 已摘 - 处理 - 饲喂（业务计算字段）。
     */
    private BigDecimal lossWeight;

    /**
     * 是否完成称重 1=是 / 2=否。
     */
    private Integer isWeighed;

    /**
     * 是否处理完 1=是 / 2=否。
     */
    private Integer isFinish;

    /**
     * 状态 djs_veg_handle_status：pending / processing / done。
     */
    private String handleStatus;

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
