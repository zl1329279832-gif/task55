# 订单-房态-库存一致性设计说明

> 本文档描述 `payOrder`、`cancelOrder`、`checkIn`、`checkOut` 四步操作对 `room_inventory` 表和 `room_type.rest` 字段的读写关系，以及并发控制策略和命名规范问题。
>
> **仅为设计说明文档，不涉及 Service 实现变更。**

---

## 1 数据模型

### 1.1 room_inventory 表（按房型-日期的细粒度库存）

| 列名 | 类型 | 说明 |
|---|---|---|
| `id` | INT PK | 自增主键 |
| `type_id` | INT | 关联 `room_type.type_id` |
| `inv_date` | DATE | 库存日期 |
| `total` | INT | 该房型当天总房间数（从 `room_info` 统计，懒初始化写入） |
| `ordered` | INT | 已付款待入住数量 |
| `occupied` | INT | 已入住数量 |
| `reserved` | INT | 操作员手动保留数量 |
| `maintenance` | INT | 维修锁房数量 |
| `price` | DECIMAL(10,2) | 当日价格覆盖，NULL 表示使用房型基础价 |
| `version` | INT | 乐观锁版本号 |

**唯一键**: `uk_type_date (type_id, inv_date)` — 每个房型每天有且仅有一行库存记录。

**可订量公式**（`RoomInventory.getAvailable()`）:

```
available = total - ordered - occupied - reserved - maintenance
```

> `available` 不是持久化列，而是由 Java 实体 getter 实时计算。SQL 层的写守卫条件直接内联计算该表达式。

### 1.2 room_type.rest 字段（房型级聚合余量）

`rest` 是 `room_type` 表上的一个整型字段，表示该房型**全局剩余可售数量**。它是一个非日期维度的聚合计数器，用于面向用户的快速筛选（如 `findAllRestType` 查询 `rest > 0` 的房型列表）。

### 1.3 双存储模型及其关系

| 存储 | 粒度 | 用途 |
|---|---|---|
| `room_inventory` | 房型 × 日期 | 精确的逐天库存核算，支持跨天入住、操作员保留/维修 |
| `room_type.rest` | 房型（无日期维度） | 用户侧快速查询"是否有空房" |

`rest` 是一个**冗余快照**，必须与 `room_inventory` 的写操作保持同步变更，否则会出现用户看到"有空房"但实际下单/入住时库存已满的不一致现象。

---

## 2 四步操作对库存的读写矩阵

下表描述每个业务操作对 `room_inventory` 各列和 `room_type.rest` 的影响。`R` = 读，`W+` = 加，`W-` = 减，`W±` = 原子减加转换，`-` = 不涉及。

### 2.1 room_inventory 列影响

| 操作 | 前置订单状态 | `ordered` | `occupied` | `reserved` | `maintenance` | `total` | `version` |
|---|---|---|---|---|---|---|---|
| **payOrder** | UNPAID→PAID | W+ (逐天 +1) | - | - | - | R (可订量校验) | R/W (乐观锁) |
| **cancelOrder** (从 PAID) | PAID→WAS_CANCELED | W- (逐天 -1) | - | - | - | - | R/W (乐观锁) |
| **cancelOrder** (从 UNPAID) | UNPAID→WAS_CANCELED | - | - | - | - | - | - |
| **checkIn** | PAID→CHECK_IN | W- (逐天 -1) | W+ (逐天 +1) | - | - | - | R/W (乐观锁) |
| **checkOut** | CHECK_IN (不变) | - | W- (逐天 -1) | - | - | - | R/W (乐观锁) |

### 2.2 room_type.rest 影响

| 操作 | 前置订单状态 | `rest` 变化 | 说明 |
|---|---|---|---|
| **payOrder** | UNPAID→PAID | **-1** | 付款即扣减余量 |
| **cancelOrder** (从 PAID) | PAID→WAS_CANCELED | **+1** | 释放已扣减的余量 |
| **cancelOrder** (从 UNPAID) | UNPAID→WAS_CANCELED | **不变** | 未付款的订单未占用 rest |
| **checkIn** | PAID→CHECK_IN | **不变** | rest 已在 payOrder 扣减，不重复扣减 |
| **checkOut** | CHECK_IN (不变) | **+1** | 客人离店，释放余量 |

