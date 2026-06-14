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
 * 操作人翻译走 {@code TransConstant.USER_ID_TO_NICKNAME}（显 sys_user.nick_name 中文名，参
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

    /**
     * 有该产品需求的门店数（去重，非取消单）。
     *
     * <p>列表页「门店」列显此数字而非原始门店编码（D-FIX-24 决策 #8）；service 层按 product_id
     * 聚合后回填，前端分页下不可靠故不在前端 reduce。</p>
     */
    @ExcelProperty(value = "需求门店数")
    private Integer storeCount;

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

    /**
     * 门店视角派生状态（字典 {@code djs_store_demand_status} 5 态，0613-04）。
     *
     * <p>仓库 {@code demand_status}（7 态）映射到门店视角：
     * {@code SUBMITTED→待确认 / CONFIRMED→已确认 / PARTIAL_SHIPPED|COMPLETED→已发货 /
     * 已确认且 received_time!=null→确认到店(ARRIVED) / DELETED→已删除}。
     * 仓库列表不展示本字段（仍用 {@code demandStatus}），门店列表用本字段显门店语义状态。
     * service 层 {@code queryPageList} 按行计算回填，非 entity 列。</p>
     */
    private String storeDemandStatus;

    @ExcelProperty(value = "确认人ID")
    private Long demandConfirmer;

    /** 确认人姓名（{@code sys_user.user_name} 翻译）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "demandConfirmer")
    private String demandConfirmerName;

    @ExcelProperty(value = "确认时间")
    private LocalDateTime confirmerTime;

    @ExcelProperty(value = "需求类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_demand_mailing_type")
    private String demandType;

    @ExcelProperty(value = "预计到店重量")
    private BigDecimal expectedWeight;

    /** 门店收货确认时间（门店侧「确认收货」）。 */
    @ExcelProperty(value = "收货时间")
    private LocalDateTime receivedTime;

    /** 门店收货确认人 user_id。 */
    private Long receivedBy;

    /** 门店收货确认人姓名（{@code sys_user.nick_name} 翻译）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "receivedBy")
    private String receivedByName;

    @ExcelProperty(value = "期望到货日")
    private LocalDate expectedArriveDate;

    @ExcelProperty(value = "已发货")
    private BigDecimal shippedCount;

    @ExcelProperty(value = "已确认")
    private BigDecimal confirmedCount;

    /** 状态历史 JSON 串（admin 详情 timeline 弹窗解析渲染）。 */
    private String auditHistory;

    /**
     * 是否已指定猪只（0613-11 需求确认页「是否指定猪只」列）。
     *
     * <p>白条 / 猪业态行才有意义；仅当 {@code queryPageList} 入参带 {@code productId}（确认页下钻场景）
     * 时按页 demand id 批量回填，主列表查询不回填（保持 null）。{@code true} = 该需求至少指定 1 头未删猪只。</p>
     */
    private Boolean pigAssigned;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /** 创建人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射取数翻译成 createByName。 */
    private Long createBy;

    /** 创建人姓名。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

    private String remark;
}
