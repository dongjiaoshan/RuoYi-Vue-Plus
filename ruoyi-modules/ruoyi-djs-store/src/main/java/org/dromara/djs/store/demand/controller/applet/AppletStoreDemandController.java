package org.dromara.djs.store.demand.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序门店需求 Controller（STR-DEMAND-001，mp 门店店员视角）。
 *
 * <p>URL 前缀 {@code /djs/applet/store/demand}（ADR-0009 新建走 {@code /djs/applet/<module>/<biz>}）。
 * 店员在 mp 端发起本店采购需求（创建即 SUBMITTED）+ 查列表 + 撤回 + 白条指定猪只。</p>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（V1 mock auth + ADR-0003，store_clerk / store_admin 角色可用，不查 perm）。</p>
 *
 * <h2>复用</h2>
 * <p>列表 / 详情 / 取消 / 指定猪只 delegate warehouse service；创建即提交走门店专属
 * {@link IStoreDemandService}——不在本 controller 复写状态机 / 编码 / 校验。</p>
 *
 * <p>门店视角隔离：列表按 {@code storeId} 显式过滤（店员在 mp 选门店；V1 不做行级拦截器）。</p>
 *
 * @author djs
 * @since STR-DEMAND-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/store/demand")
public class AppletStoreDemandController {

    private final IStoreDemandService storeDemandService;

    private final IDemandManageService demandManageService;

    private final IDemandStatusService demandStatusService;

    private final IPigQueryService pigQueryService;

    /** 本店需求列表（mp 店员视角，按 storeId 过滤）。 */
    @SaCheckLogin
    @GetMapping("/list")
    public TableDataInfo<DemandManageVo> list(DemandManageQuery query, PageQuery pageQuery) {
        return demandManageService.queryPageList(query, pageQuery);
    }

    /** 需求详情。 */
    @SaCheckLogin
    @GetMapping("/getInfo/{id}")
    public R<DemandManageVo> getInfo(@PathVariable Long id) {
        return R.ok(demandManageService.queryById(id));
    }

    /** 创建需求（创建即 SUBMITTED，跳过 DRAFT）。 */
    @SaCheckLogin
    @PostMapping("/add")
    public R<Long> add(@Validated @RequestBody DemandManageBo bo) {
        return R.ok(storeDemandService.createStoreDemand(bo));
    }

    /** 撤回未确认需求（→ CANCELLED）。 */
    @SaCheckLogin
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String remark) {
        // operator 传 null → warehouse service 内 LoginHelper 兜底（mp mock token 无 user_id 时不阻断）
        demandStatusService.transition(id, DemandEvent.CANCEL, null, remark);
        return R.ok();
    }

    /** 白条业态：批量指定可出栏猪只。 */
    @SaCheckLogin
    @PostMapping("/{id}/pigs")
    public R<Integer> assignPigs(@PathVariable Long id, @Valid @RequestBody AssignPigBo bo) {
        return R.ok(demandManageService.assignPigs(id, bo));
    }

    /** 白条业态：可出栏育肥猪分页列表。 */
    @SaCheckLogin
    @GetMapping("/pigs/available")
    public TableDataInfo<PigAvailableVo> listAvailablePigs(PageQuery pageQuery) {
        return pigQueryService.listAvailableForOutbound(pageQuery);
    }
}