### 2.3 rest 的完整生命周期守恒验证

一次完整入住流程：

```
payOrder:    rest -1
checkIn:     rest  0  (不变)
checkOut:    rest +1
─────────────────────
净变化:      0  ✓
```

付款后取消流程：

```
payOrder:    rest -1
cancelOrder: rest +1
─────────────────────
净变化:      0  ✓
```

### 2.4 调用链路详表

| 操作 | Service 方法 | Inventory 方法 | Mapper SQL | SQL 守卫条件 |
|---|---|---|---|---|
| payOrder | `OrderServiceImpl.payOrder` | `RoomInventoryService.occupyForOrder` → `optimisticIncrementOrdered` | `incrementOrdered` | `version = #{version} AND (total - ordered - occupied - reserved - maintenance) >= 1` |
| cancelOrder (PAID) | `OrderServiceImpl.cancelOrder` | `RoomInventoryService.releaseForCancel` → `retryingDecrementOrdered` | `decrementOrdered` | `version = #{version} AND ordered >= 1` |
| checkIn | `CheckInServiceImpl.checkIn` | `RoomInventoryService.occupyForCheckIn` | `convertOrderedToOccupied` | `version = #{version} AND ordered >= 1` |
| checkOut | `CheckInServiceImpl.checkOut` | `RoomInventoryService.releaseForCheckOut` | `decrementOccupied` | `version = #{version} AND occupied >= 1` |

---

## 3 库存槽位状态迁移：ordered → occupied → release

每个 `room_inventory` 行中的一个"库存单位"在业务流程中经历如下状态迁移：

```
                    cancelOrder (PAID)
                 ┌─────────────────────────┐
                 │   decrementOrdered       │
                 ▼                          │
  ┌──────────┐  payOrder   ┌──────────┐   │   checkIn    ┌──────────┐  checkOut  ┌──────────┐
  │ available ├───────────►│ ordered  ├───┘──────────────►│ occupied ├──────────►│ released │
  │ (可订)    │ increment  │ (待入住) │  convertOrdered  │ (已入住)  │ decrement │ (已释放) │
  └──────────┘  Ordered    └──────────┘  ToOccupied      └──────────┘  Occupied  └──────────┘
```

**各阶段对 available 的影响**：

| 阶段 | ordered | occupied | available 变化 | 说明 |
|---|---|---|---|---|
| payOrder（进入 ordered） | +1 | - | **-1** | 库存被预订占用，可订量减少 |
| checkIn（ordered→occupied） | -1 | +1 | **不变** | 只是从"预订占用"转为"入住占用"，总占用量不变 |
| checkOut（释放 occupied） | - | -1 | **+1** | 入住占用释放，可订量恢复 |
| cancelOrder（释放 ordered） | -1 | - | **+1** | 预订占用释放，可订量恢复 |

> **关键设计决策**：`checkIn` 是一个等价转换（ordered↔occupied），不消耗额外 available，因此不需要再次校验可订量，也不需要再次扣减 `rest`。这避免了"付款成功但入住时报无房"的问题。

### 3.1 跨天入住的逐天处理

对于 `orderDays > 1` 的订单，所有四个操作都**逐天迭代**处理：

```java
// 以 occupyForOrder 为例（payOrder 调用）
for (int i = 0; i < days; i++) {
    Date date = addDays(startDate, i);
    boolean success = optimisticIncrementOrdered(typeId, date);
    if (!success) {
        // 回滚已占用的天数
        for (int j = 0; j < i; j++) {
            retryingDecrementOrdered(typeId, addDays(startDate, j));
        }
        return -2;
    }
}
```

