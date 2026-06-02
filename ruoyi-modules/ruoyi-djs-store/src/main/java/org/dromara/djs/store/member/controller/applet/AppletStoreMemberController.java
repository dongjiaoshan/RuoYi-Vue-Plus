package org.dromara.djs.store.member.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.member.domain.bo.StoreMemberBo;
import org.dromara.djs.store.member.domain.bo.StoreMemberConsumptionBo;
import org.dromara.djs.store.member.domain.query.StoreMemberQuery;
import org.dromara.djs.store.member.domain.vo.StoreMemberConsumptionVo;
import org.dromara.djs.store.member.domain.vo.StoreMemberVo;
import org.dromara.djs.store.member.service.IStoreMemberConsumptionService;
import org.dromara.djs.store.member.service.IStoreMemberService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序门店会员 Controller（STR-MEMBER-001，mp 店员视角）。
 *
 * <p>URL 前缀 {@code /djs/applet/store/member}（ADR-0009：mp 走 {@code /djs/applet/<module>/<biz>}）。
 * 店员在 mp 端建档 / 编辑会员 + 查本店会员列表 + 手录消费 + 查某会员消费记录。</p>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin}（V1 mock auth + ADR-0003，store_clerk / store_admin 登录即可用，不查 perm）——
 * 与现有 {@code AppletStoreDemandController} 范式一致。</p>
 *
 * <h2>复用</h2>
 * <p>全部 delegate 到 admin 共用的 {@link IStoreMemberService} / {@link IStoreMemberConsumptionService}，
 * 不在本 controller 复写编码 / 查重 / 校验逻辑。门店视角隔离：列表按 {@code storeId} 显式过滤
 * （店员在 mp 选门店；V1 不做行级拦截器）。</p>
 *
 * @author djs
 * @since STR-MEMBER-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/store/member")
public class AppletStoreMemberController {

    private final IStoreMemberService memberService;
    private final IStoreMemberConsumptionService consumptionService;

    /**
     * 本店会员列表（mp 店员视角，按手机号 / 姓名 / 门店过滤）。
     */
    @SaCheckLogin
    @GetMapping("/list")
    public TableDataInfo<StoreMemberVo> list(StoreMemberQuery query, PageQuery pageQuery) {
        return memberService.queryPageList(query, pageQuery);
    }

    /**
     * 会员详情。
     */
    @SaCheckLogin
    @GetMapping("/getInfo/{id}")
    public R<StoreMemberVo> getInfo(@PathVariable Long id) {
        return R.ok(memberService.queryById(id));
    }

    /**
     * 建档（mp 店员新建会员，手机号查重 + 生成 member_no）。返回新会员 ID（string）。
     */
    @SaCheckLogin
    @PostMapping("/add")
    public R<Long> add(@Valid @RequestBody StoreMemberBo bo) {
        return R.ok(memberService.add(bo));
    }

    /**
     * 编辑会员。
     */
    @SaCheckLogin
    @PutMapping("/edit")
    public R<Void> edit(@Valid @RequestBody StoreMemberBo bo) {
        memberService.update(bo);
        return R.ok();
    }

    /**
     * 某会员的消费记录列表（按 memberId，消费日期倒序）。
     */
    @SaCheckLogin
    @GetMapping("/consume/list")
    public R<List<StoreMemberConsumptionVo>> consumeList(@RequestParam Long memberId) {
        return R.ok(consumptionService.listByMember(memberId));
    }

    /**
     * 手录一条消费记录（{@code create_by} 自动 fill 录入人，不强校验金额）。返回新记录 ID（string）。
     */
    @SaCheckLogin
    @PostMapping("/consume/add")
    public R<Long> consumeAdd(@Valid @RequestBody StoreMemberConsumptionBo bo) {
        return R.ok(consumptionService.add(bo));
    }

}
