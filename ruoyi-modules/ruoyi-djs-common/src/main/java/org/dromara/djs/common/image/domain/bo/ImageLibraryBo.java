package org.dromara.djs.common.image.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.common.image.domain.ImageLibrary;

/**
 * 公共图片库入参 BO（IMG-LIB-001）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ImageLibrary.class, reverseConvertGenerate = false)
public class ImageLibraryBo extends BaseEntity {

    /**
     * 主键（编辑时必填）。
     */
    private Long id;

    /**
     * 主名。
     */
    @NotBlank(message = "图片主名不能为空")
    @Size(max = 64, message = "图片主名长度不能超过 {max} 个字符")
    private String imageName;

    /**
     * 别名（逗号分隔）。
     */
    @Size(max = 255, message = "别名长度不能超过 {max} 个字符")
    private String aliases;

    /**
     * 单张图 ossId（字符串）。
     */
    @Size(max = 32, message = "ossId 长度非法")
    private String ossId;

    /**
     * 排序。
     */
    private Integer sortOrder;

    /**
     * 状态（字典 sys_normal_disable：0 正常 / 1 停用）。
     */
    @Size(max = 1, message = "状态取值非法")
    private String status;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
