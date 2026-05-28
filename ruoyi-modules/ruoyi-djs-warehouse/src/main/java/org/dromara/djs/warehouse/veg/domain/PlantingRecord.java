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
 * 仓库视角种植记录实体（WMS-VEG-001，doc/11 §2.14）。
 *
 * <p>对应表 {@code t_warehouse_planting_record}（V202606071200 建）。</p>
 *
 * <p>本 ticket 由 testing-ai.md §0 mock seed 灌入；D14 CROSS-FLOW-002 listener 落地后由
 * 监听种植采摘事件自动 INSERT。</p>
 *
 * <p>{@code handle_status}：mp 处理工领取标记（pending / processing / done）。WMS-VEG-001 提交
 * record 推进；所有 record 完成 + is_finish=1 时置 done。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_planting_record")
public class PlantingRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long plotId;

    private Long cropId;

    /**
     * 冗余产品 ID（D9 closing 加列，FK → {@code t_warehouse_product_info.id}；
     * PLT-PICK-001 / WMS-VEG-001 写入。用于 stock_flow / 追溯链按 product 关联，详 D9 _open-issues #18 决策 d）。
     */
    private Long productId;

    private String plotName;

    private String cropName;

    /**
     * 种植日期。
     */
    private java.sql.Date plantDate;

    /**
     * 采摘日期。
     */
    private java.sql.Date harvestDate;

    /**
     * 收获产量(kg)。
     */
    private BigDecimal harvestWeight;

    /**
     * 预计产量。
     */
    private BigDecimal expectYield;

    /**
     * 平均亩产。
     */
    private BigDecimal avgYield;

    /**
     * 是否损失 1=是 / 2=否。
     */
    private Integer isLoss;

    /**
     * 灾害记录（多个逗号分隔）。
     */
    private String disasterRecord;

    /**
     * 班组 ID。
     */
    private Long teamId;

    /**
     * 班组名称（冗余）。
     */
    private String teamName;

    /**
     * 数据生成时间（一般同采摘时间）。
     */
    private Date dataDate;

    /**
     * 处理状态 djs_veg_handle_status：pending / processing / done。
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
