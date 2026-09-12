# 源码阅读和扩展指南

本指南按用户动作说明代码入口，并给出添加日志格式、字段和处理器的完整例子。示例用于说明现有接口，提交代码时仍需补充回归。

## 从哪里开始读

1. `FlightPlot.main` 设置 HiDPI、Look and Feel、语言和主窗口。
2. `FlightPlot.openLogAsync` 接收全部打开方式的路径，创建可取消后台任务。
3. `LogService.open` 选择 `PX4LogReaderOptimized`、`DataFlashTextLogReader` 或 `ULogReader`。
4. `Workspace` 接收完成的 reader 并切换当前 `ChartTab`。
5. `FieldsPanel.setFieldsList` 根据 `LogReader.getFields()` 建立动态字段树。
6. `FlightPlot.processFile` 根据已勾选处理器计算可见范围，更新 `ChartTab`。

## 日志 reader 契约

所有 reader 实现 `me.drton.jmavlib.log.LogReader`。界面依赖以下行为：

- `getFields()` 返回原生字段键和可选说明。
- `getParameters()`、`getVersion()`、`getStartMicroseconds()` 提供元数据。
- `seek(long)` 定位到微秒时间；大文件不能每次从头扫描。
- `readUpdate(Map<String,Object>)` 填充一条时间更新的数据。
- `setNeededFields(Set<String>)` 接受当前绘图需要的字段；实现应跳过无关值。
- `close()` 可重复调用并释放文件句柄。

损坏文件可以抛出 `FormatErrorException` 或 `IOException`。正常的截断尾部可以保留前面已解析的数据，但必须向 `LogService` 返回可显示的解析提示。

## 新增一种日志格式

假设新增扩展名 `.demo`：

1. 在 `jMAVlib/src/me/drton/jmavlib/log/` 创建 `DemoLogReader` 并实现 `LogReader`。
2. 在 `LogService.supports` 加入扩展名，在 `LogService.open` 增加一次格式选择。
3. reader 从文件 schema 构造字段 map，不要在 `FieldsPanel` 写死字段名。
4. 给 `FormatRegression` 加入最小合法、截断、空文件和错误魔数样例。
5. 给 `FieldCatalogRegression` 验证消息实例、数组字段和新 schema 字段。

示意代码：

```java
final class DemoLogReader implements LogReader {
    private final Map<String, String> fields = new LinkedHashMap<String, String>();

    DemoLogReader(String path) throws IOException, FormatErrorException {
        // 只解析文件头和索引；不要在构造函数中把全部采样装入内存。
        readHeaderAndBuildIndex(path, fields);
    }

    @Override
    public Map<String, String> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    @Override
    public void setNeededFields(Set<String> names) {
        // 保存副本，读取记录时只解码这些字段。
    }

    @Override
    public long readUpdate(Map<String, Object> update)
            throws IOException, FormatErrorException {
        // 返回当前记录时间，EOF 时遵守 LogReader 的既有约定。
        return 0L;
    }
}
```

## 新增处理器

处理器位于 `src/me/drton/flightplot/processors/`，继承 `PlotProcessor`。一个处理器负责声明参数、从输入 map 取值并输出 `Series` 或标记。

```java
public final class Gain extends PlotProcessor {
    public Gain() {
        addParam("field", "", true);
        addParam("gain", 1.0);
    }

    @Override
    public void process(double time, Map<String, Object> update) {
        Object raw = update.get(getParamValue("field"));
        if (raw instanceof Number) {
            addPoint(0, time, ((Number) raw).doubleValue()
                    * ((Number) getParamValue("gain")).doubleValue());
        }
    }
}
```

实现后还需：

1. 在 `ProcessorsList` 的显式工厂和显示顺序中登记类型。
2. 在 `I18n` 中加入处理器名称、说明和参数文案。
3. 在 `ProductRegression` 验证实例化、参数 JSON 往返和错误输入。
4. 如处理器需要字段，必须通过参数声明，让 `setNeededFields` 能够限制日志读取。

## 动态字段与本地化

字段键由 reader 决定，例如 `ATT.Roll`、`RCIN.C10`、`vehicle_attitude_0.q[2]`。`FieldNameLocalizer` 和 `ParameterNameLocalizer` 只生成显示说明。不要用翻译后的文字作为处理器参数或查找键。

自然排序使用 `NaturalFieldOrder.INSTANCE`：连续数字按数值比较，所以 `C2` 排在 `C10` 前；其余字符保持稳定的字典顺序。

## 同格式日志选择继承

`ProcessorSelectionMemory` 以 reader 的格式签名保存快照：

- 处理器类型、标题和参数；
- 每一行的勾选状态；
- 自定义颜色。

恢复时先检查新日志字段集合。Simple 处理器引用的字段不存在时跳过，存在时恢复。不同格式签名不共享快照，避免把 ULog 字段误套到 DataFlash。

添加新的字段参数时，应同步更新处理器的字段引用枚举逻辑，确保恢复和缺字段裁剪能识别它。

## 对话框规则

不要直接在按钮监听器中反复 `new JDialog().setVisible(true)`。应以稳定键调用 `DialogCoordinator.claim`；成功后创建窗口并 `register`，关闭时由 coordinator 释放。重复点击时聚焦已有窗口。

```java
if (!dialogs.claim("field-color")) {
    dialogs.focus("field-color");
    return;
}
JDialog dialog = createColorDialog();
dialogs.register("field-color", dialog);
dialog.setVisible(true);
```

确认窗口也使用同一机制。空选择直接返回，不创建无意义对话框。

## 线程与取消

- Swing 组件只能在 EDT 访问。
- `LogService` 和 reader 的文件读取在后台线程运行。
- 长循环定期检查 `Thread.currentThread().isInterrupted()`。
- 后台结果提交前比较页标识和任务版本；过期结果只释放资源。
- 不在 EDT 调用 `readUpdate`、全文件字段扫描、轨迹导出或大图生成。

## 错误闭环

每个用户动作应同时定义成功、失败和取消：

- 成功：更新目标页与状态栏，释放忙碌状态。
- 失败：保留原页，关闭新 reader，显示一次可理解的错误。
- 取消：不弹错误，停止任务，恢复按钮并释放文件句柄。

若新增入口，先复用现有动作方法；不要复制格式判断、确认框或状态更新代码。
