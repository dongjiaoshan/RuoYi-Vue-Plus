package org.dromara.djs.breed.event.heat.service.impl;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.heat.domain.PigHeat;
import org.dromara.djs.breed.event.heat.domain.bo.HeatBo;
import org.dromara.djs.breed.event.heat.mapper.PigHeatMapper;
import org.dromara.djs.breed.event.weaning.mapper.PigWeaningMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HeatServiceImpl} 单元测试（BRD-EVENT-002 OESTRUS / 602-5 录入人）。
 *
 * <p>覆盖 602-5 录入人优先级：mp EmployeePicker 传 operator(userId string) 时落库 operatorId=该值；
 * operator 为空时回落 LoginHelper.getUserId()；operator 非法时 log.warn 后回落登录态。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HeatServiceImpl 单元测试 (602-5 录入人)")
class HeatServiceImplTest {

    @Mock
    private PigHeatMapper heatMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private PigWeaningMapper weaningMapper;
    @Mock
    private IPigCoreService pigCoreService;

    private static final long LOGIN_USER_ID = 1001L;

    private HeatServiceImpl service() {
        return new HeatServiceImpl(heatMapper, pigMapper, weaningMapper, pigCoreService);
    }

    private Pig mkSow() {
        Pig p = new Pig();
        p.setId(300L);
        p.setEarNo("260520-001");
        p.setPigSex("F");
        p.setPigType("sow");
        p.setCurrentStatus(PigLifecycle.PZ.name());
        return p;
    }

    private HeatBo mkBo() {
        HeatBo bo = new HeatBo();
        bo.setPigId(300L);
        bo.setHeatDate(LocalDateTime.of(2026, 6, 16, 10, 0));
        // 602-5：查情结果 / 确认妊娠均不再录入（mp 不传），保持 null
        return bo;
    }

    @Test
    @DisplayName("happy: 传 operator(userId string) → operatorId 落该值（非登录态）")
    void operator_overrides_login() {
        when(pigMapper.selectById(300L)).thenReturn(mkSow());

        try (MockedStatic<LoginHelper> mocked = Mockito.mockStatic(LoginHelper.class)) {
            mocked.when(LoginHelper::getUserId).thenReturn(LOGIN_USER_ID);

            HeatBo bo = mkBo();
            bo.setOperator("2061591133665759233"); // EmployeePicker 所选 userId（19 位雪花 string）
            service().recordHeat(bo);

            ArgumentCaptor<PigHeat> cap = ArgumentCaptor.forClass(PigHeat.class);
            verify(heatMapper, times(1)).insert(cap.capture());
            // operator 非空 → operatorId = 所选 userId（不截断、非登录态）
            assertThat(cap.getValue().getOperatorId()).isEqualTo(2061591133665759233L);
            // 未确认妊娠（confirmed=null→false）→ 不触发状态机事件
            verify(pigCoreService, never()).fireEvent(org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    @DisplayName("fallback: operator 为空 → operatorId 回落登录态")
    void blank_operator_falls_back_to_login() {
        when(pigMapper.selectById(300L)).thenReturn(mkSow());

        try (MockedStatic<LoginHelper> mocked = Mockito.mockStatic(LoginHelper.class)) {
            mocked.when(LoginHelper::getUserId).thenReturn(LOGIN_USER_ID);

            HeatBo bo = mkBo();
            bo.setOperator("   "); // 空白 → 回落
            service().recordHeat(bo);

            ArgumentCaptor<PigHeat> cap = ArgumentCaptor.forClass(PigHeat.class);
            verify(heatMapper, times(1)).insert(cap.capture());
            assertThat(cap.getValue().getOperatorId()).isEqualTo(LOGIN_USER_ID);
        }
    }

    @Test
    @DisplayName("fallback: operator 非法（非数字）→ log.warn 后回落登录态，不抛异常")
    void illegal_operator_falls_back_to_login() {
        when(pigMapper.selectById(300L)).thenReturn(mkSow());

        try (MockedStatic<LoginHelper> mocked = Mockito.mockStatic(LoginHelper.class)) {
            mocked.when(LoginHelper::getUserId).thenReturn(LOGIN_USER_ID);

            HeatBo bo = mkBo();
            bo.setOperator("not-a-number");
            service().recordHeat(bo);

            ArgumentCaptor<PigHeat> cap = ArgumentCaptor.forClass(PigHeat.class);
            verify(heatMapper, times(1)).insert(cap.capture());
            assertThat(cap.getValue().getOperatorId()).isEqualTo(LOGIN_USER_ID);
        }
    }
}
