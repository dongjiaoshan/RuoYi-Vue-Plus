package org.dromara.djs.common.image.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.common.image.domain.DefaultImage;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分类默认图视图对象（IMG-LIB-001）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
@AutoMapper(target = DefaultImage.class)
public class DefaultImageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 分类键（belong_type 值 / vegetable / global）。
     */
    private String categoryKey;

    /**
     * 默认图 ossId（字符串）。
     */
    private String ossId;

    /**
     * 默认图 public URL（resolver 回填，前端预览用）。
     */
    private String imageUrl;

    /**
     * 是否全局兜底（1 是 / 0 否）。
     */
    private Integer isGlobal;

}
