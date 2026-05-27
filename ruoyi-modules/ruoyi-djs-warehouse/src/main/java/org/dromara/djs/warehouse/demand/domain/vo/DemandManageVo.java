package org.dromara.djs.warehouse.demand.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.demand.domain.DemandManage;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 需求管理 VO（WMS-DEMAND-001）。
 *
 * <p>字典翻译走 admin 端 {@code <dict-tag>} 自渲染（ADR-0004 §2.3 范式）；
 * 操作人翻译走 {@code TransConstant.USER_ID_TO_NAME}（不是 USER_ID_TO_NICKNAME，参
 * .claude/skills/coder-djs-cross-layer-contract.md §契约 4.5）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = DemandManage.class)
public class DemandManageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "需求单号")
    private String demandNo;

    @ExcelProperty(value = "需求日期")
    private LocalDate demandDate;

    @ExcelProperty(value = "门店ID")
    private Long storeId;

    @ExcelProperty(value = "产品ID")
    private Long productId;

    @ExcelProperty(value = "产品名称")
    private String productName;

    @ExcelProperty(value = "产品规格")
    private String productSpec;

    @ExcelProperty(value = "业态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_demand_product_type")
    private String productType;

    @ExcelProperty(value = "需求量")
    private BigDecimal demandQuantity;

    @ExcelProperty(value = "单位")
    private String productUnit;

    @ExcelProperty(value = "原材料")
    private String rawMaterial;

    @ExcelProperty(value = "原料计算量")
    private BigDecimal materialQty;

    @ExcelProperty(value = "需求备注")
    private String demandRemark;

    @ExcelProperty(value = "需求说明")
    private String demandExplain;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_demand_status")
    private String demandStatus;

    @ExcelProperty(value = "确认人ID")
    private Long demandConfirmer;

    /** 确认人姓名（{@code sys_user.user_name} 翻译）。 */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "demandConfirmer")
    private String demandConfirmerName;

    @ExcelProperty(value = "确认时间")
    private LocalDateTime confirmerTime;

    @ExcelProperty(value = "期望到货日")
    private LocalDate expectedArriveDate;

    @ExcelProperty(value = "已发货")
    private BigDecimal shippedCount;

    @ExcelProperty(value = "已确认")
    private BigDecimal confirmedCount;

    /** 状态历史 JSON 串（admin 详情 timeline 弹窗解析渲染）。 */
    private String auditHistory;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /** 创建人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射取数翻译成 createByName。 */
    private Long createBy;

    /** 创建人姓名。 */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    private String remark;
}
