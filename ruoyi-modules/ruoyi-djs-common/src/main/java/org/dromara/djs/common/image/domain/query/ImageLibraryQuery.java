package org.dromara.djs.common.image.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 公共图片库列表查询入参（IMG-LIB-001）。
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageLibraryQuery extends BaseEntity {

    /**
     * 主名（模糊匹配）。
     */
    private String imageName;

    /**
     * 别名（模糊匹配）。
     */
    private String aliases;

    /**
     * 状态（字典 sys_normal_disable 精确匹配）。
     */
    private String status;

}