**注意**：`payOrder` 中如果某一天售罄，会**回滚已成功的前几天**，然后整体返回 -2（售罄），事务标记 rollback。而 `rest` 只有在所有天数都成功后才扣减，因此不会出现 rest 与 inventory 不一致的情况。

---

## 4 旧订单兼容分支（occupyForCheckIn）

### 4.1 问题背景

`room_inventory` 表是通过 `V1__add_room_inventory.sql` 后期引入的。在此之前创建的订单不会有对应的 inventory 行（payOrder 不写 inventory）。当这些"旧订单"需要入住时，`occupyForCheckIn` 不能因为找不到 inventory 行而失败。

### 4.2 兼容实现

```java
// RoomInventoryServiceImpl.occupyForCheckIn 核心逻辑
for (int i = 0; i < days; i++) {
    Date date = addDays(startDate, i);
    boolean success = false;
    for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
        RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
        if (inv == null || inv.getOrdered() == null || inv.getOrdered() < 1) {
            // ★ 兼容分支：无库存行或无 ordered 计数
            // 旧订单（系统上线前）优雅跳过
            success = true;
            break;
        }
        int affected = inventoryMapper.convertOrderedToOccupied(typeId, date, inv.getVersion());
        if (affected == 1) {
            success = true;
            break;
        }
    }
    if (!success) return -2;
}
```

**分支条件**：`inv == null || inv.getOrdered() == null || inv.getOrdered() < 1`

**语义**：

| 条件 | 含义 | 处理 |
|---|---|---|
| `inv == null` | 该房型-日期无 inventory 行（旧订单，payOrder 未写入） | 视为旧订单，跳过 |
| `inv.getOrdered() < 1` | inventory 行存在但 ordered = 0（旧订单的 payOrder 未 increment） | 视为旧订单，跳过 |

> **设计意图**：保证系统上线前已付款的旧订单能正常完成入住流程，无需数据迁移。跳过后 checkIn 仍然会分配物理房间、更新订单状态为 CHECK_IN，只是不执行 `convertOrderedToOccupied` 这一步。

### 4.3 releaseForCheckOut 的类似处理

`releaseForCheckOut` 中也有对称的兼容分支：

```java
if (inv == null || inv.getOccupied() == null || inv.getOccupied() < 1) {
    break; // 无占用（旧数据），优雅跳过
}
```

旧订单入住后因为 `occupyForCheckIn` 跳过了 ordered→occupied 转换，退房时 occupied 仍为 0，此处同样优雅跳过。

---

## 5 并发控制

### 5.1 乐观锁机制

所有对 `room_inventory` 的写操作都基于 **version 字段的乐观锁**：

```
读取当前行（含 version）→ 判断业务条件 → UPDATE ... WHERE version = #{version} → 检查 affected rows
```

- `affected == 1`：成功，version 已自增
- `affected == 0`：版本冲突（另一个事务已修改），进入重试

### 5.2 optimisticIncrementOrdered 详解

```java
private boolean optimisticIncrementOrdered(int typeId, Date date) {
    for (int attempt = 0; attempt < MAX_RETRY; attempt++) {  // MAX_RETRY = 3
        RoomInventory inv = inventoryMapper.selectByTypeAndDate(typeId, date);
        if (inv == null) {
            ensureInventoryRow(typeId, date);               // INSERT IGNORE 懒初始化
            inv = inventoryMapper.selectByTypeAndDate(typeId, date);
        }
        if (inv == null || inv.getAvailable() < 1) {
            return false;                                    // 真正售罄，不重试
        }
        int affected = inventoryMapper.incrementOrdered(typeId, date, inv.getVersion());
        if (affected == 1) {
            return true;                                     // 乐观锁成功
        }
        // affected == 0: 版本冲突，重试下一轮
    }
    return false;                                            // 重试耗尽
}
```

**失败语义分类**：

| 返回值 | 场景 | 语义 |
|---|---|---|
| `true` | `affected == 1` | 成功占用一个库存单位 |
| `false`（快速路径） | `inv.getAvailable() < 1` | **真实售罄**：可订量已为 0，无需重试 |
| `false`（重试耗尽） | 连续 3 次 `affected == 0` | **竞争过于激烈**：3 次 CAS 均被其他事务抢先，可能仍有余量但无法竞争到 |

