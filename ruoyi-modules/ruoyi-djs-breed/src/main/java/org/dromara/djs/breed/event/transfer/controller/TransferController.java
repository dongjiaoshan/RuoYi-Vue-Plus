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

    @SaCheckPermission("djs:breed:event:transfer:list")
    @GetMapping("/list")
    public TableDataInfo<PigTransferVo> list(TransferQuery query, PageQuery pageQuery) {
        return transferService.queryPage(query, pageQuery);
    }
}
