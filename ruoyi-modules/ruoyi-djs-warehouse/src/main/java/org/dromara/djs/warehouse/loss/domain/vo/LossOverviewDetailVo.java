package org.dromara.djs.warehouse.loss.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 损耗总览当日明细视图对象（WMS-LOSS-OVERVIEW-001，仓库-admin 行63）。
 *
 * <p>某自然日下钻得到的逐条损耗明细（弹窗 el-table）。数据源
 * {@code t_warehouse_loss_flow} LEFT JOIN {@code t_warehouse_product_info}（取产品图）。
 * 雪花 id 列 CAST AS CHAR 以 String 返回前端，防 JS 精度丢失。</p>
 *
 * @author djs
 * @since WMS-LOSS-OVERVIEW-001
 */
@Data
public class LossOverviewDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 损耗记录 id（String 防精度丢失）。 */
    private String id;

    /** 记录时间（loss_date）。 */
    private Date lossDate;

    /** 产品图片 oss_id（COALESCE(product_thumb, image_oss_id)；可空，前端占位兜底）。 */
    private String imageOssId;

    /** 产品编码（业务码冗余快照）。 */
    private String productCode;

    /** 产品名称（冗余快照）。 */
    private String productName;

    /** 单位（冗余快照）。 */
    private String productUnit;

    /** 损耗类型（字典 {@code djs_loss_type}，前端 dict-tag 渲染）。 */
    private String lossType;

    /** 损耗量。 */
    private BigDecimal lossWeight;
}
