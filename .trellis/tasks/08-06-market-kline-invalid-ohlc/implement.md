# 执行计划

1. 在 `CsindexJsParserTest` 增加混合响应回归用例：一行 `open/high/low=0`，一行合法 OHLCV，断言只保留合法行且成交量仍按现有规则换算。
2. 运行该测试确认新增用例在修复前失败。
3. 在 `CsindexJsParser` 增加最小正 OHLC 校验；不改数据源链、落库和前端。
4. 运行解析器测试、相关 market 测试和必要的 backend 构建；检查无调试日志、无依赖变更。
5. 记录生产只读核对结果；发布后再按单独确认执行备份、历史数据修复和页面验证。

验证命令：

```powershell
cd backend
mvn -B '-Dtest=CsindexJsParserTest' test
mvn -B test
```

回滚点：代码回滚到修复前版本；生产数据修复仅允许在备份成功后执行，并以备份恢复作为数据回滚路径。
