# 联系人聚合实现说明（Phase 1 + Phase 2）

对应方案：`Thunderbird_Android_Contact_Aggregation_Implementation_Plan.md`。
基线 commit：`a88ae92739`。

---

## 0. Phase 0 — Repo Survey（实际代码结构）

| # | 调查项 | 实际位置 |
|---|--------|----------|
| 1 | MessageListScreen | `feature/mail/message/list/internal/.../ui/MessageListScreenRenderer.kt` → `ui/component/page/MessageListPage.kt` → `ui/component/template/MessageList.kt`（Compose `LazyColumn`） |
| 2 | ViewModel / StateMachine | `internal/.../ui/MessageListViewModel.kt`、`internal/.../ui/state/machine/MessageListStateMachine.kt`（DSL：`Setup*State.kt`） |
| 3 | MessageListItem domain/UI model | `api/.../ui/state/MessageItemUi.kt`（UI）、legacy `com.fsck.k9.ui.messagelist.MessageListItem`（data） |
| 4 | Legacy data bridge | `api/.../ui/legacy/LegacyMessageListBridge.kt`，实现为 `legacy/ui/legacy/.../messagelist/MessageListFragment.loadMessages()` |
| 5 | Message list setting 存储 | `core/preference/api/.../visualSettings/message/list/DisplayMessageListSettings.kt` + `MessageListPreferencesManager.kt`，实现 `core/preference/impl/.../DefaultMessageListPreferencesManager.kt`；feature 侧映射 `GetMessageListPreferences.kt` |
| 6 | sender search 入口 | 无现成 “from this sender” 入口；复用 `LocalMessageSearch` + `MessageSearchField.SENDER` + `SearchAttribute.CONTAINS` + `MessageHomeActivity.actionDisplaySearch()` |
| 7 | contact API | `core/android/common/.../contact/`：`ContactRepository`（自带 `ExpiringCache`）、`ContactDataSource`、`ContactPermissionResolver`、`Contact(id, name, emailAddress, uri, photoUri)` |
| 8 | Message List DI wiring | `internal/.../FeatureMessageListModule.kt` + `ui/state/sideeffect/inject/MessageListSideEffectsModule.kt`（Koin） |
| 9 | 测试目录与模式 | `feature/mail/message/list/{api,internal}/src/test/...`；assertk + kotlinx-coroutines-test + Turbine；Compose/Robolectric 见 `MessageListScreenRendererTest`（继承 `ComposeTest`） |

关键既有约束（决定了实现方式）：

- `MessageItemUi.senders.displayName` 被 legacy 填成**邮箱地址**（`MessageListItemMapper` 用 `displayAddress`），群组标题需要额外的 friendly name 字段。
- 界面模式入口是 legacy 的排序弹窗 `SortCriteriaMenuList()`，排序 action view 由 `prepareSortMenu()` 挂载。
- `MessageListState` 有 5 个子类、96 处构造点 → 新字段必须给默认值，否则要改 96 处。

---

## 1. 架构落点

```
legacy loadMessages()  ──►  MessageItemUi（senderIdentity / sortTimestamp）
                                   │
                                   ▼
                     MessageListStateMachine   ← 唯一 source of truth
                        ├── messages 变化 → ContentFactory → MessageListContent
                        └── ContactIdentitiesResolved → content 重算
                                   │
                     ┌─────────────┴──────────────┐
                     ▼                            ▼
        MessageListContent.Messages     MessageListContent.ContactGroups
                     │                            │
                     ▼                            ▼
                MessageItem                  ContactGroupItem
                     │ click group
                     ▼
        MessageListEffect.OpenContactGroup(addresses)
                     ▼
        legacy openContactGroup() → LocalMessageSearch(SENDER OR ...) → actionDisplaySearch()
```

**没有**第二份邮件数据：`content` 与 `contactIdentities` 都是 `messages` 的纯投影，load more / 删除 / 刷新后不会出现重复群组。

异步联系人解析：

```
messages 就绪 → 立即按 email 分组渲染（首屏不等待联系人）
      ↓ ResolveContactIdentitiesSideEffect（仅 CONTACT 模式、仅在 sender 集合变化时）
ContactIdentityResolver.resolve(senders) → cache 命中 / ContentResolver 批量查询
      ↓ ContactIdentitiesResolved
   content 重算 → 同一 contact 的多地址合并为一个组
```

---

