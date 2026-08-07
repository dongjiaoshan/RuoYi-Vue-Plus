package org.dromara.djs.warehouse.vegreceive.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 自产果蔬入库提交入参（FIX-WMS-VEGRECEIVE-001，mp {@code submitInbound}）。
 *
 * <p>对齐 mp 契约 {@code miniapp/src/api/warehouse/vegReceive.ts#VegInboundBody}：
 * {@code cropId / plotId / weight / isFinish? / locationId}（+ {@code operatorId?} 入库人）。</p>
 *
 * <h3>Service 同事务</h3>
 * <ol>
 *   <li>校验该 (crop, product, plot) 的月台待入库量 ≥ 本次 weight（row55 起数据源是
 *       {@code t_warehouse_handle_record} 的月台明细，减已入库 self 量与已结算损耗；超量抛异常，不允许"凭空入库"）</li>
 *   <li>INSERT {@code t_warehouse_veg_receive}（receiveType=1，落 product_id）</li>
 *   <li>UPSERT {@code location_stock}（按 <b>product + plot</b> 双键行锁增量 / 不存在 INSERT）</li>
 *   <li>INSERT {@code stock_flow}（flow_type=veg_receive_in, inout_type=IN, plotId 关联）</li>
 * </ol>
 *
 * <h3>跨层契约（cross-layer-contract §1）</h3>
 * <p>{@code cropId / plotId / locationId / operatorId} 均为 snowflake，Jackson 反序列化 String → Long
 * （后端无精度问题）；mp 端发 String 不做 Number()。</p>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
@Data
public class VegInboundBo {

    /**
     * 作物 ID（FK → t_plant_crop_info.id）。
     */
    @NotNull(message = "{vegReceive.crop_id.required}")
    private Long cropId;

    /**
     * 地块 ID（FK → t_plant_plot_info.id；自产按地块入库）。
     */
    @NotNull(message = "{vegReceive.plot_id.required}")
    private Long plotId;

    /**
     * 产品 ID（row55；FK → {@code t_warehouse_product_info.id}）。
     *
     * <p>月台改按产品聚合后，一个 (作物, 地块) 下可以有多个产品各自的待入库量（红薯 / 红薯杆），
     * 封顶校验与收货记录都必须收窄到具体产品，否则红薯的入库会吃掉红薯杆的额度。</p>
     *
     * <p>不做 {@code @NotNull}，但<b>写入侧一律收窄到一个确定产品</b>：传空时——作物只有一个产品就自动补上；
     * <b>作物是多产品就直接拒绝</b>（提示更新小程序），因为「收的是哪个产品」说不清的话账一定会错。
     * 传了则校验它确实属于该作物的产品配置。详见 {@code VegReceiveServiceImpl.resolveReceiveProductId}。</p>
     */
    private Long productId;

    /**
     * 入库重量(kg)（必填，&gt; 0）。
     */
    @NotNull(message = "{vegReceive.weight.required}")
    @DecimalMin(value = "0.001", message = "{vegReceive.weight.positive}")
    private BigDecimal weight;

    /**
     * 是否入库完成 1=是（mp「是否入库完成」下拉选「是」时传）；其余 / null 视作未完成（2）。
     */
    private Integer isFinish;

    /**
     * 入库库位 ID（FK → t_warehouse_location_info.id；保鲜室）。
     */
    @NotNull(message = "{vegReceive.location_id.required}")
    private Long locationId;

    /**
     * 入库人 userId（FK → sys_user.user_id；可选，mp 入库人下拉，缺省时取当前登录用户）。
     */
    private Long operatorId;

}
