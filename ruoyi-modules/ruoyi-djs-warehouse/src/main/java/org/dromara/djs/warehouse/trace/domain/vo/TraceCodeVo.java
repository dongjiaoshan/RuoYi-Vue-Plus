package org.dromara.djs.warehouse.trace.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.trace.domain.TraceCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 追溯码主表 VO（TRC-CORE-001），供下游 TRC-ADMIN-001（D14）追溯码管理列表 / 详情消费。
 *
 * <p>{@code codeType} admin 端用字典 {@code djs_trace_code_type} 渲染 dict-tag；
 * {@code creatorName} 走 {@code USER_ID_TO_NAME} 翻译；产品名 / 门店名由 admin 端 JOIN 取
 * （本表不冗余存名）。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = TraceCode.class)
public class TraceCodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "追溯码")
    private String produceCode;

    @ExcelProperty(value = "类型")
    private String codeType;

    private Long productId;

    @ExcelProperty(value = "猪只耳号")
    private String pigEarNo;

    private Long plotId;

    @ExcelProperty(value = "种植天数")
    private Integer plantDays;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ExcelProperty(value = "采收日期")
    private LocalDate harvestDate;

    private Long cropCertId;

    private Long plotCertId;

    private Long storeId;

    private Long farmId;

    /**
     * 礼盒子码 JSON（V1 NULL）。
     */
    private String giftComponents;

    /**
     * 二维码图 OSS ID（V1 NULL）。
     */
    private Long qrOssId;

    @ExcelProperty(value = "备注")
    private String remark;

    private Long createBy;

    /**
     * 创建人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "生成人")
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "生成时间")
    private Date createTime;

}
