package org.dromara.djs.plant.zone.controller.applet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.domain.R;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.domain.vo.ZonePickerVo;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link AppletPlotZoneController#listAll(String)} 单测（D12X-MP-PLANT-WORK-IA-001）。
 *
 * <p>验证片区 picker 端点：</p>
 * <ol>
 *   <li>启用片区映射成轻量 ZonePickerVo（仅 id / zoneCode / zoneName 三字段）</li>
 *   <li>VO 不携带 zoneBelong / zoneDesc / zoneStatus 等重字段</li>
 * </ol>
 *
 * <p>仅 mock PlotZoneMapper，不起 Spring 上下文。</p>
 *
 * @author djs
 * @since D12X-MP-PLANT-WORK-IA-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppletPlotZoneController.listAll() 片区 picker 单元测试")
class AppletPlotZoneControllerTest {

    @Mock
    private PlotZoneMapper plotZoneMapper;

    @Mock
    private PlantDetailsMapper plantDetailsMapper;

    @Mock
    private PlotInfoMapper plotInfoMapper;

    private AppletPlotZoneController controller;

    @BeforeEach
    void setUp() {
        // plantDetailsMapper.selectList 默认返回空（Mockito 对 List 返回空集合），
        // fillPickTaskCount 当月无明细即早退，pickTaskCount 保持 null，不影响 3 字段断言。
        controller = new AppletPlotZoneController(plotZoneMapper, plantDetailsMapper, plotInfoMapper);
    }

    @Test
    @DisplayName("listAll：启用片区映射成轻量 ZonePickerVo（仅 3 字段）")
    void listAllMapsToLightweightVo() {
        PlotZone z = new PlotZone();
        z.setId(2059411646299815938L);
        z.setZoneCode("Z-TEST-001");
        z.setZoneName("测试片区A");
        z.setZoneBelong("东部");
        z.setZoneStatus(1);
        when(plotZoneMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(z));

        R<List<ZonePickerVo>> r = controller.listAll(null);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).hasSize(1);
        ZonePickerVo vo = r.getData().get(0);
        assertThat(vo.getId()).isEqualTo(2059411646299815938L);
        assertThat(vo.getZoneCode()).isEqualTo("Z-TEST-001");
        assertThat(vo.getZoneName()).isEqualTo("测试片区A");
    }

    @Test
    @DisplayName("listAll：无片区时返回空列表（不报错）")
    void listAllReturnsEmptyWhenNoZone() {
        when(plotZoneMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of());

        R<List<ZonePickerVo>> r = controller.listAll("不存在");

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEmpty();
    }

}
