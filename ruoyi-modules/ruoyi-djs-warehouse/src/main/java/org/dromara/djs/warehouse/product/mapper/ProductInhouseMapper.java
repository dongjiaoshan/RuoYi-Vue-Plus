package org.dromara.djs.warehouse.product.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;

import java.math.BigDecimal;

/**
 * 过程产品 Mapper（doc/11 §2.7 / WMS-PIG-002 + WMS-VEG-001 共用）。
 *
 * <p>D9 closing Group B 已从 {@code cut/mapper} 挪到 {@code product/mapper}（与 ProductInfoMapper 同域）。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
public interface ProductInhouseMapper extends BaseMapperPlus<ProductInhouse, ProductInhouse> {

    /**
     * 打包按实重部分扣减来源 inhouse 的待打包重量（行锁原子）：
     * {@code product_weight >= qty} 才扣，affected=0 表示余量不足/并发抢占（调用方回滚）。
     *
     * <p>配合 {@code consumeInhouse} 的部分消耗模型：一行领用 WIP 可分多次打包，
     * 扣到 ≤0 由调用方软删；让打包卡「原材料库存 = Σ 活动 inhouse」随打包正确递减。</p>
     *
     * @param id  来源 inhouse 主键
     * @param qty 本次打包实重（kg）
     * @return 影响行数（1=成功，0=余量不足/已被占用）
     */
    @Update("UPDATE t_warehouse_product_inhouse SET product_weight = product_weight - #{qty} "
        + "WHERE id = #{id} AND del_flag = '0' AND product_weight >= #{qty}")
    int deductWeightById(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /**
     * 邓博 row44 + A5：某产品「今日待打包余额」= SUM(product_weight) of 今日 product_inhouse（未消耗，全部人）。
     *
     * <p>该余额 = 今日领用 − 生产耗用 − 已退 − 已损（领用产 inhouse；打包 consumeInhouse / 退回 / 损耗都减
     * inhouse），故 = 可打包食品原料当前还能退/损的最大量 —— 自动净掉「产品生产耗用」（row44 核心：原 cap
     * 漏减生产耗用导致可超额），免重复计。仅今日（DATE(produce_date)=CURDATE()），与 today 额度语义一致。</p>
     */
    @Select("SELECT COALESCE(SUM(product_weight), 0) FROM t_warehouse_product_inhouse "
        + " WHERE product_id = #{productId} AND DATE(produce_date) = CURDATE() AND del_flag = '0'")
    BigDecimal sumTodayRemaining(@Param("productId") Long productId);

    /**
     * 某产品「今日某地块待打包余额」= SUM(product_weight) of 今日该 (product_id, plot_id) product_inhouse。
     *
     * <p>{@link #sumTodayRemaining} 的地块限定变体（自产果蔬按地块领用时 product_inhouse.plot_id 已写入）：
     * = 该地块今日领用 − 生产耗用 − 已退 − 已损，即该地块当前还能退/损的最大量。同一作物多地块共用同一
     * product_id，产品级 {@link #sumTodayRemaining} 会跨地块混算（A 地块领了 B 地块也能退），退回/损耗地块卡
     * 路径（returnVegPlot/lossVegPlot）须按地块校验，无该地块 inhouse 行 → 返 0 → 拒绝退回。</p>
     */
    @Select("SELECT COALESCE(SUM(product_weight), 0) FROM t_warehouse_product_inhouse "
        + " WHERE product_id = #{productId} AND plot_id = #{plotId} "
        + "   AND DATE(produce_date) = CURDATE() AND del_flag = '0'")
    BigDecimal sumTodayRemainingByPlot(@Param("productId") Long productId, @Param("plotId") Long plotId);
}
