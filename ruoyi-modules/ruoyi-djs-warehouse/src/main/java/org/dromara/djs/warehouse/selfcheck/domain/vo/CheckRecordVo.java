package org.dromara.djs.warehouse.selfcheck.domain.vo;

import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 盘点流水卡 VO（盘点记录 tab）。
 *
 * <p>对应 mp 契约 {@code stockCheck.ts} CheckRecordVo。数据源 {@code t_warehouse_stock_flow}
 * 中 {@code flow_type IN ('check_in','check_out')}（盘点留痕流水）。</p>
 *
 * <ul>
 *   <li>{@code checkResult} 由 flow_type + change_num 推：{@code check_out} → loss；
 *       {@code check_in && change_num=0} → normal；{@code check_in && change_num≠0} → abnormal。</li>
 *   <li>{@code stock} = change_quantity（实盘量）；{@code diffQuantity} = ABS(change_num)。</li>
 *   <li>{@code operatorName} 走 ruoyi {@code USER_ID_TO_NAME} 注解翻译。</li>
 * </ul>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class CheckRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流水 ID（snowflake string）。
     */
    private Long id;

    /**
     * 盘点时间 yyyy-MM-dd HH:mm:ss。
     */
    private String checkTime;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 盘点时库存量（实盘量 = change_quantity）。
     */
    private BigDecimal stock;

    /**
     * 单位。
     */
    private String productUnit;

    /**
     * 盘点结果：normal 正常 / loss 计损 / abnormal 异常。
     */
    private String checkResult;

    /**
     * 结果中文文案（正常 / 计损 / 异常）。
     */
    private String checkResultLabel;

    /**
     * 计损/异常量（= ABS(change_num)）。
     */
    private BigDecimal diffQuantity;

    /**
     * 地块/耳号。
     */
    private String plotOrEarNo;

    /**
     * 备注（计损/异常原因）。
     */
    private String remark;

    /**
     * 操作人 ID（注解翻译用 mapper，前端只用 operatorName）。
     */
    private Long operatorId;

    /**
     * 记录人姓名（注解翻译）。
     */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "operatorId")
    private String operatorName;

}
