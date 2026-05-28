package org.dromara.djs.plant.organic.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.plant.organic.domain.PlotOrganic;

import java.time.LocalDate;
import java.util.List;

/**
 * 土地有机证书入参 BO（PLT-MD-003）。
 *
 * <p>{@code plotIds} 关联地块多选列表，service 层先软删旧关联再插入新关联。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PlotOrganic.class, reverseConvertGenerate = false)
public class PlotOrganicBo extends BaseEntity {

    /** 证书 ID（编辑时必填）。 */
    private Long id;

    /** 证书编号（新增时必填，编辑时后端强制锁回旧值）。 */
    @NotBlank(message = "{plant.organic.no.required}", groups = OnCreate.class)
    @Size(max = 64, message = "{plant.organic.no.size}")
    private String organicNo;

    /** 颁发单位。 */
    @NotBlank(message = "{plant.organic.company.required}")
    @Size(max = 128, message = "{plant.organic.company.size}")
    private String organicCompany;

    /** 证书有效期到期日。 */
    @NotNull(message = "{plant.organic.valid.required}")
    private LocalDate organicValid;

    /** 缩略图 OSS ossId（单张）。 */
    @Size(max = 512, message = "{plant.organic.image.size}")
    private String organicImagePreview;

    /** 原图 OSS ossIds 逗号分隔（多张）。 */
    @Size(max = 2048, message = "{plant.organic.image.size}")
    private String organicImageUrl;

    /** 关联地块 ID 列表（多对多，前端 el-transfer 选）。 */
    private List<Long> plotIds;

    public interface OnCreate {
    }
}
