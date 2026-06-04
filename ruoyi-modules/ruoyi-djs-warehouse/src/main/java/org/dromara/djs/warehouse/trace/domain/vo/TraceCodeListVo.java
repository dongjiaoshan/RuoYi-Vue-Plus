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
 * {@code creatorName} 走 {@code USER_ID_TO_NAME} 翻译。</p>
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

    @ExcelProperty(value = "类型")
    private String codeType;

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
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "生成时间")
    private Date createTime;

}