## 2. Phase 1 — Email Sender Aggregation MVP

### 新增文件

**`feature/mail/message/list/api`**
- `aggregation/EmailAddressNormalizer.kt` — `normalizeEmailAddress()`（trim + locale-independent lowercase；不做 provider alias 合并）
- `aggregation/ContactIdentityResolver.kt` — `ContactIdentityResolver` + `ContactIdentityResolverFunction`
- `aggregation/model/SenderIdentity.kt` — `SenderIdentity` + `createSenderIdentityOrNull()`
- `aggregation/model/ContactAggregationKey.kt` — `EmailAddress` / `UnknownSender` / `AndroidContact`
- `aggregation/model/ContactIdentity.kt` — 联系人身份（Phase 2）
- `aggregation/model/ContactGroup.kt` — 聚合产物（domain）
- `ui/state/MessageListContent.kt` — `Messages` / `ContactGroups` + `toMessagesContent()`
- `ui/state/ContactGroupUiModel.kt` — 群组行 UI model
- `ui/state/MessageContentFactory.kt` — `MessageContentFactory` typealias
- `ui/component/organism/ContactGroupItem.kt` — 群组行 Compose 组件（`TEST_TAG_CONTACT_GROUP_ITEM_ROOT`）

**`feature/mail/message/list/internal`**
- `aggregation/ContactMessageAggregator.kt` — 单 pass O(n) 聚合
- `aggregation/MessageListContentFactory.kt` — 聚合结果 → UI model（含计数文案本地化）
- `aggregation/DefaultContactIdentityResolver.kt` — Phase 2 联系人解析
- `ui/state/machine/ContentFactory.kt` — state machine 的 content 工厂 + `isContactAggregationAvailable()`
- `domain/usecase/SaveMessageListAggregationMode.kt`
- `ui/state/sideeffect/SetAggregationModeSideEffect.kt`
- `ui/state/sideeffect/ResolveContactIdentitiesSideEffect.kt`（Phase 2）
- `ui/state/sideeffect/ui/OpenContactGroupSideEffect.kt`

**`core/preference`**
- `api/.../message/list/MessageListAggregationMode.kt` — `NONE` / `CONTACT` + 默认值
- `DisplayMessageListSettings.aggregationMode` + storage key + impl 读写

### 关键设计决策

1. **群组 key = normalize(From 地址)**。`displayName` 只用于展示；`notifications@github.com` 与 `noreply@github.com` 即使同名也是两组。
2. **无合法 From 的邮件**用 `UnknownSender(messageId)`，每封一个组，不会合并成 `Unknown`。
3. **聚合输入是当前完整 loaded list**（`state.messages`），不是增量 page。
4. **排序**：`latestMessage.sortTimestamp` 降序；`latestMessage` 取组内 `sortTimestamp` **最大值**（不依赖输入顺序）。
5. **Sent / Drafts / Outbox 自动回退普通列表**：`MessageListMetadata.contactAggregationAvailable` 由 folder type 计算，这些文件夹不显示入口，切换时 `ContentFactory` 也会兜底为 `NONE`。
6. **群组行动作受限**：群组行不经过 `MessageListSwipeableItem`，不能 swipe / star / select，点击只发 `OpenContactGroup`。
7. **配置**沿用现有 preference 框架（全局，默认 `NONE`）；顺带修复了 `DefaultMessageListPreferencesManager.write()` 遗漏 `dateTimeFormat` 的问题。

---

## 3. Phase 2 — Android Contact Enhancement

1. **`DefaultContactIdentityResolver`**
   - `ContactPermissionResolver` 未授权 → 直接返回 email 身份，**完全不触碰** ContentResolver。
   - 已授权 → 在 `ioDispatcher` 上逐个查 `ContactRepository.getContactFor()`（内部 `ExpiringCache` 保证同一地址只查一次）。
   - 单次查询异常被捕获并记日志，降级为 email 身份（graceful fallback）。
   - 同一 `contactId` 的所有发件人映射到**同一个** `ContactIdentity`，并带上该 contact 在当前消息列表里出现的全部地址。
