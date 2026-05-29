package org.dromara.djs.plant.pick.service.impl;

import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.pick.domain.PickActivity;
import org.dromara.djs.plant.pick.domain.bo.PickActivityBo;
import org.dromara.djs.plant.pick.mapper.PickActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PickActivityServiceImpl} 单测（PLT-PLAN-002）。
 *
 * <h3>覆盖 case</h3>
 * <ol>
 *   <li>happy create：crop 校验通过 → activity_no = PA + yyyyMMdd + 001 + default upcoming + cropName 冗余</li>
 *   <li>nextActivityNo 连续递增：mock 已有 max=5 → 下一次返回 006</li>
 *   <li>summary：拉 SUM(actual_yield) → 写回 total_yield + 状态 ended</li>
 * </ol>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PickActivityServiceImpl 单元测试")
class PickActivityServiceImplTest {

    @Mock
    private PickActivityMapper baseMapper;

    @Mock
    private CropInfoMapper cropMapper;

    private PickActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PickActivityServiceImpl(baseMapper, cropMapper);
    }

    @Test
    @DisplayName("happy create：activity_no=PA20260620001 / status=upcoming / cropName 冗余")
    void create_happy() {
        // given
        CropInfo crop = new CropInfo();
        crop.setId(701L);
        crop.setCropName("南瓜");
        when(cropMapper.selectById(701L)).thenReturn(crop);
        when(baseMapper.selectMaxSeqByPrefix(eq("1001"), eq("PA20260620"))).thenReturn(0);

        PickActivityBo bo = new PickActivityBo();
        bo.setActivityName("南瓜采摘节");
        bo.setActivityDate(LocalDate.of(2026, 6, 20));
        bo.setCropId(701L);

        // when
        service.createByBo(bo);

        // then
        ArgumentCaptor<PickActivity> captor = ArgumentCaptor.forClass(PickActivity.class);
        verify(baseMapper, times(1)).insert(captor.capture());
        PickActivity inserted = captor.getValue();
        assertThat(inserted.getActivityNo()).isEqualTo("PA20260620001");
        assertThat(inserted.getActivityStatus()).isEqualTo("upcoming");
        assertThat(inserted.getCropName()).isEqualTo("南瓜");
        assertThat(inserted.getTotalPlot()).isEqualTo(0);
        assertThat(inserted.getTotalYield()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("nextActivityNo：max=5 → 返回 PAyyyyMMdd006（连续递增）")
    void nextActivityNo_increments() {
        when(baseMapper.selectMaxSeqByPrefix(eq("1001"), eq("PA20260620"))).thenReturn(5);
        String next = service.nextActivityNo(LocalDate.of(2026, 6, 20));
        assertThat(next).isEqualTo("PA20260620006");
    }

    @Test
    @DisplayName("summary：聚合 actual_yield 写回 total_yield + status=ended")
    void summary_writesBackTotalAndEnded() {
        // given
        PickActivity existing = new PickActivity();
        existing.setId(10001L);
        existing.setCropId(701L);
        existing.setActivityDate(LocalDate.of(2026, 6, 20));
        existing.setActivityStatus("ongoing");
        when(baseMapper.selectById(10001L)).thenReturn(existing);
        when(baseMapper.sumYieldForActivity(eq(701L), eq(LocalDate.of(2026, 6, 20))))
            .thenReturn(new BigDecimal("38.5"));

        // 返回 vo 简化
        org.dromara.djs.plant.pick.domain.vo.PickActivityVo vo =
            new org.dromara.djs.plant.pick.domain.vo.PickActivityVo();
        vo.setId(10001L);
        vo.setTotalYield(new BigDecimal("38.5"));
        vo.setActivityStatus("ended");
        when(baseMapper.selectVoById(10001L)).thenReturn(vo);

        // when
        org.dromara.djs.plant.pick.domain.vo.PickActivityVo result = service.summary(10001L);

        // then
        ArgumentCaptor<PickActivity> captor = ArgumentCaptor.forClass(PickActivity.class);
        verify(baseMapper, times(1)).updateById(captor.capture());
        PickActivity updated = captor.getValue();
        assertThat(updated.getTotalYield()).isEqualByComparingTo("38.5");
        assertThat(updated.getActivityStatus()).isEqualTo("ended");
        assertThat(result.getActivityStatus()).isEqualTo("ended");
    }
}
