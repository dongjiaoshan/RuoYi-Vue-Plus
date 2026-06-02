package org.dromara.djs.store.member.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.member.domain.bo.StoreMemberBo;
import org.dromara.djs.store.member.domain.bo.StoreMemberConsumptionBo;
import org.dromara.djs.store.member.domain.query.StoreMemberQuery;
import org.dromara.djs.store.member.domain.vo.StoreMemberConsumptionVo;
import org.dromara.djs.store.member.domain.vo.StoreMemberStatsVo;
import org.dromara.djs.store.member.domain.vo.StoreMemberVo;
import org.dromara.djs.store.member.service.IStoreMemberConsumptionService;
import org.dromara.djs.store.member.service.IStoreMemberService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 会员档案 + 手录消费 admin 端 Controller（STR-MEMBER-001，店长视角）。
 *
 * <p>端点：会员档案 CRUD + 导出 + 简单统计（本月会员数 / 消费记录数）+ 手录消费列表 / 录入。
 * 权限串 {@code djs:store:member:{list,query,add,edit,remove,export}} + {@code consume:{list,add}}
 * （menu 10320-10327）。</p>
 *
 * <p>V1 会员只做「档案 + 手录」，不做 RFM / A-B-C 分类 / 营销分析（迁 V2）。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/store/member")
public class StoreMemberController extends BaseController {

    private final IStoreMemberService memberService;
    private final IStoreMemberConsumptionService consumptionService;

    /**
     * 会员档案分页（按手机号 / 姓名 / 等级 / 门店筛选）。
     */
    @SaCheckPermission("djs:store:member:list")
    @GetMapping("/list")
    public TableDataInfo<StoreMemberVo> list(StoreMemberQuery query, PageQuery pageQuery) {
        return memberService.queryPageList(query, pageQuery);
    }

    /**
     * 会员详情。
     */
    @SaCheckPermission("djs:store:member:query")
    @GetMapping("/{id}")
    public R<StoreMemberVo> getInfo(@PathVariable Long id) {
        return R.ok(memberService.queryById(id));
    }

    /**
     * 新增会员（生成 member_no + 手机号查重）。
     */
    @SaCheckPermission("djs:store:member:add")
    @Log(title = "会员档案", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody StoreMemberBo bo) {
        return R.ok(memberService.add(bo));
    }

    /**
     * 编辑会员。
     */
    @SaCheckPermission("djs:store:member:edit")
    @Log(title = "会员档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Valid @RequestBody StoreMemberBo bo) {
        memberService.update(bo);
        return R.ok();
    }

    /**
     * 软删会员（{@code del_flag='1' + del_unique=id}）。
     */
    @SaCheckPermission("djs:store:member:remove")
    @Log(title = "会员档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        memberService.deleteByIds(Arrays.asList(ids));
        return R.ok();
    }

    /**
     * 会员档案导出（ExcelUtil）。
     */
    @SaCheckPermission("djs:store:member:export")
    @Log(title = "会员档案", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(StoreMemberQuery query, HttpServletResponse response) {
        List<StoreMemberVo> list = memberService.queryList(query);
        ExcelUtil.exportExcel(list, "会员档案", StoreMemberVo.class, response);
    }

    /**
     * 简单统计：本月会员数 + 本月录入消费记录数（两个 count，不做 RFM）。
     */
    @SaCheckPermission("djs:store:member:list")
    @GetMapping("/stats")
    public R<StoreMemberStatsVo> stats() {
        return R.ok(memberService.getMonthlyStats());
    }

    /**
     * 某会员的手录消费记录列表（按 memberId 过滤，消费日期倒序）。
     */
    @SaCheckPermission("djs:store:member:consume:list")
    @GetMapping("/consume/list")
    public R<List<StoreMemberConsumptionVo>> consumeList(@RequestParam Long memberId) {
        return R.ok(consumptionService.listByMember(memberId));
    }

    /**
     * 手录一条消费记录（{@code create_by} 自动 fill 录入人，不强校验金额）。
     */
    @SaCheckPermission("djs:store:member:consume:add")
    @Log(title = "会员消费", businessType = BusinessType.INSERT)
    @PostMapping("/consume")
    public R<Long> consumeAdd(@Valid @RequestBody StoreMemberConsumptionBo bo) {
        return R.ok(consumptionService.add(bo));
    }

}
