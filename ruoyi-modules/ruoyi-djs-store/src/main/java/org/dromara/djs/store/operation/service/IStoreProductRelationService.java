package org.dromara.djs.store.operation.service;

import org.dromara.djs.store.operation.domain.bo.StoreProductRelationSyncBo;
import org.dromara.djs.store.operation.domain.vo.StoreProductCandidateVo;
import org.dromara.djs.store.operation.domain.vo.StoreProductRelationVo;

import java.util.List;

/**
 * 门店产品关联服务（STR-OP-001）。
 *
 * @author djs
 * @since STR-OP-001
 */
public interface IStoreProductRelationService {

    /**
     * 查询某门店已关联的产品列表（el-transfer 右侧），按产品 JOIN 回填名称 / 单位 / 规格。
     *
     * @param storeId 门店 ID
     * @return 已关联产品 VO 列表
     */
    List<StoreProductRelationVo> listByStore(Long storeId);

    /**
     * 查询全部在售候选 SKU（el-transfer 左侧）。
     *
     * @return 候选产品 VO 列表
     */
    List<StoreProductCandidateVo> listCandidates();

    /**
     * 按门店全量 diff 同步关联：目标列表中新增的 INSERT（is_active=1），已存在但不在目标列表的 softDelete。
     *
     * @param bo 同步入参（storeId + 目标产品 ID 全集）
     * @return 实际变更行数（新增 + 软删）
     */
    int syncRelations(StoreProductRelationSyncBo bo);

}
