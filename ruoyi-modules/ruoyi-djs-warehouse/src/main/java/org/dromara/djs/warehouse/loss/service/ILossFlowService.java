package org.dromara.djs.warehouse.loss.service;

import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.domain.vo.LossFlowVo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 统一损耗流水台账 Service（WMS-LOSS-001，行58）。
 *
 * <p>所有「会产生损耗」的业务环节统一调本门面双写一条损耗明细，集中生成快照 + 落库，
 * 避免每个 service 各写各的。损耗类型见字典 {@code djs_loss_type}：
 * veg_handle_loss / transport_loss / manual_loss / production_loss / check_loss / precool_loss / cut_loss。</p>
 *
 * @author djs
 * @since WMS-LOSS-001
 */
public interface ILossFlowService {

    /**
     * 记录一条损耗明细（通用）。
     *
     * <p>调用方按已知信息构造 {@link LossFlow}（至少 {@code lossType} + {@code lossWeight}），
     * 本方法负责：① 缺省 {@code lossDate=now}；② 若 {@code productId} 非空则回填
     * 产品编码/名称/单位/业态快照；③ 若 {@code locationId} 非空则回填库位名称；④ insert。
     * {@code lossWeight <= 0} 时跳过（不记零/负损耗），返回 {@code null}。</p>
     *
     * @param entity 损耗明细（部分字段，由本方法补全快照）
     * @return 新损耗记录 id；跳过时 {@code null}
     */
    Long record(LossFlow entity);

    /**
     * 记录一条损耗明细（常用便捷重载：领用/盘点等出库型损耗，关联一条 stock_flow）。
     *
     * @param lossType      损耗类型（字典 {@code djs_loss_type}）
     * @param productId     产品 ID（FK → product_info.id）
     * @param lossWeight    损耗量
     * @param locationId    损耗位置库位 ID（可空）
     * @param operatorId    操作人（系统自动算的损耗传 {@code null}）
     * @param sourceBizType 来源业务标识
     * @param sourceBizId   来源记录 ID（可空）
     * @param sourceFlowId  关联 stock_flow.id（可空）
     * @return 新损耗记录 id；跳过时 {@code null}
     */
    Long record(String lossType, Long productId, BigDecimal lossWeight, Long locationId,
                Long operatorId, String sourceBizType, Long sourceBizId, Long sourceFlowId);

    /**
     * 按产品取损耗明细（库存查询详情「损耗记录」tab，行57）。
     *
     * @param productId 产品 ID（FK → product_info.id）
     * @param dateFrom  起始时间（含，可空）
     * @param dateTo    截止时间（含，可空）
     * @return 该产品损耗明细（按损耗时间倒序）
     */
    List<LossFlowVo> listByProduct(Long productId, Date dateFrom, Date dateTo);

}
