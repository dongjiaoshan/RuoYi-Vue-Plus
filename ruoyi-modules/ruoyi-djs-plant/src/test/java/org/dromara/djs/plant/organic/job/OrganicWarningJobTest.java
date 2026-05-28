package org.dromara.djs.plant.organic.job;

import org.dromara.common.core.service.ConfigService;
import org.dromara.djs.plant.organic.mapper.CropOrganicMapper;
import org.dromara.djs.plant.organic.mapper.PlotOrganicMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrganicWarningJob} 单测（PLT-MD-003）。
 *
 * @author djs
 * @since PLT-MD-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrganicWarningJob 单元测试")
class OrganicWarningJobTest {

    @Mock
    private PlotOrganicMapper plotOrganicMapper;

    @Mock
    private CropOrganicMapper cropOrganicMapper;

    @Mock
    private ConfigService configService;

    @InjectMocks
    private OrganicWarningJob job;

    @Test
    @DisplayName("scanWarning: 读 sys_config 阈值 + 双表各 markWarning 一次")
    void testScanWarning_HappyPath() {
        when(configService.getConfigInt(OrganicWarningJob.CONFIG_KEY_WARNING_DAYS)).thenReturn(60);
        when(plotOrganicMapper.markWarning(60)).thenReturn(3);
        when(cropOrganicMapper.markWarning(60)).thenReturn(2);

        job.scanWarning();

        verify(plotOrganicMapper, times(1)).markWarning(60);
        verify(cropOrganicMapper, times(1)).markWarning(60);
    }

    @Test
    @DisplayName("scanWarning: sys_config 读取异常 → 降级到 60 天默认值")
    void testScanWarning_ConfigMissing_Fallback() {
        when(configService.getConfigInt(OrganicWarningJob.CONFIG_KEY_WARNING_DAYS))
            .thenThrow(new RuntimeException("redis down"));

        job.scanWarning();

        // 仍然按 fallback=60 跑
        verify(plotOrganicMapper, times(1)).markWarning(60);
        verify(cropOrganicMapper, times(1)).markWarning(60);
    }

    @Test
    @DisplayName("scanWarning: sys_config 值非法（null）→ 降级 60")
    void testScanWarning_ConfigNull_Fallback() {
        when(configService.getConfigInt(OrganicWarningJob.CONFIG_KEY_WARNING_DAYS)).thenReturn(null);

        job.scanWarning();

        verify(plotOrganicMapper, times(1)).markWarning(60);
    }
}
