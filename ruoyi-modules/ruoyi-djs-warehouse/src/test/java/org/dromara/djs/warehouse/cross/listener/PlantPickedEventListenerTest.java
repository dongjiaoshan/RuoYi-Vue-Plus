package org.dromara.djs.warehouse.cross.listener;

import org.dromara.djs.plant.pick.event.PlantPickedEvent;
import org.dromara.djs.plant.pick.event.PlantPickedPayload;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.mapper.PlantingRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlantPickedEventListener} 单测（CROSS-FLOW-002）。
 *
 * <h3>覆盖场景</h3>
 * <ol>
 *   <li>happy path：PlantPickedEvent 触发 → PlantingRecord.insert 调用 1 次 + 入参字段
 *       （plotId / cropId / plotName / cropName / harvestWeight / handle_status='pending' /
 *       is_loss=2 / data_date 非 null / product_id=null / plantDate / harvestDate）全对</li>
 *   <li>mapper INSERT 抛异常 → listener **不**重抛（AFTER_COMMIT 阶段抛异常会让上游误以为采摘失败）</li>
 * </ol>
 *
 * <p>用 Mockito mock {@link PlantingRecordMapper}，不需要 Spring 上下文。listener 只 insert(entity)，
 * 无 LambdaQueryWrapper method-ref，故无需 TableInfoHelper 预热。</p>
 *
 * @author djs
 * @since CROSS-FLOW-002
 */
@Tag("local")
@ExtendWith(MockitoExtension.class)
class PlantPickedEventListenerTest {

    @Mock
    private PlantingRecordMapper plantingRecordMapper;

    private PlantPickedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PlantPickedEventListener(plantingRecordMapper);
    }

    /**
     * 构造 happy path 采摘完成载荷。
     */
    private PlantPickedPayload buildPayload() {
        PlantPickedPayload p = new PlantPickedPayload();
        p.setPlotId(20L);
        p.setCropId(30L);
        p.setPlotName("A区1号地块");
        p.setCropName("有机白菜");
        p.setPlantDate(LocalDate.of(2026, 4, 1));
        p.setHarvestDate(LocalDate.of(2026, 6, 1));
        p.setHarvestWeight(new BigDecimal("125.500"));
        p.setTeamId(40L);
        p.setTeamName("采摘一组");
        return p;
    }

    @Test
    @DisplayName("happy path：触发事件后 PlantingRecord INSERT 一次，字段全对（pending / is_loss=2 / product_id=null）")
    void onPlantPicked_happyPath_insertsPlantingRecordWithCorrectFields() {
        PlantPickedPayload payload = buildPayload();
        PlantPickedEvent event = new PlantPickedEvent(this, payload);

        listener.onPlantPicked(event);

        ArgumentCaptor<PlantingRecord> captor = ArgumentCaptor.forClass(PlantingRecord.class);
        verify(plantingRecordMapper, times(1)).insert(captor.capture());
        PlantingRecord record = captor.getValue();

        assertThat(record.getPlotId()).isEqualTo(20L);
        assertThat(record.getCropId()).isEqualTo(30L);
        assertThat(record.getPlotName()).isEqualTo("A区1号地块");
        assertThat(record.getCropName()).isEqualTo("有机白菜");
        assertThat(record.getHarvestWeight()).isEqualByComparingTo(new BigDecimal("125.500"));
        assertThat(record.getTeamId()).isEqualTo(40L);
        assertThat(record.getTeamName()).isEqualTo("采摘一组");
        assertThat(record.getHandleStatus()).isEqualTo("pending");
        assertThat(record.getIsLoss()).isEqualTo(2);
        assertThat(record.getDataDate()).isNotNull();
        assertThat(record.getProductId()).isNull();   // V1 无 crop→product 映射
        assertThat(record.getPlantDate()).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 4, 1)));
        assertThat(record.getHarvestDate()).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 6, 1)));
    }

    @Test
    @DisplayName("plantDate 为 null → toSqlDate 返 null，不 NPE，仍正常 INSERT")
    void onPlantPicked_nullDates_insertsWithNullSqlDates() {
        PlantPickedPayload payload = buildPayload();
        payload.setPlantDate(null);
        payload.setHarvestDate(null);
        PlantPickedEvent event = new PlantPickedEvent(this, payload);

        listener.onPlantPicked(event);

        ArgumentCaptor<PlantingRecord> captor = ArgumentCaptor.forClass(PlantingRecord.class);
        verify(plantingRecordMapper, times(1)).insert(captor.capture());
        PlantingRecord record = captor.getValue();
        assertThat(record.getPlantDate()).isNull();
        assertThat(record.getHarvestDate()).isNull();
        assertThat(record.getHandleStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("INSERT 抛异常 → listener 吞掉异常（AFTER_COMMIT 阶段不能影响上游采摘事务）")
    void onPlantPicked_mapperThrows_listenerSwallowsException() {
        PlantPickedPayload payload = buildPayload();
        PlantPickedEvent event = new PlantPickedEvent(this, payload);
        when(plantingRecordMapper.insert(any(PlantingRecord.class)))
            .thenThrow(new DataAccessResourceFailureException("db down"));

        // AFTER_COMMIT 阶段抛异常无意义（采摘事务已提交），listener 必须 swallow
        assertThatCode(() -> listener.onPlantPicked(event)).doesNotThrowAnyException();
        verify(plantingRecordMapper, times(1)).insert(any(PlantingRecord.class));
    }
}
