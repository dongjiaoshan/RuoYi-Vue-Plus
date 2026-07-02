package org.dromara.djs.warehouse.trace.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 追溯码主表实体（TRC-CORE-001）。
 *
 * <p>对应表 {@code t_warehouse_trace_code}（SYS-INIT-001 warehouse DDL 已建，本 ticket 不改表）。
 * 一个产品成品 = 一条追溯码记录，{@code produce_code} 由 {@link org.dromara.djs.common.encoder.BizCodeType#TRACE_CODE}
 * 规则生成（{@code T{yyyyMMdd}{productCode2}{seq6}}，终生递增）。</p>
 *
 * <h3>三业态链路字段（按 code_type 分支填，互斥为 NULL）</h3>
 * <ul>
 *   <li>{@code code_type='pork'}（猪肉链）：填 {@code pigEarNo}，地块/认证链字段 NULL</li>
 *   <li>{@code code_type='veg'}（果蔬链）：填 {@code plotId / plantDays / harvestDate / cropCertId / plotCertId}，
 *       {@code pigEarNo} NULL</li>
 *   <li>{@code code_type='gift'}（礼盒）：{@code giftComponents} JSON 子码 V1 写 NULL（V3 启用）</li>
 * </ul>
 *
 * <p>表无 {@code product_name / product_weight / store_address} 冗余列，仅存 {@code product_id / store_id}
 * （产品名 / 门店地址 D14 admin 端 JOIN 取，genCode 不冗余存）。</p>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}（{@code DjsMetaObjectHandler} 在软删时把 id 写入
 * {@code del_unique}，保证 UNIQUE(tenant_id, produce_code, del_unique) 不阻塞业务码复用），服务层走
 * {@link org.dromara.djs.common.base.DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_trace_code")
public class TraceCode extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 追溯业务码（UNIQUE(tenant_id, produce_code, del_unique)）。
     */
    private String produceCode;

    /**
     * 追溯码类型（字典 {@code djs_trace_code_type}，VARCHAR(16) 值 pork / veg / gift）。
     */
    private String codeType;

    /**
     * 产品 FK → {@code t_warehouse_product_info.id}。
     */
    private Long productId;

    /**
     * 生码时定格的展示名（DENGBO-R16）。
     *
     * <p>果蔬按「原材料对应作物是否有有效有机证书」取产品名 / 别名，其余业态直接产品名。快照语义：
     * 证书事后过期 / 产品事后改名，历史追溯码显示不变。显示层优先读本列，NULL（旧码）时兜底 JOIN product 当前名。</p>
     */
    private String traceDisplayName;

    /**
     * 猪肉链：耳号（果蔬 / 礼盒为 NULL）。
     */
    private String pigEarNo;

    /**
     * 果蔬链：来源地块 FK（猪肉 / 礼盒为 NULL）。
     */
    private Long plotId;

    /**
     * 果蔬链：种植天数。
     */
    private Integer plantDays;

    /**
     * 果蔬链：采收日期。
     *
     * <p>DB 列名 {@code havest_date} 保留 typo（不 ALTER），Java 侧 {@code @TableField} 修正为
     * {@code harvestDate}。</p>
     */
    @TableField("havest_date")
    private LocalDate harvestDate;

    /**
     * 果蔬链：作物有机认证 ID。
     */
    private Long cropCertId;

    /**
     * 果蔬链：地块有机认证 ID。
     */
    private Long plotCertId;

    /**
     * 门店 FK（表无 store_address 列，门店地址 JOIN 取）。
     */
    private Long storeId;

    /**
     * 农场 ID。
     */
    private Long farmId;

    /**
     * 礼盒子码 JSON（V1 写 NULL 预留，V3 启用）。
     */
    private String giftComponents;

    /**
     * 二维码图 OSS ID（V1 写 NULL，TRC-WATERMARK / 顾客追溯页 V3 生成）。
     */
    private Long qrOssId;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列：未删时 0；软删时由 {@code DjsMetaObjectHandler} 写入 id，
     * 保证 UNIQUE(tenant_id, produce_code, del_unique) 不阻塞重新启用同业务码。
     */
    private Long delUnique;

}