### 5.3 SQL 层的双重守卫

`incrementOrdered` 的 SQL 同时检查 version 和 available：

```sql
UPDATE room_inventory
SET ordered = ordered + 1,
    version = version + 1,
    update_time = NOW()
WHERE type_id  = #{typeId}
  AND inv_date = #{invDate}
  AND version  = #{version}                                    -- 乐观锁守卫
  AND (total - ordered - occupied - reserved - maintenance) >= 1  -- 可订量守卫
```

这意味着即使 version 匹配，如果在 Java 层读取与 SQL 执行之间有其他事务将最后一个可订量占走，SQL 也会因为可订量守卫而 `affected = 0`，从而触发重试或判定售罄，**不会出现超卖**。

### 5.4 ConcurrencyTest 测试覆盖

| 测试方法 | 并发场景 | 验证目标 |
|---|---|---|
| `testConcurrentPayOrder_onlyOneSucceeds` | 5 线程同时 payOrder，仅 3 间单人房 | 成功数 ≤ 3；`rest == 10 - successCount`；不超卖 |
| `testConcurrentCheckIn_noDuplicateRoom` | 3 线程同时 checkIn 3 个已付款订单 | 全部成功且分配的物理房间 roomId 两两不同 |
| `testCrossDayCheckIn_inventoryCorrect` | 单线程 3 天订单：pay → checkIn → checkOut | 逐天验证：pay 后 `ordered=1`；checkIn 后 `ordered=0, occupied=1`；checkOut 后 `occupied=0` |
| `testCancelReleasesInventory_correctlyAfterPay` | 单线程 2 天订单：pay → cancel | 验证 `rest` 恢复初始值；两天的 `ordered` 均归 0 |

**testConcurrentPayOrder 核心逻辑**：

```java
// 5 个线程在 CountDownLatch 后同时触发
latch.countDown();
// ...每个线程调用 orderService.payOrder(order.getOrderId())
// 预期结果：
assertTrue(successCount.get() <= 3);           // 不超卖
assertEquals(10 - successCount.get(), rest);   // rest 一致
```

该测试证明乐观锁在并发条件下能正确限制 inventory 占用量，并且 `rest` 与实际成功数保持一致。

### 5.5 各写操作的重试策略对比

| 方法 | 最大重试 | 售罄判定 | 重试耗尽处理 |
|---|---|---|---|
| `optimisticIncrementOrdered` | 3 | `available < 1` 时立即返回 false | 返回 false（视为失败） |
| `retryingDecrementOrdered` | 3 | `ordered < 1` 时幂等跳过 | 静默（释放操作，已尽力） |
| `occupyForCheckIn` (ordered→occupied) | 3 | `ordered < 1` 时兼容跳过 | 返回 -2（入住失败） |
| `releaseForCheckOut` (decrement occupied) | 3 | `occupied < 1` 时幂等跳过 | 静默（释放操作，已尽力） |
| `setReserved` / `setMaintenance` | 3 | 不适用 | 返回 -2（操作员需重试） |

---

## 6 OrderStatus 与 README 功能清单命名对照

### 6.1 OrderStatus 枚举定义

| 枚举常量 | code | 中文标签 | 说明 |
|---|---|---|---|
| `WAS_DELETED` | -3 | 已删除 | 逻辑删除标记 |
| `OVERTIME` | -2 | 支付超时 | 超时未付款 |
| `WAS_CANCELED` | -1 | 已取消 | 用户或操作员取消 |
| `UNPAID` | 0 | 未付款 | 订单已创建，等待付款 |
| `PAID` | 1 | 待入住 | 已付款，等待入住 |
| `CHECK_IN` | 2 | 已入住 | 已完成入住登记 |

### 6.2 与 README 功能清单的不一致

