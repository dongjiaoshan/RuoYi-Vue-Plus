package org.dromara.djs.plant.crop.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 作物信息实体（PLT-MD-001）。
 *
 * <p>对应表 {@code t_plant_crop_info}（V202606050900）：</p>
 * <ul>
 *   <li>{@code relatedProduct} → {@code t_warehouse_product_info.id}（D8 WMS-MD-002 同日，逻辑关联不写 FK）</li>
 *   <li>{@code plantingSeason} 字典 {@code djs_planting_season} 多选逗号分隔（spring,summer 等）</li>
 *   <li>{@code pickUnitPrice} 采摘单价（元/斤），P1#14 单价位置 V1 暂放此</li>
 * </ul>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_crop_info")
public class CropInfo extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 作物业务码（用户手填）。 */
    private String cropCode;

    /** 作物名称（例 白菜）。 */
    private String cropName;

    /** 缩略图 OSS objectName。 */
    private String cropImagePreview;

    /** 原图 OSS objectNames 逗号分隔。 */
    private String cropImageUrl;

    /** 品种名（例 京白菜 4 号）。 */
    private String varietyName;

    /** 品种来源/供应商（V1 自由文本）。 */
    private String varietyOrigin;

    /** 作物科属（字典 djs_crop_family）。 */
    private String cropFamily;

    /** 关联产品 FK → t_warehouse_product_info.id（D8 WMS-MD-002 同日，逻辑关联）。 */
    private Long relatedProduct;

    /** 种植季节（字典 djs_planting_season，多选逗号分隔）。 */
    private String plantingSeason;

    /** 适宜播种期（手输自由文本，例 "3 月上旬-4 月下旬"）。 */
    private String sowingPeriod;

    /** 生长最大周期（天）。 */
    private Integer maxCycle;

    /** 生长最小周期（天）。 */
    private Integer minCycle;

    /** 施肥间隔（天）。 */
    private Integer fertilizationInterval;

    /** 浇灌间隔（天）。 */
    private Integer irrigationInterval;

    /** 预计亩产（kg/亩）。 */
    private BigDecimal predictedPer;

    /** 品质描述。 */
    private String qualityDesc;

    /** 采摘单价（元/斤）。 */
    private BigDecimal pickUnitPrice;

    /** 主图 ossId（IMG-LIB-001 4 层 resolver L1；create 时按 cropName 自动匹配存入）。 */
    private String imageOssId;

    /** 图来源（IMG-LIB-001：0 自动匹配 / 1 用户手改）。 */
    private Integer imageSource;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
