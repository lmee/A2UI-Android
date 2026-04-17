# A2UI Android Compose 协议合规修复计划

## 目标

使 Android Compose 渲染器完全兼容 A2UI v0.9 协议规范，同时保持对 v0.8 的基本兼容。

---

## Phase 1: 消息模型与数据结构修复（高优先级）

### 1.1 修复 Component 数据模型

**问题**: `Component` 类的属性名与 v0.9 规范不匹配。

**修改文件**: `data/A2UIMessage.kt`

- Modal: 添加 `trigger` 和 `content` 属性（对应 v0.9 的 `trigger`/`content`）
- Tabs: 添加 `tabs` 属性（v0.9 用 `tabs` 而非 `tabItems`/`options`）
- Icon: 将图标名从 `text` 改为 `name` 属性
- Button: 确保通过 `child` 引用子组件渲染，而非直接用 `text`
- Image: 添加 `fit` 和 `variant` 属性
- ChoicePicker: 添加 `displayStyle` 和 `filterable` 属性
- TextField: 添加 `validationRegexp` 属性
- DateTimeInput: 添加 `enableDate`/`enableTime`/`min`/`max` 属性
- List: 添加 `direction` 属性

### 1.2 修复 ChildList 序列化

**问题**: v0.9 的 `children` 可以是字符串数组（直接）或 template 对象。

**修改文件**: `data/A2UIMessage.kt`

- `ChildList` 需要支持直接反序列化 JSON 数组为 `ArrayChildList`
- template 对象的 `dataBinding` (v0.8) / `path` (v0.9) 都需要支持

### 1.3 修复 DynamicValue 序列化

**问题**: v0.9 的 `DynamicString` 可以是纯字符串字面量、`{path: "..."}` 对象、或 `FunctionCall` 对象。

**修改文件**: `data/A2UIMessage.kt`

- 支持纯字符串直接作为 `LiteralValue`（v0.9 简化格式）
- 支持 `{path: "/user/name"}` 对象格式
- 支持 `{call: "formatString", args: {...}}` 函数调用格式

---

## Phase 2: 组件渲染修复（高优先级）

### 2.1 Button 子组件引用

**修改文件**: `rendering/ComponentRegistry.kt`

- Button 应通过 `child` 属性引用子组件渲染，而非直接读取 `text`
- 保留 `text` 作为 fallback（向后兼容）

### 2.2 Modal 属性修复

- 使用 `trigger`（v0.9）/ `entryPointChild`（v0.8）作为触发器组件
- 使用 `content`（v0.9）/ `contentChild`（v0.8）作为内容组件

### 2.3 Tabs 属性修复

- 从 `tabs` 数组读取标签页定义（v0.9）
- 每个 tab 有 `title`（DynamicString）和 `child`（ComponentId）

### 2.4 Icon 属性修复

- 从 `name` 属性读取图标名
- 补全 v0.9 标准图标列表（约 55 个图标，使用 camelCase）

### 2.5 Image 增强

- 支持 `fit` 属性（contain/cover/fill/none/scale-down）
- 支持 `variant` 属性（icon/avatar/smallFeature/mediumFeature/largeFeature/header）

### 2.6 Divider 方向支持

- 支持 `axis: "vertical"` 渲染垂直分隔线

---

## Phase 3: 模板作用域与数据绑定（高优先级）

### 3.1 Collection Scope 实现

**修改文件**: `rendering/ComponentRegistry.kt`, `rendering/A2UIRenderer.kt`

- `SurfaceContext` 添加 `scopePath: String?` 字段
- 模板渲染时，为每个 item 创建带有 `scopePath = "/users/0"` 的子上下文
- `resolveValue` 中，相对路径（不以 `/` 开头）在 scopePath 下解析

### 3.2 BoundValue 初始化简写（v0.8 兼容）

- 当 `path` 和 `literalString` 同时存在时，先用 literal 初始化 data model

---

## Phase 4: 缺失函数实现（中优先级）

### 4.1 新增函数

**修改文件**: `data/DataModelProcessor.kt`

| 函数 | 描述 |
|---|---|
| `formatNumber` | 数字格式化（分组、小数位） |
| `formatCurrency` | 货币格式化 |
| `formatDate` | 日期格式化（Unicode TR35 模式） |
| `pluralize` | 基于 CLDR 复数规则的字符串选择 |
| `openUrl` | 打开 URL（已在 handleLocalFunction 中部分实现） |
| `not` | 逻辑非 |

### 4.2 增强 formatString

- 支持 `${/path}` 内联路径插值
- 支持 `${functionName(args)}` 内联函数调用
- 支持 `\${` 转义

---

## Phase 5: 协议兼容层（低优先级）

### 5.1 v0.8 beginRendering 支持

- 添加 `BeginRenderingMessage` 消息类型
- 实现渲染就绪信号机制（收到 beginRendering 前缓冲组件）

### 5.2 v0.8 dataModelUpdate 邻接表格式

- 支持 `contents` 数组格式（`key` + `valueString`/`valueNumber`/`valueBoolean`/`valueMap`）

### 5.3 Catalog 协商

- `SurfaceContext` 已有 `catalogId`，添加 `a2uiClientCapabilities` 支持

---

## 实施顺序

1. Phase 1 + Phase 2（消息模型 + 组件渲染）→ 核心协议合规
2. Phase 3（模板作用域）→ 动态列表正确渲染
3. Phase 4（函数实现）→ 完整的客户端逻辑
4. Phase 5（兼容层）→ 多版本支持
