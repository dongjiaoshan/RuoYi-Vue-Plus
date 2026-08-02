package org.dromara.djs.plant.activity.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.plant.activity.domain.PlantActivity;
import org.dromara.djs.plant.activity.domain.bo.PickActivityRecordBo;
import org.dromara.djs.plant.activity.mapper.PlantActivityMapper;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采摘活动 Service 单测（DENGBO-R4 采摘去向）：recordPickActivity 销售/非销售 happy path。
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TestMethodOrder(MethodOrderer.MethodName.class)
class PlantActivityServiceImplTest {

    @Mock
    private PlantActivityMapper baseMapper;
    @Mock
    private CropInfoMapper cropInfoMapper;
    @Mock
    private PlantDetailsMapper plantDetailsMapper;
    @Mock
    private org.dromara.djs.plant.plot.mapper.PlotInfoMapper plotInfoMapper;
    @Mock
    private org.dromara.djs.plant.team.service.PlantTeamLinkService teamLinkService;

    private PlantActivityServiceImpl service;

    /**
     * MyBatis-Plus entity cache 预热：service 内 LambdaQueryWrapper/UpdateWrapper 在 mock 路径下
     * 也会触发 TableInfoHelper.getTableInfo() 解析列名，必须先注册 entity。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PlantActivity.class);
        TableInfoHelper.initTableInfo(assistant, PlantDetails.class);
    }

    @BeforeEach
    void setUp() {
        service = new PlantActivityServiceImpl(baseMapper, cropInfoMapper, plantDetailsMapper, plotInfoMapper, teamLinkService);
    }

    @Test
    @DisplayName("非销售去向：写 activity 行 + 累加所选地块产量")
    void recordPickActivity_nonSale_accumulatesPlotYield() {
        // crop.related_product 解析出 product_id
        CropInfo crop = new CropInfo();
        crop.setId(10L);
        crop.setRelatedProduct(200L);
        when(cropInfoMapper.selectById(10L)).thenReturn(crop);

        // 定位到一行 plant_details（actual_yield 现 5.0），先命中未结束种植查询
        PlantDetails detail = new PlantDetails();
        detail.setId(99L);
        detail.setActualYield(new BigDecimal("5.000"));
        when(plantDetailsMapper.selectOne(any())).thenReturn(detail);

        PickActivityRecordBo bo = new PickActivityRecordBo();
        bo.setCropId(10L);
        bo.setActivityDate(LocalDate.of(2026, 6, 30));
        bo.setPickWeight(new BigDecimal("3.000"));
        bo.setPickDest("veg_fresh");
        bo.setPlotId(7L);
        bo.setRecorderId(88L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(88L);

            ArgumentCaptor<PlantActivity> insertCap = ArgumentCaptor.forClass(PlantActivity.class);
            service.recordPickActivity(bo);

            // 1. 插入 per-event 行，去向/地块/产品/记录人正确
            verify(baseMapper).insert(insertCap.capture());
            PlantActivity inserted = insertCap.getValue();
            assertEquals("veg_fresh", inserted.getPickDest());
            assertEquals(7L, inserted.getPlotId());
            assertEquals(200L, inserted.getProductId());
            assertEquals(88L, inserted.getRecorderId());
            assertEquals(new BigDecimal("3.000"), inserted.getPickWeight());
            assertEquals(new BigDecimal("3.000"), inserted.getDailyWeight());

            // 2. 累加地块产量 5.0 + 3.0 = 8.0（走 update）
            verify(plantDetailsMapper, times(1)).update(any(), any());
        }
    }

    @Test
    @DisplayName("销售去向：写 activity 行 plot_id 留空，不累加地块产量")
    void recordPickActivity_sale_noPlotYield() {
        CropInfo crop = new CropInfo();
        crop.setId(10L);
        crop.setRelatedProduct(null);
        when(cropInfoMapper.selectById(10L)).thenReturn(crop);

        PickActivityRecordBo bo = new PickActivityRecordBo();
        bo.setCropId(10L);
        bo.setActivityDate(LocalDate.of(2026, 6, 30));
        bo.setPickWeight(new BigDecimal("4.000"));
        bo.setPickDest("sale");
        bo.setPlotId(null);
        bo.setRecorderId(88L);

        ArgumentCaptor<PlantActivity> insertCap = ArgumentCaptor.forClass(PlantActivity.class);
        service.recordPickActivity(bo);

        verify(baseMapper).insert(insertCap.capture());
        PlantActivity inserted = insertCap.getValue();
        assertEquals("sale", inserted.getPickDest());
        assertNull(inserted.getPlotId());
        // 销售去向不分摊地块产量
        verify(plantDetailsMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("非销售去向缺地块 → 抛异常")
    void recordPickActivity_nonSale_missingPlot_throws() {
        PickActivityRecordBo bo = new PickActivityRecordBo();
        bo.setCropId(10L);
        bo.setActivityDate(LocalDate.of(2026, 6, 30));
        bo.setPickWeight(new BigDecimal("3.000"));
        bo.setPickDest("loss");
        bo.setPlotId(null);

        assertThrows(ServiceException.class, () -> service.recordPickActivity(bo));
        verify(baseMapper, never()).insert(any(PlantActivity.class));
    }

}
