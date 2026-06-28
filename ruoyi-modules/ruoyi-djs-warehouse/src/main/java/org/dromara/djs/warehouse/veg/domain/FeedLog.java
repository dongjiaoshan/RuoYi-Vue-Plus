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
 * 饲料饲喂台账实体（WMS-VEG-FEED-LOG-001，果蔬全流程 spec 步8）。
 *
 * <p>对应表 {@code t_warehouse_feed_log}（V202607181200）。毛菜处理「果蔬处理录入」去向③饲料饲喂时，
 * 由 {@link org.dromara.djs.warehouse.veg.service.impl.VegetableHandleServiceImpl#submitProcess}
 * 同事务插入一条；按自然日 × 作物品类记录重量，{@code 不记录地块编号}（无 plot_id）。</p>
 *
 * @author djs
 * @since WMS-VEG-FEED-LOG-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_feed_log")
public class FeedLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 饲喂时间（DATETIME，精确到时分秒）。按自然日统计时在 SQL 层 DATE(feed_date) 截断。
     */
    private Date feedDate;

    /**
     * 作物 FK → {@code t_plant_crop_info.id}。
     */
    private Long cropId;

    /**
     * 作物名称（冗余快照）。
     */
    private String cropName;

    /**
     * 饲喂来源（字典 {@code djs_feed_type}：veg_handle 毛菜间 / warehouse 仓库；行64）。
     */
    private String feedType;

    /**
     * 产品 ID（FK → {@code t_warehouse_product_info.id}；按产品归集饲喂量用，
     * 毛菜间来源经 crop→related_product 反解可空）。
     */
    private Long productId;

    /**
     * 饲喂位置（库位 FK；仓库领用饲喂来源有值）。
     */
    private Long locationId;

    /**
     * 操作人 user_id。
     */
    private Long operatorId;

    /**
     * 饲喂重量(kg)。
     */
    private BigDecimal feedWeight;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

}
