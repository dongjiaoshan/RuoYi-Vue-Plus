package org.dromara.djs.breed.farm.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.farm.domain.Barn;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 栋舍视图对象（BRD-MD-002）。
 *
 * @author djs
 * @since BRD-MD-002
 */
@Data
@AutoMapper(target = Barn.class)
public class BarnVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 栋舍 ID。
     */
    private Long id;

    /**
     * 栋舍编码。
     */
    private String barnCode;

    /**
     * 栋舍名称。
     */
    private String barnName;

    /**
     * 栋舍类型（字典 {@code djs_barn_type}）。
     */
    private String barnType;

    /**
     * 容量。
     */
    private Integer capacity;

    /**
     * 当前存栏。
     */
    private Integer currentCount;

    /**
     * 状态（1=启用 / 0=停用）。
     */
    private Integer barnStatus;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private Date createTime;

}
