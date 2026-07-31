package org.dromara.djs.breed.event.transfer.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.transfer.domain.bo.ToFattenBo;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBatchBo;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBo;
import org.dromara.djs.breed.event.transfer.domain.query.TransferQuery;
import org.dromara.djs.breed.event.transfer.domain.vo.PigTransferVo;
import org.dromara.djs.breed.event.transfer.service.ITransferService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 转移事件 Controller（BRD-EVENT-004 TRANSFER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/transfer")
public class TransferController extends BaseController {

    private final ITransferService transferService;

    @SaCheckPermission("djs:breed:event:transfer")
    @Log(title = "转移录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigTransferVo> record(@Validated @RequestBody TransferBo bo) {
        return R.ok(transferService.recordTransfer(bo));
    }

    /**
     * 批量转移（BRD-FIX-MP-EVENT-LEAVE-IA-001）。原型 98 多选批量：N 头猪 → 同一目标位置。
     * 逐头复用单只 recordTransfer，每只都触发完整 side effect，整批同一事务原子提交。
     */
    @SaCheckPermission("djs:breed:event:transfer")
    @Log(title = "批量转移录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/batch")
    public R<List<PigTransferVo>> recordBatch(@Validated @RequestBody TransferBatchBo bo) {
        return R.ok(transferService.recordTransferBatch(bo));
    }

    /**
     * 后备种母猪「转为育肥猪」（admin row162）。
     *
     * <p>猪只主表操作列入口：录转移日期 / 负责人 / 栋舍 / 栏位 → 状态 后备(HB) → 育肥(YF)、
     * 类型 种母猪 → 育肥猪，并在事件台账留一条 {@code TO_FATTEN} 记录。</p>
     */
    @SaCheckPermission("djs:breed:event:transfer")
    @Log(title = "转为育肥猪", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/to-fatten")
    public R<PigTransferVo> toFatten(@Validated @RequestBody ToFattenBo bo) {
        return R.ok(transferService.toFatten(bo));
    }

    @SaCheckPermission("djs:breed:event:transfer:list")
    @GetMapping("/list")
    public TableDataInfo<PigTransferVo> list(TransferQuery query, PageQuery pageQuery) {
        return transferService.queryPage(query, pageQuery);
    }
}