2. **多地址合并**：`ContactMessageAggregator.aggregate(messages, contactIdentities)`；key 变为 `AndroidContact(contactId)`，于是 `zhangsan@qq.com` 与 `zhangsan@company.com` 进入一个组。
3. **异步不阻塞首屏**：`ResolveContactIdentitiesSideEffect` 只在 `aggregationMode == CONTACT` 且 sender 集合变化时触发；解析在后台协程，结果通过 `ContactIdentitiesResolved` 事件回流。联系人名称/头像通过 `ContactGroupUiModel.title` / `avatar` 更新。
4. **缓存**：复用 `ContactRepository` 的 `ExpiringCache`（进程级）；side effect 内用 `resolvedSenders` 避免同一批发送者重复解析。
5. **多地址打开**：`MessageListEffect.OpenContactGroup(senderAddresses: List<String>)` → legacy 用 `LocalMessageSearch.or(SearchCondition(SENDER, CONTAINS, address))` 组合多地址 OR 条件，并限定 `accountUuids`。
6. **联系人权限说明**：权限仅作为增强；未授权时按方案要求**零功能损失**（仍按地址聚合）。

---

## 4. 兼容性 / 回归

- `aggregationMode` 默认 `NONE`；旧偏好文件缺 key 时回落默认值 → 关闭状态下行为与 upstream 一致。
- `MessageListContent` / `contactIdentities` 均为新增字段且带默认值，所有既有构造点自动得到普通列表。
- `MessageItemUi` 新增 `senderIdentity`（默认 `null`）与 `sortTimestamp`（无默认，已同步 legacy mapper、预览与测试工厂）。
- 未改动 IMAP/POP3/SMTP、同步、threading、搜索、swipe、selection、分页逻辑。
- 未新增跨 feature `:internal` 依赖；legacy 仅做桥接。

---

## 5. 测试

新增/扩展（`api` 16 + `internal` 188 tests，全部通过）：

- `EmailAddressNormalizerTest`（api）
- `ContactMessageAggregatorTest` — 同地址合并、同名不同址不合、大小写合并、最新优先排序、未读计数、load more 合并、跨账户合并、无地址不合并、**同 contact 多地址合并**、无联系人时回退
- `MessageListContentFactoryTest` — 普通/聚合内容、标题回退、未读文案、**联系人名称与头像**
- `DefaultContactIdentityResolverTest` — **权限关闭回退、命中 contact、同 contact 多地址同一 key、未知地址回退、查询异常 graceful fallback、缓存生效、空输入**
- `SetAggregationModeSideEffectTest`、`OpenContactGroupSideEffectTest`、`ResolveContactIdentitiesSideEffectTest`
- `MessageListStateMachineTest` — 模式切换 / 回退 / 不可用文件夹回退
- `MessageListScreenRendererTest` — 群组行展示、联系人名称展示、点击派发 `OpenContactGroup`、swipe 不产生 swipe 事件

### 已执行的验证

```
./gradlew :feature:mail:message:list:api:testDebugUnitTest          PASS (16)
./gradlew :feature:mail:message:list:internal:testDebugUnitTest     PASS (188, 1 skipped 预存在)
./gradlew :core:preference:api:allTests :core:preference:impl:allTests  PASS
./gradlew :app-thunderbird:assembleDebug :app-k9mail:assembleDebug   PASS
./gradlew :feature:mail:message:list:{api,internal}:detekt           PASS
./gradlew :feature:mail:message:list:{api,internal}:spotlessCheck    PASS
./gradlew :feature:mail:message:list:{api,internal}:lintDebug        PASS
./gradlew :legacy:ui:legacy:compileDebugKotlin                       PASS
```

### 未执行

- `connectedAndroidTest` / 仪器化测试（本机无 Android 设备/模拟器）。Compose 相关行为已通过 Robolectric 单测覆盖。

### 过程中修复的两个真实缺陷

1. `MessageListState.withPreferences` 在 `WarmingUp` 状态下变换了 `null`（旧实现 `preferences?.transform()`），导致 `UpdatePreferences` 在预热阶段被静默丢弃、状态机永远停在 `WarmingUp`。
2. `ContactMessageAggregator` 取组内“最新邮件”时依赖 map 插入顺序，而不是 `sortTimestamp` 最大值。

---

## 6. 提交拆分建议

```
1. feat(preference): add message list aggregation mode setting
2. feat(message-list): add sender identity normalization and aggregation domain logic
3. test(message-list): cover sender aggregation behavior
4. feat(message-list): render contact group rows in compose
5. feat(message-list): wire contact group navigation
6. feat(contacts): resolve android contact identities for sender groups
7. test(contacts): cover contact identity resolution
```
