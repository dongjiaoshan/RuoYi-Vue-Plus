package org.dromara.djs.store.demand.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreDemandViewEnricher} 直测。
 *
 * <p>为什么必须单独测：这个类是 admin 需求列表与 mp 按天明细<b>共用的唯一口径</b>，
 * 而两个调用方的单测都把它 {@code @Mock} 掉了 —— 不直测的话，把 belongType 分支写反、
 * 或把「只对已发货行回填计损量」写成全量回填，整个后端 128 个用例一个都不会红。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreDemandViewEnricherTest {

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private ProductProductionMapper productProductionMapper;

    @Mock
    private IProductProductionService productProductionService;

    @InjectMocks
    private StoreDemandViewEnricher enricher;

    private static DemandManageVo row(long id, long productId, String storeStatus) {
        DemandManageVo vo = new DemandManageVo();
        vo.setId(id);
        vo.setProductId(productId);
        vo.setStoreDemandStatus(storeStatus);
        return vo;
    }

    private static ProductInfo product(long id, String belongType) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setBelongType(belongType);
        return p;
    }

    /**
     * 预热 MP 的 lambda 列缓存。
     *
     * <p>不加这段，本类**单独跑**（`-Dtest=StoreDemandViewEnricherTest` / 按类跑 / 换 runOrder）会 6/7 报
     * `can not find lambda cache for this entity [ProductInfo]` —— 全量跑之所以绿，是同模块另外 5 个测试类
     * 先在同一个 JVM 里初始化过这份静态缓存（surefire 默认 forkCount=1 + reuseForks=true）。
     * 那是顺序依赖不是自足，同 skill `coder-mp-entity-cache-test`。</p>
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    private void stubProducts(ProductInfo... ps) {
        lenient().when(productInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ps));
    }

    // ─────────────────────────── 计损量 ───────────────────────────

    @Test
    @DisplayName("计损量：只对门店态 已发货 / 已到店 的行回填，其余行保持 null（前端显示 --）")
    void damagedCount_onlyShippedOrArrived() {
        stubProducts(product(9001L, "vegetable"));
        when(productProductionService.countDamagedByDemand(anyLong())).thenReturn(3L);

        DemandManageVo submitted = row(1L, 9001L, "SUBMITTED");
        DemandManageVo confirmed = row(2L, 9001L, "CONFIRMED");
        DemandManageVo shipped = row(3L, 9001L, "SHIPPED");
        DemandManageVo arrived = row(4L, 9001L, "ARRIVED");
        enricher.enrich(List.of(submitted, confirmed, shipped, arrived));

        assertThat(submitted.getDamagedCount()).isNull();
        assertThat(confirmed.getDamagedCount()).isNull();
        assertThat(shipped.getDamagedCount()).isEqualTo(3);
        assertThat(arrived.getDamagedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("计损量：查到 0 就写 0（「查过了、没损坏」不能退化成 null）")
    void damagedCount_zeroIsNotNull() {
        stubProducts(product(9001L, "vegetable"));
        when(productProductionService.countDamagedByDemand(anyLong())).thenReturn(0L);

        DemandManageVo shipped = row(3L, 9001L, "SHIPPED");
        enricher.enrich(List.of(shipped));

        assertThat(shipped.getDamagedCount()).isZero();
    }

    // ─────────────────────────── 预计到店量：belongType 分流 ───────────────────────────

    @Test
    @DisplayName("预计到店量：白条走 sumShippedWhiteBar，果蔬/猪肉走 sumShippedVegPork，干货走 sumShippedDryGood —— 三条互不串线")
    void expectedWeight_routesByBelongType() {
        stubProducts(product(1L, "white_bar"), product(2L, "vegetable"),
            product(3L, "pork"), product(4L, "dry_good"));
        when(productProductionMapper.sumShippedWhiteBarWeightByDemand(anyLong())).thenReturn(new BigDecimal("19.000"));
        when(productProductionMapper.sumShippedVegPorkWeightByDemand(anyLong())).thenReturn(new BigDecimal("0.250"));
        when(productProductionMapper.sumShippedDryGoodWeightByDemand(anyLong())).thenReturn(new BigDecimal("0.080"));

        DemandManageVo bar = row(10L, 1L, "ARRIVED");
        DemandManageVo veg = row(11L, 2L, "ARRIVED");
        DemandManageVo pork = row(12L, 3L, "ARRIVED");
        DemandManageVo dry = row(13L, 4L, "ARRIVED");
        enricher.enrich(List.of(bar, veg, pork, dry));

        assertThat(bar.getExpectedWeight()).isEqualByComparingTo("19.000");
        assertThat(veg.getExpectedWeight()).isEqualByComparingTo("0.250");
        assertThat(pork.getExpectedWeight()).isEqualByComparingTo("0.250");
        assertThat(dry.getExpectedWeight()).isEqualByComparingTo("0.080");
    }

    @Test
    @DisplayName("预计到店量：礼盒 / 鸡蛋 / 未知归属不回填，且一次称重查询都不发")
    void expectedWeight_unsupportedBelongTypeStaysNull() {
        stubProducts(product(5L, "gift_box"), product(6L, "egg"), product(7L, null));

        DemandManageVo gift = row(20L, 5L, "ARRIVED");
        DemandManageVo egg = row(21L, 6L, "ARRIVED");
        DemandManageVo unknown = row(22L, 7L, "ARRIVED");
        enricher.enrich(List.of(gift, egg, unknown));

        assertThat(gift.getExpectedWeight()).isNull();
        assertThat(egg.getExpectedWeight()).isNull();
        assertThat(unknown.getExpectedWeight()).isNull();
        verify(productProductionMapper, never()).sumShippedWhiteBarWeightByDemand(anyLong());
        verify(productProductionMapper, never()).sumShippedVegPorkWeightByDemand(anyLong());
        verify(productProductionMapper, never()).sumShippedDryGoodWeightByDemand(anyLong());
    }

    @Test
    @DisplayName("预计到店量：还没发货（合计 0 / null）不回填 —— 0 kg 与「没这个数」必须分开")
    void expectedWeight_zeroOrNullNotBackfilled() {
        stubProducts(product(1L, "white_bar"), product(2L, "vegetable"));
        when(productProductionMapper.sumShippedWhiteBarWeightByDemand(anyLong())).thenReturn(BigDecimal.ZERO);
        when(productProductionMapper.sumShippedVegPorkWeightByDemand(anyLong())).thenReturn(null);

        DemandManageVo bar = row(30L, 1L, "CONFIRMED");
        DemandManageVo veg = row(31L, 2L, "CONFIRMED");
        enricher.enrich(List.of(bar, veg));

        assertThat(bar.getExpectedWeight()).isNull();
        assertThat(veg.getExpectedWeight()).isNull();
    }

    @Test
    @DisplayName("预计到店量：库里已有落库值就不覆盖（expected_weight 是真实列，尊重已写入的值）")
    void expectedWeight_existingValueWins() {
        stubProducts(product(1L, "white_bar"));

        DemandManageVo bar = row(40L, 1L, "ARRIVED");
        bar.setExpectedWeight(new BigDecimal("7.500"));
        enricher.enrich(List.of(bar));

        assertThat(bar.getExpectedWeight()).isEqualByComparingTo("7.500");
        verify(productProductionMapper, never()).sumShippedWhiteBarWeightByDemand(anyLong());
    }

    // ─────────────────────────── 边界 ───────────────────────────

    @Test
    @DisplayName("空入参 / null 一律安静返回，不打库")
    void emptyInputIsNoop() {
        enricher.enrich(null);
        enricher.enrich(List.of());
        verify(productInfoMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(productProductionService, never()).countDamagedByDemand(anyLong());
    }
}
