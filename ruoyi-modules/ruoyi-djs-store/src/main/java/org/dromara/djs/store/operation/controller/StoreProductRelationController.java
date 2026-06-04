package org.dromara.djs.store.operation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.operation.domain.bo.StoreProductRelationSyncBo;
import org.dromara.djs.store.operation.domain.vo.StoreProductCandidateVo;
import org.dromara.djs.store.operation.domain.vo.StoreProductRelationVo;
import org.dromara.djs.store.operation.service.IStoreProductRelationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店产品关联 Controller（STR-OP-001，admin 端 el-transfer 配「门店能卖哪些 SKU」）。
 *
 * <p>权限串：{@code djs:storeRelation:{list,query,edit}}（seed 在
 * {@code V202606141220__STR-OP-001-menu.sql}，menu_id 10110-10112）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/store/relation")
public class StoreProductRelationController extends BaseController {

    private final IStoreProductRelationService relationService;

    /**
     * el-transfer 右侧：某门店已关联产品列表。
     */
    @SaCheckPermission("djs:storeRelation:list")
    @GetMapping("/list")
    public R<List<StoreProductRelationVo>> list(@RequestParam Long storeId) {
        return R.ok(relationService.listByStore(storeId));
    }

    /**
     * el-transfer 左侧：全部候选 SKU。
     */
    @SaCheckPermission("djs:storeRelation:query")
    @GetMapping("/candidates")
    public R<List<StoreProductCandidateVo>> candidates() {
        return R.ok(relationService.listCandidates());
    }

    /**
     * 保存：按门店全量 diff 同步关联（新增 INSERT / 移除 softDelete）。
     */
    @SaCheckPermission("djs:storeRelation:edit")
    @Log(title = "门店产品关联", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/sync")
    public R<Integer> sync(@Validated @RequestBody StoreProductRelationSyncBo bo) {
        return R.ok(relationService.syncRelations(bo));
    }
}
