package org.dromara.djs.store.operation.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.operation.domain.bo.StoreSaleRecordBo;
import org.dromara.djs.store.operation.domain.query.StoreSaleRecordQuery;
import org.dromara.djs.store.operation.domain.vo.StoreSaleImportVo;
import org.dromara.djs.store.operation.domain.vo.StoreSaleRecordVo;

import java.util.Collection;
import java.util.List;

/**
 * 门店销售流水服务（STR-OP-001）。
 *
 * @author djs
 * @since STR-OP-001
 */
public interface IStoreSaleRecordService {

    /**
     * 分页查询销售流水（JOIN 门店名回填）。
     *
     * @param query     筛选参数
     * @param pageQuery 分页参数
     * @return 流水 VO 分页
     */
    TableDataInfo<StoreSaleRecordVo> queryPageList(StoreSaleRecordQuery query, PageQuery pageQuery);

    /**
     * 详情。
     *
     * @param id 主键
     * @return 流水 VO
     */
    StoreSaleRecordVo queryById(Long id);

    /**
     * 手录单条销售（source='manual'）；产品名 / 单位按 productId 反查冗余。
     *
     * @param bo 手录入参
     * @return 新增主键
     */
    Long addManual(StoreSaleRecordBo bo);

    /**
     * 批量软删。
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteByIds(Collection<Long> ids);

    /**
     * Excel 导入：逐行按 productName + storeId 反查 productId / sale_unit，校验通过批量入库（source='excel_import'）。
     *
     * @param storeId  目标门店 ID
     * @param rows     解析出的导入行
     * @param operatorId 操作人（导入触发者）
     * @return 校验通过并入库的行数
     */
    int importRows(Long storeId, List<StoreSaleImportVo> rows, Long operatorId);

}
