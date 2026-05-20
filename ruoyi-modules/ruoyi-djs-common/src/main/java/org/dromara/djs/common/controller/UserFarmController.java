package org.dromara.djs.common.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.vo.FarmVo;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 用户农场切换 Controller。
 *
 * <p>V1 multi-farm disabled（{@code djs.multi-farm.enabled=false}）：
 * 所有用户返回固定 {@code [{ id: "1001", name: "东角山主农场", current: true }]}，
 * switch 端点直接 no-op 返 ok。</p>
 *
 * <p>V2 启用后：从 sys_user.accessible_farm_ids（CSV）拆分查询 sys_farm 表，switch 端点
 * 写入 sys_user.current_farm_id 并刷新 Sa-Token session extra "farmId"。</p>
 *
 * @author djs
 * @see org.dromara.djs.common.config.DjsTenantInterceptorConfig
 */
@Slf4j
@RestController
@RequestMapping("/djs/user/farm")
@RequiredArgsConstructor
public class UserFarmController {

    private final DjsUserExtMapper djsUserExtMapper;

    @Value("${djs.multi-farm.enabled:false}")
    private boolean multiFarmEnabled;

    /**
     * 查询当前用户可访问的农场列表。
     *
     * <p>V1：始终返单元素列表 [{ id: "1001", name: "东角山主农场", current: true }]。</p>
     */
    @SaCheckPermission("djs:user:farm:query")
    @GetMapping("/accessible")
    public R<List<FarmVo>> listAccessible() {
        if (!multiFarmEnabled) {
            return R.ok(Collections.singletonList(
                new FarmVo(DjsAuthConstants.DEFAULT_FARM_ID, DjsAuthConstants.DEFAULT_FARM_NAME, true)));
        }

        // V2 路径：按 accessible_farm_ids CSV 拆分查询 sys_farm；本 ticket 仅占位
        Long userId = LoginHelper.getUserId();
        String csv = djsUserExtMapper.selectAccessibleFarmIds(userId);
        String currentFarmId = djsUserExtMapper.selectCurrentFarmId(userId);
        if (StrUtil.isBlank(csv)) {
            return R.ok(Collections.singletonList(
                new FarmVo(DjsAuthConstants.DEFAULT_FARM_ID, DjsAuthConstants.DEFAULT_FARM_NAME, true)));
        }
        List<FarmVo> list = Stream.of(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            // TODO V2: 查询 sys_farm 拿真实 farm_name
            .map(id -> new FarmVo(id, "农场-" + id, id.equals(currentFarmId)))
            .toList();
        return R.ok(list);
    }

    /**
     * 切换当前农场。
     *
     * <p>V1：无效操作（multi-farm disabled），直接返 ok。
     * V2：UPDATE sys_user.current_farm_id 并刷新 Sa-Token session extra "farmId"。</p>
     */
    @SaCheckPermission("djs:user:farm:switch")
    @PostMapping("/switch")
    public R<Void> switchFarm(@RequestParam String farmId) {
        if (!multiFarmEnabled) {
            log.info("V1 multi-farm disabled, switchFarm farmId={} no-op", farmId);
            return R.ok();
        }
        Long userId = LoginHelper.getUserId();
        int rows = djsUserExtMapper.updateCurrentFarmId(userId, farmId);
        if (rows == 0) {
            return R.fail("切换农场失败");
        }
        // TODO V2: 刷新当前 Sa-Token session extra "farmId"（StpUtil.getTokenSession().set(...)）
        return R.ok();
    }
}
