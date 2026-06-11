package org.dromara.djs.common.image.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.common.image.domain.DefaultImage;

/**
 * 分类默认图入参 BO（IMG-LIB-001）。
 *
 * <p>只编辑 {@code ossId}（7 行 seed 已建，不新增 / 删除 categoryKey）。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = DefaultImage.class, reverseConvertGenerate = false)
public class DefaultImageBo extends BaseEntity {

    /**
     * 主键（必填）。
     */
    @NotNull(message = "默认图 ID 不能为空")
    private Long id;

    /**
     * 分类键（不允许编辑端点修改，service 拉回旧值）。
     */
    @Size(max = 32, message = "分类键取值非法")
    private String categoryKey;

    /**
     * 默认图 ossId（字符串；清空表示移除默认图）。
     */
    @Size(max = 32, message = "ossId 长度非法")
    private String ossId;

}
