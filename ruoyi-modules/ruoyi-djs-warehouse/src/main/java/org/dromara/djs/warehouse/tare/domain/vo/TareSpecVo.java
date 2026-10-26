package org.dromara.djs.warehouse.tare.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.tare.domain.TareSpec;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 去皮规格列表 / 详情 VO。{@code positions} 原样回 CSV，前端按字典 {@code djs_tare_position} 渲染。
 *
 * @author djs
 * @since V7-R2
 */
@Data
@AutoMapper(target = TareSpec.class)
public class TareSpecVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String specName;

    private BigDecimal tareWeight;

    private Integer isDefault;

    private String positions;

    private Long createBy;

    /** 创建人昵称。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

    private Date createTime;

    private Date updateTime;

}
