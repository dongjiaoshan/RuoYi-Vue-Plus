package org.dromara.djs.warehouse.veg.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 饲料饲喂明细 VO（库存查询详情「饲料饲喂记录」tab，行57）。
 *
 * <p>仅当库存行产品是【果蔬且原材料】时该 tab 才显示。{@code locationName} 由 @Select JOIN
 * {@code t_warehouse_location_info} 回填；{@code operatorName} 走 ruoyi {@code USER_ID_TO_NICKNAME} 翻译。</p>
 *
 * @author djs
 * @since WMS-VEG-FEED-LOG-001
 */
@Data
public class FeedLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 饲喂日期。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date feedDate;

    private Long productId;

    /**
     * 饲喂位置（库位 FK）。
     */
    private Long locationId;

    /**
     * 饲喂位置名称（@Select JOIN 回填）。
     */
    private String locationName;

    /**
     * 饲喂重量(kg)。
     */
    private BigDecimal feedWeight;

    private Long operatorId;

    /**
     * 操作人姓名（注解翻译 sys_user.nick_name）。
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 备注。
     */
    private String remark;
}
