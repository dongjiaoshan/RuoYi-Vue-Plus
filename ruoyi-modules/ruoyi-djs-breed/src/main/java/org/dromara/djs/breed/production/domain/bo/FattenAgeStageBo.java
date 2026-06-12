package org.dromara.djs.breed.production.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.production.domain.FattenAgeStage;

/**
 * 育肥日龄阶段配置入参 BO（A1 Tab2）。
 *
 * <p>批量保存的单行单元；{@code id} 仅用于前端 row-key，batchSave 不依赖。</p>
 *
 * @author djs
 * @since A1
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = FattenAgeStage.class, reverseConvertGenerate = false)
public class FattenAgeStageBo extends BaseEntity {

    /**
     * 主键（前端 row-key，可空）。
     */
    private Long id;

    /**
     * 起始日龄（天，含）。
     */
    @NotNull(message = "起始日龄不能为空")
    @Min(value = 0, message = "起始日龄不能为负")
    private Integer startAge;

    /**
     * 截止日龄（天，含）。
     */
    @NotNull(message = "截止日龄不能为空")
    @Min(value = 0, message = "截止日龄不能为负")
    private Integer endAge;

    /**
     * 是否记录生长记录（1 记录 / 0 不记录，默认 0）。
     */
    @Min(value = 0, message = "是否记录生长记录取值非法")
    private Integer recordGrowth;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
