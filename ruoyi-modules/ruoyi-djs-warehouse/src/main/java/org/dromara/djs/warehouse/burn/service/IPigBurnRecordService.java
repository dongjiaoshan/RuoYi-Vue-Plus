package org.dromara.djs.warehouse.burn.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.domain.query.PigBurnRecordQuery;
import org.dromara.djs.warehouse.burn.domain.vo.BarPendingVo;
import org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;

import java.util.List;

/**
 * 燎毛入库 Service（D12X-MP-BURN-IA-001：燎毛间 IA 重做 = list→detail + 白条按产品类型分类入库）。
 *
 * <p>核心方法 {@link #submitBurnRecord} 在同一事务内（入库语义）：</p>
 * <ol>
 *   <li>SELECT bar_info 校验 status IN ('pending_singe','singing')</li>
 *   <li>生成 burn_id + INSERT 燎毛记录（operatorId=入库人）</li>
 *   <li>for each productTypeItem：INSERT product_inhouse + INSERT stock_flow（IN，slaughter_burn）</li>
 *   <li>UPDATE bar_info status → in_stock（乐观锁）+ 回填 in_weight / in_time / in_method=1</li>
 * </ol>
 *
 * @author djs
 * @since D12X-MP-BURN-IA-001
 */
public interface IPigBurnRecordService {

    /**
     * mp 端提交燎毛入库记录（核心方法，按产品类型分别入库）。
     *
     * <p>FIX-WMS-MP-BURN-001：产品逐项入库只推进 bar 到中间态 {@code singing}（燎毛中），
     * 不直推 {@code in_stock}；bar 终态由 {@link #finishBurn} 推进。</p>
     *
     * @param bo 入参
     * @return 燎毛记录主键 id
     */
    Long submitBurnRecord(PigBurnRecordBo bo);

    /**
     * mp 端燎毛「处理完成」（FIX-WMS-MP-BURN-001 客户 6/11 新需求）。
     *
     * <p>校验 bar 在 {@code singing} 态 + 累计已入库产品总重 ≤ 出栏重量 + 整只/半只互斥（后端兜底前端约束）
     * → UPDATE bar status → {@code singed}（燎毛处理完成终态），回填 in_weight。</p>
     *
     * @param barInfoId  白条 ID
     * @param operatorId 操作人 ID（处理完成人）
     */
    void finishBurn(Long barInfoId, Long operatorId);

    /**
     * 已出栏待燎毛入库白条列表（bar_info status IN ('pending_singe','singing')，按出栏时间倒序）。
     *
     * <p>mp 燎毛入库列表页进页调用。</p>
     */
    List<BarPendingVo> queryPendingBars();

    /**
     * 入库产品类型列表（标准白条 4 类型：整只 / 半只 / 猪头 / 猪蹄）。
     *
     * <p>mp 入库子页拉取，每类型分别录入库重量。</p>
     */
    List<BurnProductTypeVo> queryProductTypes();

    /**
     * 按产品取已配置的入库库位列表（MP-PRODIN 决策 #2）。
     *
     * <p>读 {@code t_warehouse_product_info.store_location_id}（逗号分隔库位 ID），过滤为
     * 启用 + 冻品库（与燎毛入库 frozen 约束一致），按配置顺序返回。mp 产品入库弹层据此：
     * 单库位 → 只读预填；多库位 → 可选并随机默认其一；空 → 回落全量冻品库可选。</p>
     *
     * @param productId 产品主键（{@code t_warehouse_product_info.id}）
     * @return 该产品配置的入库库位（启用 + 冻品库）；未配置或无命中返回空列表
     */
    List<LocationPickerVo> queryProductInboundLocations(Long productId);

    /**
     * 分页查询。
     */
    TableDataInfo<PigBurnRecordVo> queryPageList(PigBurnRecordQuery query, PageQuery pageQuery);

    /**
     * 不分页（导出 / 报表用）。
     */
    List<PigBurnRecordVo> queryList(PigBurnRecordQuery query);

    /**
     * 详情。
     */
    PigBurnRecordVo queryById(Long id);

}
