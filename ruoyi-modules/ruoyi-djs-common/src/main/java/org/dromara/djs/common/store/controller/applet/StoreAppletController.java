package org.dromara.djs.common.store.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序门店 picker Controller（MP-UX-002）。
 *
 * <p>专给 mp 端 {@code StorePicker} 组件远程拉门店候选用。复用
 * {@link StoreMapper#selectList} 直接查 entity，转 {@link StorePickerVo}
 * 只保留 id + storeName + storeCode，不动 admin 端的 {@code IStoreService}。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/common/store/list?keyword=&storeType=}
 *       返合作中（businessStatus='0'）门店列表；按 keyword LIKE 匹配 storeName/storeCode。</li>
 *   <li>{@code GET /djs/applet/common/store/my-stores}
 *       返当前登录人「我的门店」（门店墙感知，见 {@link IStoreUserRelationService#listMyStores(boolean)}）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckLogin} + {@code @SaCheckPermission("djs:applet:common:store:list")}—
 * mp_role 默认含；菜单 seed 见 {@code V202606061100__MP-UX-002-applet-permission-menus.sql}。</p>
 *
 * @author djs
 * @since MP-UX-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/common/store")
public class StoreAppletController {

    private final StoreMapper storeMapper;

    private final IStoreUserRelationService storeUserRelationService;

    /**
     * 门店 picker 列表。
     *
     * <p>过滤口径：</p>
     * <ul>
     *   <li>{@code business_status='0'} 仅返合作中</li>
     *   <li>{@code del_flag='0'} 由 MP 全局拦截器兜底</li>
     *   <li>{@code keyword} 同时 LIKE storeName 与 storeCode</li>
     *   <li>{@code storeType} 精确匹配（如指定屠宰加工点）</li>
     *   <li>结果按 ID 倒序，最多 200 条（picker 不分页）</li>
     * </ul>
     *
     * @param keyword   关键字（同时模糊匹配 storeName / storeCode）
     * @param storeType 字典 {@code djs_store_type} 精确匹配
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:common:store:list")
    @GetMapping("/list")
    public R<List<StorePickerVo>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String storeType
    ) {
        LambdaQueryWrapper<Store> wrapper = new LambdaQueryWrapper<Store>()
            .eq(Store::getBusinessStatus, "0")
            .eq(StringUtils.isNotBlank(storeType), Store::getStoreType, storeType)
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(Store::getStoreName, keyword)
                .or()
                .like(Store::getStoreCode, keyword))
            .orderByDesc(Store::getId)
            .last("LIMIT 200");
        List<Store> rows = storeMapper.selectList(wrapper);
        List<StorePickerVo> vos = rows.stream().map(s -> {
            StorePickerVo vo = new StorePickerVo();
            vo.setId(s.getId());
            vo.setStoreName(s.getStoreName());
            vo.setStoreCode(s.getStoreCode());
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

    /**
     * 我的门店（mp 门店板块「我的」+ 需求下单页顶部门店切换，V6 row65）。
     *
     * <p>语义随 {@code djs.store.wall-enabled} 走：当前 false（关墙，ADR-0018 全员可跨店）→ 返<b>全部可下单</b>
     * 门店；将来置 true → 只返当前登录人绑定的门店。前端一律按「返 1 条 = 只读不可选 / 返 &gt;1 条 = 可选」
     * 渲染，所以开墙那天前端零改动。</p>
     *
     * <p>过滤判据与下单硬闸 {@code IStoreService.assertStoreActive} <b>逐条相同</b>：只排除
     * {@code business_status='1'}（已终止），{@code '2'}（装修中）照返 —— 装修中门店后端是能下单的，
     * 挡掉它等于让那家店的店员看到「无可用门店」、整个板块不可用。<b>注意本端点与 {@link #list}
     * 口径不同</b>（后者按 {@code ='0'} 只留合作中，那是跨板块通用 picker 的口径，不要拿来对齐）。
     * admin 侧 {@code /djs/common/store/my-stores} 仍走不带过滤的 {@code listMyStores()}，行为不变。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:common:store:list")
    @GetMapping("/my-stores")
    public R<List<StorePickerVo>> myStores() {
        return R.ok(storeUserRelationService.listMyStores(true));
    }

}
