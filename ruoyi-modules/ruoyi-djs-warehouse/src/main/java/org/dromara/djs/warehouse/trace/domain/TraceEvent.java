package org.dromara.djs.warehouse.trace.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 追溯事件流水实体（TRC-CORE-001），immutable append-only。
 *
 * <p>对应表 {@code t_warehouse_trace_event}（SYS-INIT-001 warehouse DDL 已建，本 ticket 不改表）。
 * 一个工序完成 = 一条事件流水，追溯链不可篡改：表**故意只有** {@code create_time} + {@code operator_id}，
 * 无 {@code update_* / del_flag / create_dept / remark}。</p>
 *
 * <h3>不继承 BaseEntity / TenantEntity 的原因</h3>
 * <p>BaseEntity 带 {@code createDept / createBy / updateBy / updateTime}、TenantEntity 多带审计字段，
 * 这些列在 {@code t_warehouse_trace_event} 表里**不存在**；若继承，{@code InjectionMetaObjectHandler}
 * 会尝试 fill 这些字段 → INSERT 生成不存在的列 → {@code Unknown column}。故本实体裸映射，
 * 仅声明表实有的 7 列。</p>
 *
 * <p>{@code createTime} 走 {@code @TableField(fill = INSERT)} 由 handler 的 else 分支
 * {@code strictInsertFill} 自动填当前时间；{@code tenantId} V1 不赋值（DB DEFAULT '1001' 兜底，
 * V2 由多租户 SQL 拦截器注入）。</p>
 *
 * <p>service 层**只 INSERT + query**，绝不 UPDATE / DELETE（immutable）。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
@Data
@TableName("t_warehouse_trace_event")
public class TraceEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 租户编号（V1 DB DEFAULT '1001' 兜底，不显式赋值）。
     */
    private String tenantId;

    /**
     * 追溯码 FK → {@code t_warehouse_trace_code.produce_code}。
     */
    private String produceCode;

    /**
     * 事件类型（字典 {@code djs_trace_content}，VARCHAR(32) 值
     * marketing / singe / slaughter / acid / in_stock / ship / arrival）。
     */
    private String traceContent;

    /**
     * 事件时间（工序完成时刻）。
     */
    private LocalDateTime traceTime;

    /**
     * 事件上下文 JSON（可空，V1 多写 NULL）。
     */
    private String eventData;

    /**
     * 操作人 FK → {@code sys_user.user_id}。
     */
    private Long operatorId;

    /**
     * 创建时间（{@code InjectionMetaObjectHandler} else 分支自动填）。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
