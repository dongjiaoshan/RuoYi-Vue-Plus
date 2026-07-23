package org.dromara.djs.store.loss.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.loss.domain.query.StoreLossQuery;
import org.dromara.djs.store.loss.domain.vo.StoreLossRecordVo;
import org.dromara.djs.store.loss.domain.vo.WhiteBarSplitLossVo;
import org.dromara.djs.store.loss.service.IStoreLossService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 门店损耗记录 admin Controller（DENGBO-R30）。
 *
 * <p>列表：门店管理 &gt; 门店损耗记录（读 {@code t_store_loss_record}，门店维度行级注入）。
 * 白条到店重：门店盘点抽屉「当日白条分割损耗」实时基数。</p>
 *
 * @author djs
 * @since DENGBO-R30
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/store/loss")
public class StoreLossController extends BaseController {

    private final IStoreLossService service;

    /** 门店损耗记录分页列表（门店维度由 {@code Current-Store-Id} 头行级注入）。 */
    @SaCheckPermission("djs:storeLoss:list")
    @GetMapping("/list")
    public TableDataInfo<StoreLossRecordVo> list(StoreLossQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /**
     * 当日某门店白条分割损耗（门店盘点抽屉「当日白条分割损耗」展示，口径同定时任务）。
     * = max(0, 白条到店重 + 材料外售同产品到店重 − 白条退回产品入库重)；退回入库重来自门店退货入库流水 {@code t_store_return}。
     *
     * @param storeId 门店 id
     * @param date    业务日（yyyy-MM-dd，空取今天）
     * @return 到店重 / 退回入库重 / 分割损耗（无白条到店三者均 0，前端据 arriveWeight=0 隐藏）
     */
    @SaCheckPermission("djs:storeLoss:list")
    @GetMapping("/white-bar-split-loss")
    public R<WhiteBarSplitLossVo> whiteBarSplitLoss(@RequestParam Long storeId,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return R.ok(service.getWhiteBarSplitLoss(storeId, date));
    }
}
