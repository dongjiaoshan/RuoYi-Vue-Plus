package org.dromara.djs.store.demand.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.store.demand.domain.bo.StoreDemandQuantityBo;
import org.dromara.djs.store.demand.domain.vo.StoreDemandCatalogVo;
import org.dromara.djs.store.demand.domain.vo.StoreDemandDayVo;
import org.dromara.djs.store.demand.service.IStoreDemandAppletService;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

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

    /** mp 门店板块专属读模型 + 改量删行（V6 row66/68/70）。 */
    private final IStoreDemandAppletService storeDemandAppletService;

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

    /**
     * 按天聚合的需求卡列表（mp 需求下单首页，V6 row66）。
     *
     * <p>一行 = 门店某一天的全部需求汇总（下单人 / 品类数 / 确认率 / 日状态 / 计损量 / 最后下单时间）。
     * 统计口径统一排除门店态「已删除」的行；某天全删则该天整条不出现。分页在 SQL 层。</p>
     */
    @SaCheckLogin
    @GetMapping("/day-list")
    public TableDataInfo<StoreDemandDayVo> dayList(
        @RequestParam(required = false) Long storeId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate beginDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
        PageQuery pageQuery) {
        return storeDemandAppletService.queryDayList(storeId, beginDate, endDate, pageQuery);
    }

    /**
     * 某天的需求明细（mp 需求详情页，V6 row70）。
     *
     * @param storeStatuses 门店视角态多选，逗号分隔（如 {@code SUBMITTED,CONFIRMED}）；
     *                      不传 = 不按状态筛。已删除行永不返回，传 {@code DELETED} 直接报错。
     */
    @SaCheckLogin
    @GetMapping("/day-detail")
    public R<List<DemandManageVo>> dayDetail(
        @RequestParam Long storeId,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate demandDate,
        @RequestParam(required = false) String productName,
        @RequestParam(required = false) String storeStatuses) {
        return R.ok(storeDemandAppletService.queryDayDetail(storeId, demandDate, productName, storeStatuses));
    }

    /**
     * 下单目录（mp 下单页产品清单，V6 row68）。
     *
     * <p>服务端已完成过滤 / 关键字模糊 / 排序（果蔬按最早采摘日升序无日期沉底、其余按本店最近下单时间倒序
     * 无记录沉底，同组内再按产品 id 倒序稳定收口），前端只按品类切片、不再排序。一次返全量，上限 1000 条。</p>
     */
    @SaCheckLogin
    @GetMapping("/catalog")
    public R<List<StoreDemandCatalogVo>> catalog(@RequestParam Long storeId,
                                                 @RequestParam(required = false) String keyword) {
        return R.ok(storeDemandAppletService.queryCatalog(storeId, keyword));
    }

    /**
     * 购物车整单下单（V6 row69）。
     *
     * <p>直接 delegate 既有 {@link IStoreDemandService#batchCreate}（与 admin 购物车同一条落库路径），
     * 不在 mp 侧复写业务逻辑。</p>
     *
     * <p>同一天同一产品重复下单当前会新建第二条 demand 行（详情页显示两行同名产品）。甲方原文
     * 「整合到对应日期的需求内容」只约束了列表层（一天一张卡，day-list 已满足）；<b>是否行级合并累加待甲方拍板，
     * 本轮不做合并</b>。</p>
     *
     * @return 落库条数
     */
    @SaCheckLogin
    @PostMapping("/batch")
    public R<Integer> batchCreate(@Validated @RequestBody StoreDemandBatchBo bo) {
        // 走 applet service：它在共用落库路径之上加了 mp 侧三道闸
        // （需求日期下界 / 业态服务端推导 / 原材料一律拒），详见 IStoreDemandAppletService#batchCreate
        return R.ok(storeDemandAppletService.batchCreate(bo));
    }

    /**
     * 改需求量 / 删行（mp 需求详情页，V6 row70）。
     *
     * <p>只允许门店态「待确认」的行；{@code demandQuantity=0} 走既有删除路径（置 DELETED 终态 + 软删）。</p>
     */
    @SaCheckLogin
    @PutMapping("/quantity")
    public R<Void> updateQuantity(@Validated @RequestBody StoreDemandQuantityBo bo) {
        storeDemandAppletService.updateQuantity(bo);
        return R.ok();
    }
}
