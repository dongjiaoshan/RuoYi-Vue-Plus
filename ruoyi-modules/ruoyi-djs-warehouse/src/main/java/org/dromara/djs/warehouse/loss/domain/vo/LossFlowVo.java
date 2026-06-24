package org.dromara.djs.warehouse.loss.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 统一损耗流水 VO（库存查询详情「损耗记录」tab，行57）。
 *
 * <p>{@code operatorName} 走 ruoyi {@code USER_ID_TO_NICKNAME} 翻译（参 {@code StockFlowVo.operatorName}）。
 * 系统自动算的损耗（如生产损耗）operator_id 可空 → operatorName 为空。</p>
 *
 * @author djs
 * @since WMS-LOSS-001
 */
@Data
public class LossFlowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 损耗发生时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lossDate;

    private Long productId;

    /**
     * 产品编码（冗余快照）。
     */
    private String productCode;

    /**
     * 产品名称（冗余快照）。
     */
    private String productName;

    /**
     * 单位（冗余快照）。
     */
    private String productUnit;

    /**
     * 损耗类型（字典 {@code djs_loss_type}）。
     */
    private String lossType;

    /**
     * 损耗量。
     */
    private BigDecimal lossWeight;

    private Long locationId;

    /**
     * 损耗位置名称（冗余快照）。
     */
    private String locationName;

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
