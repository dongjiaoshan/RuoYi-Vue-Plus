package org.dromara.djs.store.split.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.split.domain.bo.StoreSplitBo;
import org.dromara.djs.store.split.domain.query.StoreSplitQuery;
import org.dromara.djs.store.split.domain.vo.StoreSplitVo;

import java.util.List;

/**
 * 门店白条分割 Service（STR-SPLIT-001，admin only 无 mp）。
 *
 * <p>复用 warehouse 域过程产品表 {@code t_warehouse_product_inhouse}，用 {@code source} 列区分来源：
 * 列表 / 导出固定查 {@code source='store'}（门店再分行，不看仓库分割 {@code source='warehouse'} 行）；
 * 录入写 {@code source='store'} 的新行。</p>
 *
 * <p>V1 最薄版本：纯独立录入，不扣减源部位重量、不做重量勾稽、不写库存流水（业务理由待客户澄清后 V2 补）。</p>
 *
 * @author djs
 * @since STR-SPLIT-001
 */
public interface IStoreSplitService {

    /**
     * 门店分割记录分页列表（固定 {@code source='store'} + 按 query 条件筛选）。
     */
    TableDataInfo<StoreSplitVo> queryPage(StoreSplitQuery query, PageQuery pageQuery);

    /**
     * 门店分割明细列表（导出用，不分页，固定 {@code source='store'}）。
     */
    List<StoreSplitVo> queryList(StoreSplitQuery query);

    /**
     * 门店再分录入：选部位 + 录重量 + 库位 + 可选源白条 → 按 {@code cutPart} 反查标准 SKU
     * → INSERT 一行 inhouse（{@code source='store'}）。
     *
     * @param bo 录入入参
     * @return 新建 inhouse 行主键（snowflake）
     */
    Long addSplit(StoreSplitBo bo);

}
