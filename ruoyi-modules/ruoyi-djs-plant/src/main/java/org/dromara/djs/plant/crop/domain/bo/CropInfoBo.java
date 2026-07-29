package org.dromara.djs.plant.crop.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.plant.crop.domain.CropInfo;

import java.math.BigDecimal;

/**
 * 作物入参 BO（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = CropInfo.class, reverseConvertGenerate = false)
public class CropInfoBo extends BaseEntity {

    private Long id;

    @NotBlank(message = "{plant.crop.code.required}", groups = OnCreate.class)
    @Size(max = 32, message = "{plant.crop.code.size}")
    private String cropCode;

    @NotBlank(message = "{plant.crop.name.required}")
    @Size(max = 64, message = "{plant.crop.name.size}")
    private String cropName;

    @Size(max = 512, message = "{plant.crop.image.size}")
    private String cropImagePreview;

    @Size(max = 2048, message = "{plant.crop.image.size}")
    private String cropImageUrl;

    @Size(max = 64, message = "{plant.crop.variety.size}")
    private String varietyName;

    @Size(max = 128, message = "{plant.crop.varietyOrigin.size}")
    private String varietyOrigin;

    @Size(max = 32, message = "{plant.crop.family.size}")
    private String cropFamily;

    /** 作物属（自由文本，例 芸薹属）。 */
    @Size(max = 32, message = "{plant.crop.genus.size}")
    private String cropGenus;

    /** 关联产品 FK → t_warehouse_product_info.id。 */
    private Long relatedProduct;

    @Size(max = 64, message = "{plant.crop.season.size}")
    private String plantingSeason;

    @Size(max = 64, message = "{plant.crop.sowingPeriod.size}")
    private String sowingPeriod;

    @PositiveOrZero(message = "{plant.crop.maxCycle.positive}")
    private Integer maxCycle;

    @PositiveOrZero(message = "{plant.crop.minCycle.positive}")
    private Integer minCycle;

    @PositiveOrZero(message = "{plant.crop.fertInterval.positive}")
    private Integer fertilizationInterval;

    @PositiveOrZero(message = "{plant.crop.irrInterval.positive}")
    private Integer irrigationInterval;

    @PositiveOrZero(message = "{plant.crop.predictedPer.positive}")
    private BigDecimal predictedPer;

    @Size(max = 500, message = "{plant.crop.quality.size}")
    private String qualityDesc;

    @PositiveOrZero(message = "{plant.crop.unitPrice.positive}")
    private BigDecimal pickUnitPrice;

    /** 主图 ossId（IMG-LIB-001；用户手选则随提交带 imageSource=1）。 */
    @Size(max = 32, message = "{plant.crop.image.size}")
    private String imageOssId;

    /** 图来源（IMG-LIB-001：0 自动匹配 / 1 用户手改；前端手选图后置 1）。 */
    private Integer imageSource;

    public interface OnCreate {
    }
}
