package org.dromara.djs.warehouse.trace.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.trace.domain.TraceEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 追溯事件流水 VO（TRC-CORE-001），供下游 TRC-ADMIN-001（D14）事件链查询消费。
 *
 * <p>{@code traceContent} admin 端用字典 {@code djs_trace_content} 渲染 dict-tag；
 * {@code operatorName} 走 {@code USER_ID_TO_NICKNAME} 翻译。immutable 流水无更新人 / 备注。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = TraceEvent.class)
public class TraceEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "记录ID")
    private Long id;

    @ExcelProperty(value = "追溯码")
    private String produceCode;

    @ExcelProperty(value = "事件类型")
    private String traceContent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "事件时间")
    private LocalDateTime traceTime;

    /**
     * 事件上下文 JSON（V1 多 NULL）。
     */
    private String eventData;

    private Long operatorId;

    /**
     * 操作人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "操作人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "记录时间")
    private LocalDateTime createTime;

}