| # | 不一致项 | README 描述 | 代码实际 | 建议规范 |
|---|---|---|---|---|
| 1 | **缺少"已退房"状态** | "结账：通过房间号码进行退房结账"作为独立功能 | 退房后订单状态仍为 `CHECK_IN(2)`，无 `CHECKED_OUT` 状态 | 增加 `CHECKED_OUT(3, "已退房")`，checkOut 时设置 |
| 2 | **枚举命名风格** | — | `WAS_DELETED`、`WAS_CANCELED` 使用过去时前缀 `WAS_` | 统一为 `DELETED` / `CANCELED`，或全部使用形容词形式 |
| 3 | **"预订"与"订单"** | README 使用"预订信息管理"、"预订客房" | 代码中统一为 `Order`、`OrderService` | README 应明确："预订"在代码中对应 `Order` 实体和 `OrderService`，建议文档统一为"订单" |
| 4 | **"结账"与"退房"** | README 使用"结账" | 代码中使用 `checkOut`，无独立的结账/计费逻辑 | README 应改为"退房"或在 checkOut 中补充计费逻辑 |
| 5 | **"费用信息"** | README 在"入住登记"下列出"费用信息" | `CheckIn` 实体无费用字段，费用仅在 `Order.orderCost` | README 的"费用信息"实际对应 `Order.orderCost`，应注明 |
| 6 | **OVERTIME 未体现** | README 未提及支付超时 | `OVERTIME(-2)` 存在于枚举，`OpOrderController` 有 overtime 端点 | README 应补充"超时自动取消"功能说明 |
| 7 | **WAS_DELETED 未体现** | README 未提及订单删除 | `WAS_DELETED(-3)` 用于 `UsersAllOrders` 过滤已删除订单 | README 应补充订单软删除说明 |
| 8 | **"业务统计"未实现** | README 列出"业务统计" | 代码中仅有 `getOrderCount()`，无完整统计模块 | README 应标记为未实现，或补充实现 |
| 9 | **RoomStatus.ORDERED 未使用** | — | `RoomStatus` 枚举定义了 `ORDERED(2, "被预订")`，但 `payOrder` 不设置物理房间状态 | `ORDERED` 为预留状态，当前流程中物理房间仅在 `checkIn` 时分配（`inRoom`），payOrder 只操作库存层 |

### 6.3 建议的 OrderStatus 规范命名

```java
public enum OrderStatus {
    DELETED(-3, "已删除"),       // 原 WAS_DELETED
    EXPIRED(-2, "已过期"),       // 原 OVERTIME，"expired" 比 "overtime" 更符合状态语义
    CANCELED(-1, "已取消"),      // 原 WAS_CANCELED
    UNPAID(0, "未付款"),         // 不变
    PAID(1, "待入住"),           // 不变
    CHECKED_IN(2, "已入住"),     // 原 CHECK_IN，使用过去分词
    CHECKED_OUT(3, "已退房");    // 新增：填补退房后的状态空缺
}
```

---

## 7 总结：一致性保障要点

| 风险场景 | 保障机制 | 相关代码 |
|---|---|---|
| 并发超卖 | 乐观锁 + SQL 可订量守卫 `available >= 1` | `incrementOrdered` SQL |
| rest 与 inventory 不一致 | 同一事务内原子操作；payOrder/cancelOrder/checkOut 同步修改两者 | `@Transactional` + `setRollbackOnly()` |
| 跨天订单部分天售罄 | 逐天占用，失败时手动回滚已占用天数 | `occupyForOrder` 回滚循环 |
| 旧订单入住报错 | `occupyForCheckIn` 兼容分支：inventory 行不存在或 ordered=0 时优雅跳过 | `inv == null \|\| ordered < 1` |
| 退房后状态丢失 | **当前缺陷**：订单停留在 CHECK_IN，无 CHECKED_OUT 状态 | 建议新增枚举值 |
| 用户看到"有空房"但下单失败 | `rest` 是快照，实际可订量由 `room_inventory.available` 决定 | `rest > 0` 仅用于列表筛选 |
