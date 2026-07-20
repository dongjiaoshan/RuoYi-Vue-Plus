package org.dromara.djs.warehouse.trace.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 追溯码管理列表行 VO（TRC-ADMIN-001，admin only）。
 *
 * <p>主表 {@code t_warehouse_trace_code} 字段 + service 内存 JOIN 出的展示名
 * （{@code productName / storeName / farmName / plotName}，主表只存 FK，名字非冗余列）。
 * {@code codeType} 前端用字典 {@code djs_trace_code_type} 渲染 dict-tag（值 pork/veg/gift）；
 * {@code creatorName} 走 {@code USER_ID_TO_NICKNAME} 翻译。</p>
 *
 * <p>不复用 TRC-CORE 的 {@code TraceCodeVo}（那是纯主表 VO 无 JOIN 展示名），admin 列表另立本 VO。</p>
 *
 * @author djs
 * @since TRC-ADMIN-001
 */
@Data
@ExcelIgnoreUnannotated
public class TraceCodeListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "追溯码")
    private String produceCode;

    /**
     * 生产编码（DENGBO-ROW84：门店现场打包持久化 {@code <生产标识码>YYMMDD####}；仓库码 / 旧门店码为空）。
     *
     * <p>补打（重打印）列表「生产编号」列优先读本列（门店现做码无产出记录、{@code produceNo} 反查为空）。</p>
     */
    @ExcelProperty(value = "生产编码")
    private String productionCode;

    @ExcelProperty(value = "类型")
    private String codeType;

    /**
     * 生成来源：{@code warehouse}=仓库 / {@code store}=门店现场分割打包（service 按 remark「现场生码」前缀计算）。
     */
    @ExcelProperty(value = "生成来源")
    private String source;

    private Long productId;

    /**
     * 产品名（service JOIN {@code t_warehouse_product_info} 取，主表非冗余列）。
     */
    @ExcelProperty(value = "产品")
    private String productName;

    /**
     * 产品规格（service JOIN product 取，供码样式页展示重量 / 规格）。
     */
    @ExcelProperty(value = "规格")
    private String productSpec;

    /**
     * 产品图 OSS ID（service JOIN product 取，详情页 ImagePreview 用；主表无产品图列）。
     */
    private String productImg;

    @ExcelProperty(value = "猪只耳号")
    private String pigEarNo;

    private Long plotId;

    /**
     * 地块名（service JOIN {@code t_plant_plot_info} 取）。
     */
    @ExcelProperty(value = "地块")
    private String plotName;

    @ExcelProperty(value = "种植天数")
    private Integer plantDays;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "采收日期")
    private LocalDate harvestDate;

    private Long cropCertId;

    private Long plotCertId;

    private Long storeId;

    /**
     * 门店名（service JOIN {@code t_md_store} 取，门店非字典不能 dict-tag）。
     */
    @ExcelProperty(value = "门店")
    private String storeName;

    private Long farmId;

    /**
     * 农场名（service JOIN {@code sys_farm} 取）。
     */
    @ExcelProperty(value = "农场")
    private String farmName;

    private Long qrOssId;

    @ExcelProperty(value = "备注")
    private String remark;

    private Long createBy;

    /**
     * 生成人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "生成人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "生成时间")
    private Date createTime;

    // ============== 果蔬追溯码管理列对齐原型补充字段（STORE-TRACE-ONSITE-001） ==============
    // 原型「果蔬追溯码管理」列：到店日期/生产编号/序号/产品名称/产品规格/实际重量/来源地块/采摘时间/月台接收时间/发货时间。
    // 主表无这些列，service 按 produce_code JOIN t_warehouse_trace_event 时间轴 +
    // t_warehouse_product_production 产出记录聚合填。确无数据源的字段留空（不造假），openIssue 标注。

    /**
     * 到店日期（trace_event 中 {@code trace_content='arrival'} 事件时间；无 arrival 事件则空）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "到店日期")
    private LocalDate arrivalDate;

    /**
     * 生产编号（JOIN {@code t_warehouse_product_production.produce_no}，按 produce_code 反查产出记录）。
     */
    @ExcelProperty(value = "生产编号")
    private String produceNo;

    /**
     * 序号（生产编号末段流水号，从 {@code produce_no} 尾部数字截取；无产出记录则空）。
     */
    @ExcelProperty(value = "序号")
    private Integer serialNo;

    /**
     * 实际重量 kg（JOIN 产出记录 {@code produce_quantity}；无产出记录则空）。
     */
    @ExcelProperty(value = "实际重量")
    private java.math.BigDecimal actualWeight;

    /**
     * 采摘时间（JOIN 产出记录 {@code produce_date}；trace_code.havest_date 仅日期无时刻，产出记录时刻优先）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "采摘时间")
    private Date pickTime;

    /**
     * 月台接收时间（trace_event 中 {@code trace_content='in_stock'} 事件时间；无则空）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "月台接收时间")
    private Date platformReceiveTime;

    /**
     * 发货时间（trace_event 中 {@code trace_content='ship'} 事件时间；无则空）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "发货时间")
    private Date shipTime;

}
