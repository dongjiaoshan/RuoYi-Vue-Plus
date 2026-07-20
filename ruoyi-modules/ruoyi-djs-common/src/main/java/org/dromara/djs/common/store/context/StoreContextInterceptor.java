package org.dromara.djs.common.store.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 门店上下文拦截器（STORE-PERM-001）。
 *
 * <p>{@link #preHandle} 读请求头 {@code Current-Store-Id} → 校验 → 写入 {@link StoreContext}。
 * 上下文存于 Sa-Token 请求级 {@code SaStorage}，请求结束自动销毁，<b>无需</b> afterCompletion 清理
 * （区别于裸 ThreadLocal）。</p>
 *
 * <h3>跨门店访问拦截（spec：绕过前端校验后端拦截返回权限错误）</h3>
 * <p>非超管账号请求头携带的门店必须在「当前登录人有权限门店」集合内
 * （{@link IStoreUserRelationService#listStoreIdsByUser}，仅取未软删绑定，对齐"移除绑定→视角隐藏"），
 * 否则抛 {@link ServiceException}(403) 拒绝。超管 / 租管（{@link StoreContext#isIgnore()}）免绑定校验，
 * 但仍按所选门店写入上下文（option B：显式选店即按该店过滤；未选门店才看全部）。
 * 校验走实时 DB（移除绑定即时生效，强于"重登后生效"）。</p>
 *
 * <p>注册见 {@code DjsStoreWebConfig}：在 SaInterceptor 之后注册，拦 {@code /**}。</p>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@RequiredArgsConstructor
public class StoreContextInterceptor implements HandlerInterceptor {

    private final IStoreUserRelationService storeUserRelationService;

    /**
     * 门店业务域 API 前缀。非超管访问该前缀下接口必须携带 {@code Current-Store-Id}，否则拒绝
     * （无门店上下文不得读门店数据，对齐"无绑定→数据全隐"，堵死 no-header 兜底返全部漏洞）。
     * 门店主数据 {@code /djs/common/store/**}（含 my-stores / 绑定）与仓库域 {@code /djs/warehouse/**}
     * 不在此前缀内：前者操作 t_md_store 自身（非门店行级表）、需在未选门店时可达；后者跨门店查共享表无 header。
     */
    private static final String STORE_DOMAIN_PREFIX = "/djs/store/";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 门店上下文只作用于门店业务域 /djs/store/**：其余域（仓库 /djs/warehouse/** 等跨门店查共享表
        // t_warehouse_demand_manage / t_warehouse_product_inhouse）不注入上下文 → storeId 恒空 →
        // StoreLineHandler 不按门店过滤 → 照常看全部门店（option B：门店切换器不误伤仓库聚合视图）。
        if (!request.getRequestURI().startsWith(STORE_DOMAIN_PREFIX)) {
            return true;
        }
        String storeId = request.getHeader(StoreContext.HEADER_STORE_ID);
        boolean ignore = StoreContext.isIgnore();           // 超管 / 租管：免绑定校验（仍按所选门店过滤）
        Long userId = currentUserId();

        if (StringUtils.isBlank(storeId)) {
            // 登录的非超管访问门店业务域却未选门店 → 拒绝（不允许空上下文读门店表返全部）；
            // 超管 / 租管未选门店 → 放行看全部门店。
            if (!ignore && userId != null) {
                throw new ServiceException("请先选择门店后再操作", 403);
            }
            return true;
        }
        // 非超管：请求头门店必须在当前登录人有权限门店内，否则拒绝（防绕过前端校验跨门店读写）
        if (!ignore && userId != null) {
            Long sid = parseStoreId(storeId);
            // 门店墙感知：V1 关墙时任一门店放行（ADR-0018 全员可跨店），开墙则按绑定校验
            if (!storeUserRelationService.isStoreAccessible(userId, sid)) {
                throw new ServiceException("无该门店操作权限，请重新选择门店", 403);
            }
        }
        // 写入上下文（含超管 / 租管）：StoreLineHandler 据此按所选门店过滤（option B）
        StoreContext.setStoreId(storeId);
        return true;
    }

    /**
     * 当前登录用户 ID；无登录上下文返 null（不抛）。
     */
    private static Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 请求头门店 ID 转 Long；非法返 null（由调用方按"无权限"处理，绝不污染查询）。
     */
    private static Long parseStoreId(String storeId) {
        try {
            return Long.valueOf(storeId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
