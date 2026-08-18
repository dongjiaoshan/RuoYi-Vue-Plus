package org.dromara.djs.warehouse.stock.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存明细实体（WMS-MD-001）。
 *
 * <p>对应表 {@code t_warehouse_location_stock}（V202605290900 建）：</p>
 * <ul>
 *   <li><b>主维</b>：{@code productId} / {@code earNo} / {@code medicineId} 三选一，标明这行库存记的是什么。</li>
 *   <li><b>分篮维</b>：{@code plotId} / {@code whiteBarNo} / {@code thirdPhase} 与主维<b>并存</b>，
 *       把同一主维的货按来源拆成互不混账的「篮子」。典型：毛菜处理入库一次建一篮
 *       ({@code productId} + {@code plotId} 同时非空，见 {@code VegetableHandleServiceImpl} 的 basket 分支)，
 *       毛菜间出库因此能按「产品 × 地块」逐篮出、把地块带进追溯链。
 *       <b>不要按「四选一互斥」理解</b> —— 那是 WMS-MD-001 初版口径，早已不成立。</li>
 *   <li>只有 {@code plotId} 非空而 {@code productId} 为空的行是另一条链路：果蔬月台自产收货
 *       ({@code VegReceiveServiceImpl.insertPlotStockRow})，按地块建账、按地块领用，与本表其余行口径不同。</li>
 *   <li>{@code operatorId} 由 service insert 时通过 {@link org.dromara.common.satoken.utils.LoginHelper#getUserId()}
 *       注入（ADR-0007 强制；冗余存最后操作人便于追溯，独立于 {@code createBy}）</li>
 *   <li>库存写入入口：本 ticket admin 不暴露 add/edit；后续 WMS-DEMAND-001 / WMS-STOCK-001 D8-D11
 *       通过出入库流水触发更新本表</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_location_stock")
public class LocationStock extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 所在库位（FK → t_warehouse_location_info.id）。
     */
    private Long locationId;

    /**
     * 产品 ID（FK → t_warehouse_product_info.id，WMS-MD-002 D8 建；与 earNo/plotId 三选一）。
     */
    private Long productId;

    /**
     * 猪只耳号（白条入库按耳号关联；与 productId/plotId 三选一）。
     */
    private String earNo;

    /**
     * 白条流水号（半只/整只白条唯一标识，燎毛按白条生成）。
     * 白条库存按 white_bar_no 半只一行（区分同一耳号的两个半只）；耳号空(外购)时按 white_bar_no 区分。
     */
    private String whiteBarNo;

    /**
     * 地块 ID（蔬菜采摘入库按地块关联；与 productId/earNo 三选一）。
     */
    private Long plotId;

    /**
     * 药品维（FK → t_breed_medicine_info.id；ADR-0012 药品归仓库库位统一，
     * 与 productId/earNo/plotId 四选一）。
     */
    private Long medicineId;

    /**
     * 【三期】标识（0=否 / 1=是；V6 row92）。
     *
     * <p>甲方口径：「三期」没有真实地块，只做文案显示 —— 所以它<b>不是</b> plotId 的一个取值，
     * 而是与 plot/ear/white_bar 同级的<b>第四个分篮维度</b>：三期货的 plotId 恒为 NULL，
     * 形状与产品级非篮子行一样，不按本字段分篮就会与普通货并进同一行、混账。
     * 因此 {@code addByProductLocation} 系列 product 维度 UPDATE 一律带
     * {@code third_phase} 条件（见 {@code LocationStockMapper}）。</p>
     *
     * <p>展示：本字段为 1 时「地块」列渲染成「三期」，否则渲染真实地块名。</p>
     */
    private Integer thirdPhase;

    /**
     * 产品名称（冗余字段，便于列表展示，免 JOIN）。
     */
    private String productName;

    /**
     * 当前库存数量。
     */
    private BigDecimal productStock;

    /**
     * 产品单位（kg / 头 / 箱 等）。
     */
    private String productUnit;

    /**
     * 是否完成（字典 {@code djs_yes_no}：1=是（已用完不显示）/ 0=否（进行中））。
     */
    private Integer isEnd;

    /**
     * 最新盘点时间（由 WMS-STOCK-001 D11 盘点流程更新）。
     */
    private Date latestCheckTime;

    /**
     * 盘点结果（字典 {@code djs_check_result}：1=正常 / 2=异常 / 3=计损）。
     */
    private Integer checkResult;

    /**
     * 最后操作人（FK → {@code sys_user.user_id}；ADR-0007）。
     */
    private Long operatorId;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

    /**
     * 读某条库存行的【三期】标识并归一成 {@code 0/1}（V6 row92）。
     *
     * <p><b>所有「先拿到 LocationStock 行、再写出库流水」的路径都必须经这里把标识带到流水上</b>
     * （{@code LocationStockServiceImpl#productOut / #pigTransfer}、{@code MatFlowServiceImpl} 的
     * 按篮领用 / 退回 / 损耗 / 饲喂各分支）。出的是哪个篮就带哪个标识 —— 甲方 row92「出库的时候也可以
     * 以三期的标识进行出库」，「三期总出库」就是按流水这一列汇总的（{@code StockFlowMapper#sumThirdPhaseByInout}），
     * 不带就漏计、出库记录的「地块」列也退回显示 {@code -}。</p>
     *
     * <p>归一成 0 而不是原样透传 null：迁移前建的存量库存行该列读出来是 null，
     * 而 {@code t_warehouse_stock_flow.third_phase} 是 {@code NOT NULL}，直接写会失败。</p>
     *
     * @param row 库存行（可空 —— 传 null 视为非三期）
     * @return 1 = 三期篮；0 = 其余
     */
    public static int thirdPhaseOf(LocationStock row) {
        return row != null && row.getThirdPhase() != null && row.getThirdPhase() == 1 ? 1 : 0;
    }

}
