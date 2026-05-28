package org.dromara.djs.warehouse.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.flow.domain.StockFlow;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 出入库流水 VO（WMS-MAT-001）。
 *
 * <p>覆盖 admin 流水查询页 + mp 流水回显两端用：</p>
 * <ul>
 *   <li>{@code operatorName} 走 ruoyi {@code USER_ID_TO_NAME} 翻译</li>
 *   <li>{@code productName} / {@code locationName} service 层 JOIN 回填（避免 N+1，参 LocationStockServiceImpl.fillLocationNames 模式）</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = StockFlow.class)
public class StockFlowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "流水号")
    private String flowNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "业务时间")
    private Date flowDate;

    private Long productId;

    /**
     * 产品名（service 层 JOIN 回填）。
     */
    @ExcelProperty(value = "产品")
    private String productName;

    /**
     * 产品业务码 PROD-xxx（service JOIN 回填）。
     */
    @ExcelProperty(value = "产品码")
    private String productCode;

    /**
     * 产品归属 djs_belong_type（service JOIN 回填，便于 admin 流水页按 matType 过滤）。
     */
    @ExcelProperty(value = "归属")
    private String belongType;

    /**
     * 单位（service JOIN 回填）。
     */
    @ExcelProperty(value = "单位")
    private String productUnit;

    /**
     * 库位 ID（DDL 列名 warehouse_id，实为 location FK；详 {@link StockFlow#warehouseId}）。
     */
    private Long warehouseId;

    /**
     * 库位名（service 回填）。
     */
    @ExcelProperty(value = "库位")
    private String locationName;

    /**
     * 关联需求单 ID（仅 {@code flow_type=ship_out} 写入；D14 CROSS-FLOW-003 聚合 shipped_count）。
     */
    private Long demandId;

    @ExcelProperty(value = "出入")
    private String inoutType;

    @ExcelProperty(value = "类型")
    private String flowType;

    private String stockInType;

    private String stockOutType;

    @ExcelProperty(value = "出库去向")
    private String stockOutDest;

    @ExcelProperty(value = "变动数量(±)")
    private BigDecimal changeNum;

    @ExcelProperty(value = "变动绝对值")
    private BigDecimal changeQuantity;

    private Long supplierId;

    @ExcelProperty(value = "耳号")
    private String earNo;

    private Long plotId;

    private Long operatorId;

    /**
     * 操作人姓名（注解翻译）。
     */
    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "operatorId")
    private String operatorName;

    @ExcelProperty(value = "备注")
    private String remark;

    @ExcelProperty(value = "凭证图IDs")
    private String proofOssIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
