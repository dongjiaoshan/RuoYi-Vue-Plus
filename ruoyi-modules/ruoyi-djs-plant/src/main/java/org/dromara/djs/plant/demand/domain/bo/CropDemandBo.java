package org.dromara.djs.plant.demand.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.plant.demand.domain.CropDemand;

/**
 * 作物需求新增入参 BO（V6-R152 运营端「新增需求」弹框）。
 *
 * <p>弹框只有分类 / 内容 / 图片三项；需求日期与状态由服务端强制写入，前端传了也不认。</p>
 *
 * @author djs
 * @since V6-R152
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = CropDemand.class, reverseConvertGenerate = false)
public class CropDemandBo extends BaseEntity {

    /** 需求分类（字典 djs_plant_demand_category）。 */
    @NotBlank(message = "{plant.demand.category.required}")
    @Size(max = 32, message = "{plant.demand.category.size}")
    private String demandCategory;

    /** 需求内容（多行文本）。 */
    @NotBlank(message = "{plant.demand.content.required}")
    @Size(max = 1000, message = "{plant.demand.content.size}")
    private String demandContent;

    /** 需求图片 OSS ossIds 逗号分隔（可空）。 */
    @Size(max = 2048, message = "{plant.demand.images.size}")
    private String imageOssIds;
}
