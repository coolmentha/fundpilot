# 寻优择优：train top-k 提名 + test Calmar 择优

`OptimizeParamRanker` 原只返 train 集风险调整收益（Calmar）最高的**一组**参数（单点冠军），test 集仅做 pass/fail 门槛验证。
本次变更：train 集返 Calmar 前 **k=5** 名（top-k 提名），每组都送 test 集跑验证，在 passed 候选中按 **test Calmar** 择优落库。
test 集从"布尔门槛"升级为"连续择优标尺"。

## Considered Options

- **A. train top-k → test passed 过滤 → test Calmar 择优（已采纳）**：train 提名 5 组，test 集在达标候选中选 Calmar 最高的落库。
- **B. 保持单点冠军 + test 门槛**：现状。train 冠军可能是噪声产物,test 只验证不择优。
- **C. train top-k → test 全部要 passed → 取 train 排名最高**：太严,k 个全过门槛概率低,退化成现状。
- **D. 多切分 + minimax**：3 折前向滚动,每组参数取最差折 Calmar。抗过拟合更强,但改动大、且单段噪声可能主导 minimax。

## Consequences

选 A 的核心理由：**test 集从"门槛"变"择优",降低选到 train 过拟合参数的概率**。

1. **train 单点冠军的偶然性**——train Calmar 第一名可能是 train 集噪声的产物,第二名可能只差 0.01 但 test 稳健。
   单点选择完全忽略第二名。top-k 给 test 集多个候选,train 冠军 test 翻车时能选到 test 稳健的次优候选。
2. **passed 仍是硬门槛**——top-k 候选须先 passed(收益 > hs300 ∧ > dca ∧ Calmar ≥ dca)才进入择优。
   不会选出"Calmar 高但收益输 dca"的诡异参数(高 Calmar 可能来自极低回撤但收益也低)。
3. **test Calmar 择优与 train 排序同源**——train 用 Calmar 排序提名,test 用 Calmar 择优落库,标尺一致。
   零回撤(null, +∞)在 test 择优时优先选。

## 代价

**test 集独立性打折**——test 集参与选参数(从 5 组里择优),不再是纯粹的样本外验证,有循环论证嫌疑。
但相比单点选择,top-k 的过拟合风险低得多:用 Calmar(稳健指标)而非纯收益择优,且 passed 门槛已过滤明显不达标者。
实践可接受。

**未验证的假设**——"top-k 比单点更好"取决于过拟合参数在 grid 里的占比。若 grid 本身保守(以默认值为中心 ±3pp 小扰动),
过拟合空间有限,top-k 边际收益可能有限。落地后须在多基金上对比"单点会选谁 vs top-k 选谁"的落库 passed 率,
据此判断是否真有效(见 ADR-0010 的留痕原则:历史记录不重算,新机制只影响今后回测)。

## k 的取值

k=5:train 前 5 名有足够多样性(覆盖不同 tier1/tier4/stopLoss 组合),又不至于把 train 平庸参数送进 test 择优
(k 过大易选到 train 排名低但 test 偶然高的参数,另一种过拟合)。k 落为 `StrategyOptimizeService.TOP_K` 常量。

## 与多切分(D)的关系

D(多切分 minimax)治"单次切分方差",A(top-k)治"单点选择偶然性",两者互补不互斥。
本期先做 A(轻改、风险低),D 留待 A 效果验证后视需要叠加。
