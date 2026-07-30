package com.fundpilot.backend.productcatalog.domain.product;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProductClassifier {
    public static final String ACTIVE_DEFAULT_BENCHMARK = "000300.SH";
    private static final Map<String, String> BENCHMARKS = benchmarks();
    private static final Set<String> SECTORS = Set.of(
            "半导体", "芯片", "医药", "医疗", "生物", "创新药", "中药", "新能源", "光伏",
            "锂电", "电池", "新能源车", "汽车", "消费", "食品", "白酒", "饮料", "家电",
            "军工", "国防", "航天", "银行", "证券", "金融", "保险", "地产", "房地产",
            "建材", "科技", "人工智能", "AI", "云计算", "5G", "通信", "传媒", "游戏",
            "有色", "钢铁", "煤炭", "化工", "石油", "环保", "电力", "基建", "机械", "制造");
    private static final Set<String> MIXED = Set.of("混合", "灵活配置", "平衡", "稳健", "配置");

    private ProductClassifier() {}

    public static ProductClassification classify(String fundName) {
        String name = fundName == null ? "" : fundName;
        Optional<String> benchmark = lookupBenchmark(name);
        ProductType type;
        if (name.contains("ETF")) type = ProductType.ETF;
        else if (name.contains("指数增强") || name.contains("增强")) type = ProductType.INDEX_ENHANCED;
        else if (name.contains("指数") || benchmark.isPresent()) type = ProductType.INDEX;
        else type = ProductType.ACTIVE;

        DefaultDisciplineCategory category;
        if (type == ProductType.ACTIVE) {
            category = MIXED.stream().anyMatch(name::contains)
                    ? DefaultDisciplineCategory.MIXED : DefaultDisciplineCategory.ACTIVE;
        } else if (benchmark.isPresent()) {
            category = DefaultDisciplineCategory.BROAD_BASE;
        } else {
            category = SECTORS.stream().anyMatch(name::contains)
                    ? DefaultDisciplineCategory.SECTOR : DefaultDisciplineCategory.BROAD_BASE;
        }
        return new ProductClassification(type, category,
                benchmark.orElse(type == ProductType.ACTIVE ? ACTIVE_DEFAULT_BENCHMARK : null));
    }

    private static Optional<String> lookupBenchmark(String fundName) {
        return BENCHMARKS.entrySet().stream().filter(entry -> fundName.contains(entry.getKey()))
                .map(Map.Entry::getValue).findFirst();
    }

    private static Map<String, String> benchmarks() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("中证1000", "000852.SH"); values.put("中证500", "000905.SH");
        values.put("沪深300", "000300.SH"); values.put("科创创业50", "931643.CSI");
        values.put("科创50", "000688.SH"); values.put("上证50", "000016.SH");
        values.put("创业板", "399006.SZ"); values.put("中证机器人", "H30590.CSI");
        values.put("机器人", "H30590.CSI"); values.put("中证5G通信主题", "931079.CSI");
        values.put("5G通信", "931079.CSI"); values.put("细分有色金属", "000811.CSI");
        values.put("有色金属", "000811.CSI"); values.put("人工智能", "930713.CSI");
        values.put("半导体", "931865.CSI"); values.put("新能源车", "930997.CSI");
        values.put("中证新能源", "399808.SZ"); values.put("新能源", "399808.SZ");
        values.put("国证绿色电力", "399438.SZ"); values.put("绿色电力", "399438.SZ");
        values.put("光伏", "931151.CSI"); values.put("白酒", "930622.CSI");
        values.put("家电", "930697.CSI"); values.put("旅游", "930633.CSI");
        return values;
    }
}
