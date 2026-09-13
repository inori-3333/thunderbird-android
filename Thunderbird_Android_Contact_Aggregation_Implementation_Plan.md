# Thunderbird for Android 联系人 / 发件人聚合功能实现方案

> 目标：基于 Thunderbird for Android 当前架构，实现类似网易邮箱大师的“同一发件人邮件聚合”能力，并为后续“同一联系人多个邮箱地址合并”为一个联系人组预留扩展点。
>
> 文档用途：直接交给本地 Coding Agent / Codex / Claude Code 执行。
>
> 基线日期：2026-09-12。Thunderbird for Android 主干仍处于 Message List Compose 化与 legacy data bridge 并存阶段，因此执行前必须先扫描当前 checkout 的实际代码结构，不得机械依赖本文中的类名。

---

## 0. 执行总原则

本功能应被实现为 **Message List 的一种展示/聚合模式**，而不是修改 IMAP、POP3、SMTP、同步、下载、消息存储协议逻辑。

### 必须遵守

1. **不要修改 IMAP / POP3 / SMTP 协议实现。**
2. **不要为了此功能重写邮件同步逻辑。**
3. 新代码优先放在：
   - `:feature:mail:message:list:api`
   - `:feature:mail:message:list:internal`
4. 联系人能力优先复用：
   - `:core:android:contact`
5. 配置存储优先复用项目现有 preference / config store，不新造配置框架。
6. UI 使用 Jetpack Compose。
7. 状态管理遵循项目现有 MVI / Unidirectional Data Flow。
8. 异步逻辑使用 Coroutine / Flow。
9. DI 使用 Koin。
10. 不允许从一个 feature 的 `:internal` 直接依赖另一个 feature 的 `:internal`。
11. `legacy:*` 只作为兼容桥接层；除非当前实现确实无法绕开，否则不要在 legacy 中增加新的产品逻辑。
12. 任何“临时桥接 legacy”的代码必须通过 adapter / mapper 隔离，并注明后续删除条件。

### 非目标

本次不做：

- 修改邮件服务器协议；
- Gmail 风格 conversation 重写；
- 服务端联系人同步；
- AI 联系人识别；
- 根据企业 Logo、域名自动猜测“同一公司”；
- Gmail `+tag`、点号规则等服务商特化地址合并；
- 自动把相似姓名的人合并；
- 在第一版对联系人组执行批量删除、归档、移动等高风险操作。

---

# 1. 产品目标

新增一种“联系人聚合”视图。

普通模式：

```text
GitHub: [PR] Fix Android issue
张三: 周末聚餐
GitHub: Security alert
李四: 会议纪要
GitHub: Build failed
```

联系人聚合模式：

```text
GitHub                     3 封 / 1 未读
[PR] Fix Android issue             10:23

张三                       1 封
周末聚餐                           昨天

李四                       1 封
会议纪要                           周一
```

点击 `GitHub` 后进入该发件人的邮件列表：

```text
GitHub

10:23   [PR] Fix Android issue
昨天    Security alert
周一    Build failed
```

## 1.1 第一阶段必须实现

第一阶段按 **标准化后的 From 邮箱地址** 聚合。

例如：

```text
GitHub <notifications@github.com>
GitHub <notifications@github.com>
```

必须进入同一组。

以下地址第一阶段不得自动合并：

```text
notifications@github.com
noreply@github.com
```

即便显示名称都叫 `GitHub`，也视为两个发件人。

## 1.2 第二阶段扩展

如果系统通讯录中：

```text
联系人：张三
- zhangsan@qq.com
- zhangsan@company.com
```

则允许把两个地址映射到同一个 Android Contact，并聚合成一个联系人组。

联系人权限未授权时，应自动退化为第一阶段的“邮箱地址聚合”，不得影响邮箱核心功能。

---

# 2. 当前 Thunderbird Android 架构约束

执行 Agent 首先阅读仓库根目录：

```text
AGENTS.md
```

并确认当前分支的架构规则。

当前上游的重要模块包括：

```text
:app-thunderbird
:app-k9mail
:app-common

:feature:mail:message:list:api
:feature:mail:message:list:internal

:core:android:contact
:core:configstore:api

:feature:search:impl-legacy

:legacy:core
:legacy:mailstore
:legacy:message
:legacy:storage
:legacy:ui:legacy
```

当前 Thunderbird Android 正在把 Message List 向 Compose + `feature:mail:message:list` 迁移，同时仍存在 legacy data source / business logic bridge。

因此本功能采用：

```text
现有邮件数据源
      ↓
Legacy / current Message List bridge
      ↓
MessageList domain / state
      ↓
ContactAggregationMapper
      ↓
Contact-group UI model
      ↓
Compose MessageListScreen
```

而不是：

```text
Compose UI
   ↓
直接访问 SQLite / legacy storage   ← 禁止
```

---

# 3. 功能边界设计

## 3.1 不替代现有 Threading

“联系人聚合”与“邮件会话 Threading”不是同一个概念。

不要把现有 conversation/threading 逻辑改造成 sender grouping。

定义两个层次：

```text
Top-level list grouping
    NORMAL
    CONTACT

进入某个联系人后
    ↓
继续沿用 Thunderbird 现有 message/thread 展示逻辑
```

建议新增：

```kotlin
enum class MessageListAggregationMode {
    NONE,
    CONTACT,
}
```

如果项目已有更合适的 setting/model，复用现有类型，不重复创建概念。

---

# 4. 推荐 UX

## 4.1 入口

在 Message List 的显示/排序菜单中新增：

```text
邮件显示方式

✓ 普通列表
  联系人聚合
```

如果现有 UI 更适合 Switch / radio dialog，则遵循项目 Design System。

第一版不要把该功能默认开启。

## 4.2 联系人组 Item

推荐展示：

```text
[Avatar] GitHub                 12
         notifications@github.com
         [PR] Fix Android issue
         3 未读                      10:23
```

字段：

- 头像：优先 Android Contact 头像；否则使用当前 Thunderbird 的 contact picture / initials fallback；
- 主标题：联系人显示名 > 邮件 From display name > 邮箱地址；
- 副标题：主邮箱地址；
- preview：该组最新邮件的 Subject；
- timestamp：该组最新邮件时间；
- `messageCount`：当前数据源中已知的组内邮件数；
- `unreadCount`：当前数据源中已知的未读数；
- Unified Inbox 中保留必要的 account indicator。

### 排序

联系人组按：

```text
latestMessageDate DESC
```

排序。

组内邮件继续使用当前 Message List 默认排序。

## 4.3 交互

第一版：

- 点击联系人组 → 打开该发件人的邮件列表；
- 长按联系人组 → 暂不提供 destructive batch actions；
- swipe delete/archive → 联系人组模式禁用；
- pull-to-refresh → 保持可用；
- load more → 保持可用；
- 新邮件到达 → 重新聚合并更新对应联系人组；
- 联系人名称/头像变化 → 下次解析时更新。

禁止第一版做：

```text
左滑联系人组 = 删除该联系人全部邮件
```

这种行为风险太高。

---

# 5. 数据模型

不要直接让 Compose 对 `Message` 对象执行 `groupBy`。

应在 domain/presentation mapping 层完成聚合。

建议模型如下。

## 5.1 SenderIdentity

```kotlin
data class SenderIdentity(
    val normalizedAddress: String,
    val rawAddress: String,
    val displayName: String?,
)
```

## 5.2 ContactIdentity

```kotlin
data class ContactIdentity(
    val key: ContactAggregationKey,
    val displayName: String,
    val primaryAddress: String,
    val addresses: Set<String>,
    val photoUri: String? = null,
)
```

## 5.3 ContactAggregationKey

```kotlin
sealed interface ContactAggregationKey {
    data class EmailAddress(
        val normalizedAddress: String,
    ) : ContactAggregationKey

    data class AndroidContact(
        val contactId: Long,
    ) : ContactAggregationKey
}
```

不要把 `displayName` 当 key。

错误：

```text
"GitHub" == "GitHub"
```

正确：

```text
notifications@github.com
```

或者第二阶段：

```text
Android contactId = 12345
```

## 5.4 ContactGroup

```kotlin
data class ContactGroup(
    val key: ContactAggregationKey,
    val identity: ContactIdentity,
    val latestMessage: MessageListItem,
    val messageCount: Int,
    val unreadCount: Int,
    val hasStarredMessage: Boolean,
    val accountIds: Set<String>,
    val messageIds: List<String>,
)
```

注意：

`MessageListItem`、message ID、account ID 的具体类型必须根据当前 checkout 的真实代码替换。

不要为了匹配本文而创建重复 wrapper。

---

# 6. 邮箱地址标准化规则

新增一个可单测的：

```kotlin
fun normalizeEmailAddress(address: String): String
```

第一版规则：

1. trim whitespace；
2. 去掉外围无意义空格；
3. domain 转 lowercase；
4. 为实际用户体验，可将整个地址做 locale-independent lowercase；
5. 不做 provider-specific alias normalization。

示例：

```text
" Foo@Example.COM "
→ foo@example.com
```

不要做：

```text
foo+shop@gmail.com → foo@gmail.com
john.smith@gmail.com → johnsmith@gmail.com
```

原因：会引入跨服务商误合并。

如果 From 中存在多个 mailbox：

- 选择第一个合法 mailbox 作为 V1 sender；
- 若无合法地址，则为该邮件生成稳定 fallback key；
- 无地址的邮件不得全部聚合到一个 `Unknown` 组。

建议 fallback：

```text
unknown:<stable-message-id>
```

---

# 7. 联系人解析

## 7.1 第一阶段

不依赖通讯录权限。

```text
From email
   ↓
normalizeEmailAddress
   ↓
EmailAddress key
```

## 7.2 第二阶段

复用 `:core:android:contact`。

流程：

```text
normalized email
      ↓
ContactResolver
      ↓
Android contact match?
     / \
   Yes  No
   ↓     ↓
contactId EmailAddress key
```

建议接口：

```kotlin
interface ContactIdentityResolver {
    suspend fun resolve(sender: SenderIdentity): ContactIdentity
}
```

批量版本优先：

```kotlin
interface ContactIdentityResolver {
    suspend fun resolve(
        senders: Set<SenderIdentity>,
    ): Map<SenderIdentity, ContactIdentity>
}
```

不要在 `LazyColumn` 每个 item 重组时直接查询 Android Contacts Provider。

## 7.3 性能要求

已知 Thunderbird 历史上联系人名称解析曾造成 Message List 显著延迟，因此实现必须避免 N 次同步 ContentResolver 查询。

最低要求：

- IO dispatcher；
- batch / cache；
- UI 不等待联系人解析才能首屏显示；
- 可先显示邮件 From 名称，然后异步替换联系人名称/头像；
- 对本次 Message List 生命周期内的 email → contact identity 做 cache；
- 联系人权限关闭时完全跳过 contact provider。

建议：

```text
Messages loaded
   ↓
立即按 email grouping 渲染
   ↓
异步 contact resolution
   ↓
发现多个地址属于同 contactId
   ↓
合并 group + state update
```

这样不会因为联系人数据库慢而卡住首屏。

---

# 8. 聚合算法

建议新建：

```text
ContactMessageAggregator
```

职责单一：输入 Message List domain items，输出 Contact Groups。

伪代码：

```kotlin
fun aggregate(
    messages: List<MessageListItem>,
    identities: Map<MessageKey, ContactIdentity>,
): List<ContactGroup> {
    return messages
        .groupBy { message -> identities.getValue(message.key).key }
        .map { (key, groupMessages) ->
            val latest = groupMessages.maxBy { it.sortTimestamp }

            ContactGroup(
                key = key,
                identity = identities.getValue(latest.key),
                latestMessage = latest,
                messageCount = groupMessages.size,
                unreadCount = groupMessages.count { !it.isRead },
                hasStarredMessage = groupMessages.any { it.isStarred },
                accountIds = groupMessages.map { it.accountId }.toSet(),
                messageIds = groupMessages.map { it.id },
            )
        }
        .sortedByDescending { it.latestMessage.sortTimestamp }
}
```

实际实现不要阻塞 Main thread。

---

# 9. 分页 / Load More 策略

这是本功能最容易踩坑的地方。

Thunderbird Message List 并不保证一次加载全部历史邮件。

因此 V1 定义为：

> 联系人组统计值针对“当前 Message List data source 已加载/已知的消息集合”。

例如：

```text
第一次加载：
GitHub 3 封

Load more 后发现 5 封旧 GitHub 邮件：
GitHub 8 封
```

必须 **合并到已有 GitHub 组**，不能出现两个 GitHub group。

## 9.1 State 要求

聚合输入必须是当前完整 loaded list，而不是只对增量 page 做：

```kotlin
newPage.groupBy(...)
```

错误：

```text
Page 1 → GitHub Group A
Page 2 → GitHub Group B
```

正确：

```text
loadedMessages = page1 + page2
aggregate(loadedMessages)
→ GitHub Group A，count 更新
```

## 9.2 第二阶段可选优化

如果后续要求“不加载全部邮件也立即显示完整 count”，再新增 local-store aggregation query。

不要在 MVP 为了 count 重构整个 legacy storage。

---

# 10. 打开联系人组后的实现

## 10.1 V1：邮箱地址组

点击：

```text
notifications@github.com
```

应进入：

```text
From = notifications@github.com
```

对应的 Message List。

优先复用 Thunderbird 已存在的“Search messages from this sender”能力，而不是自己再写邮件查询系统。

Agent 必须先搜索仓库中的：

```text
search from sender
sender search
MessageSearch
SearchCondition
SearchSpecification
Searchable
```

以及当前 message list 点击/导航 wiring。

如果当前 search API 支持 sender filter，则直接复用。

## 10.2 V2：Android Contact 多地址组

联系人：

```text
张三
- a@example.com
- b@example.com
```

详情页应满足：

```text
FROM a@example.com OR FROM b@example.com
```

如果现有 search abstraction 不支持多 sender OR：

优先新增 feature/domain 层组合 filter；不要拼接未经验证的 IMAP search string。

---

# 11. Message List State 改造

推荐不要维护两套完全独立的 MessageList screen。

建议：

```kotlin
data class MessageListState(
    ...,
    val aggregationMode: MessageListAggregationMode,
    val messageItems: List<MessageListItem>,
    val contactGroups: List<ContactGroupUiModel>,
)
```

或者使用 sealed content：

```kotlin
sealed interface MessageListContent {
    data class Messages(...): MessageListContent
    data class ContactGroups(...): MessageListContent
}
```

更推荐 sealed 方案，减少 UI 同时持有两套互斥列表。

示例：

```kotlin
sealed interface MessageListContent {
    data class Messages(
        val items: List<MessageListItemUiModel>,
    ) : MessageListContent

    data class ContactGroups(
        val items: List<ContactGroupUiModel>,
    ) : MessageListContent
}
```

Compose：

```kotlin
when (val content = state.content) {
    is Messages -> MessageItems(...)
    is ContactGroups -> ContactGroupItems(...)
}
```

---

# 12. Intent / Action 设计

建议新增类似：

```kotlin
sealed interface MessageListIntent {
    data class SetAggregationMode(
        val mode: MessageListAggregationMode,
    ) : MessageListIntent

    data class OpenContactGroup(
        val key: ContactAggregationKey,
    ) : MessageListIntent
}
```

命名以当前项目 MVI 约定为准。

不要让 Composable 自己：

- 修改 preference；
- 查询联系人；
- 构建 Search query；
- 访问 legacy controller。

---

# 13. 配置持久化

功能应记住用户选择。

建议：

```text
message_list_aggregation_mode = none | contact
```

但先检查项目是否已经有类似：

- message list display preference；
- threading preference；
- sort preference；
- per-account / global display config。

### V1 推荐

做成全局 display preference。

不要第一版做 per-folder preference。

如果项目现有 message list preference 都是 per-account，则遵循项目既有模式。

---

# 14. Unified Inbox

必须支持 Unified Inbox。

聚合 key 默认跨账户共享：

```text
Account A: notifications@github.com
Account B: notifications@github.com
```

在 Unified Inbox 中应聚成同一联系人组。

`ContactGroup` 内保留：

```kotlin
accountIds: Set<AccountId>
```

如果组内只有一个账户：显示现有 account indicator。

如果组内多个账户：

V1 可以：

- 显示最新邮件所属账户 indicator；
- 或显示简洁 multi-account indicator。

不要为了该功能重做 Account Avatar 系统。

---

# 15. 特殊文件夹策略

## Inbox / Unified Inbox

完整支持。

## Archive / 普通文件夹

支持按 `From` 聚合。

## Sent / Drafts / Outbox

按 `From` 聚合通常会导致大量邮件全部聚到“自己”。

V1 建议：

```text
Sent / Drafts / Outbox
→ 自动回退普通列表
```

或者不在这些 folder type 中展示“联系人聚合”入口。

不要第一版擅自实现“如果是自己发出的邮件则按 To 聚合”，因为那已经变成 correspondent/conversation-person 聚合，语义不同。

未来可以独立设计：

```text
Correspondent mode
incoming → From
outgoing → To
```

---

# 16. Search 结果策略

V1：

```text
Search result screen
→ 默认使用普通 Message List
```

不要在第一版对搜索结果再次联系人聚合，避免 search semantics + group semantics 同时复杂化。

后续可扩展。

---

# 17. Swipe / Selection / Batch Action 策略

## 联系人组列表

V1：

- 禁用 message-level swipe delete/archive；
- 禁用星标点击；
- 不把联系人组直接当成单封 Message；
- 不允许沿用原 Message List item handler 强制转换。

## 联系人详情列表

进入联系人组后恢复正常 Message List 行为：

- 单封删除；
- 归档；
- 已读；
- 星标；
- 多选；
- thread 展示。

---

# 18. 推荐代码结构

不要为了本文强制创建不存在的 module。

在现有：

```text
feature/mail/message/list/
├── api/
└── internal/
```

内部建议按项目已有 package style 放置：

```text
internal/
├── aggregation/
│   ├── ContactMessageAggregator.kt
│   ├── ContactIdentityResolver.kt
│   ├── DefaultContactIdentityResolver.kt
│   ├── EmailAddressNormalizer.kt
│   └── model/
│       ├── ContactAggregationKey.kt
│       └── ContactGroup.kt
│
├── ui/
│   ├── ContactGroupItem.kt
│   └── model/
│       └── ContactGroupUiModel.kt
│
└── ... existing message list files
```

如果仓库当前结构已经明确采用：

```text
ui/
domain/
data/
```

则按现有结构放置，不创建新的风格。

---

# 19. DI

`ContactIdentityResolver` 使用 constructor injection。

例如：

```kotlin
internal class DefaultContactIdentityResolver(
    private val contactRepository: ContactRepository,
    private val dispatcher: CoroutineDispatcher,
) : ContactIdentityResolver
```

在 `app-common` 或当前项目规定的 wiring 层完成：

```text
interface → implementation
```

不要让 `:api` 依赖 `:internal`。

---

# 20. 联系人权限

原则：

联系人权限是 enhancement，不是邮箱功能运行条件。

行为：

```text
Permission granted
→ Android contact ID 聚合

Permission denied
→ email address 聚合
```

不要为了联系人聚合强制弹权限。

优先：

- 用户已经授权联系人 → 自动增强；
- 未授权 → 不阻断功能；
- 如果产品需要提示，在联系人聚合设置说明中解释“授权联系人后可合并同一联系人多个邮箱地址”。

---

# 21. 性能预算

目标数据量：

```text
已加载 1,000 封邮件
不同 sender 300~600
```

聚合本身必须接近 O(n)。

禁止：

```text
for each message:
    scan every other message
```

也禁止：

```text
for each message:
    synchronous ContactsProvider query
```

应使用：

```text
O(n) groupBy / mutable map
+ batch/cached contact lookup
```

建议聚合核心使用单 pass：

```kotlin
val groups = LinkedHashMap<ContactAggregationKey, MutableGroup>()
```

而不是多次完整扫描。

---

# 22. 并发与更新

需要正确处理：

- 新邮件加入；
- 邮件被删除；
- read/unread 改变；
- star 改变；
- load more；
- pull refresh；
- account 切换；
- folder 切换；
- aggregation mode 切换；
- contact resolution 异步返回。

聚合状态应从单一 source of truth 派生。

推荐：

```text
messageListFlow
aggregationModeFlow
contactIdentityFlow
       ↓
combine(...)
       ↓
ContactGroup state
```

不要维护一个容易与 Message List 不同步的可变“第二份邮件数据库”。

---

# 23. 单元测试

至少新增以下测试。

## EmailAddressNormalizerTest

```text
Foo@Example.COM → foo@example.com
前后空格被去除
空字符串安全处理
异常 From 不崩溃
```

## ContactMessageAggregatorTest

### Case 1

两封同地址：

```text
foo@example.com
foo@example.com
```

→ 1 group / count 2。

### Case 2

同 displayName，不同 address：

```text
GitHub <a@github.com>
GitHub <b@github.com>
```

→ 2 groups。

### Case 3

不同大小写：

```text
Foo@Example.com
foo@example.COM
```

→ 1 group。

### Case 4

排序：

最新邮件所属 group 必须排最前。

### Case 5

unread count 正确。

### Case 6

load more：

第一次：

```text
A, B
```

第二次：

```text
A, B, A
```

→ A 仍然只有一个 group，count 从 1 → 2。

### Case 7

Unified Inbox：

相同 sender 跨两个 account → 1 group。

### Case 8

无合法 From 的两封不同邮件 → 不应错误合并成一个 Unknown group。

## ContactIdentityResolverTest

- 联系人权限关闭 → email group；
- email 命中 contact → AndroidContact key；
- 同 contact 两个 email → same key；
- contact lookup failure → graceful fallback；
- cache 生效。

---

# 24. UI 测试

至少验证：

1. 普通模式与联系人聚合模式切换；
2. Contact group item 显示名称、地址、subject、timestamp；
3. 未读计数；
4. 点击组进入 sender list；
5. 返回后 aggregation mode 保持；
6. pull refresh 不退出联系人模式；
7. load more 后 group 更新；
8. 联系人组不出现危险 swipe delete；
9. Unified Inbox group 正常显示；
10. 联系人无头像时 fallback UI 正常。

Compose 测试遵循项目已有 test tag / semantics 约定。

---

# 25. 回归测试

联系人模式关闭时必须确保：

- Message List 行为与 upstream 完全一致；
- Threading 不受影响；
- 搜索不受影响；
- swipe 不受影响；
- selection 不受影响；
- refresh 不受影响；
- pagination 不受影响；
- Unified Inbox 不受影响；
- K-9 Mail white-label build 仍能编译。

至少执行项目要求的：

```text
format / lint
unit tests
relevant feature tests
app-thunderbird build
app-k9mail build
```

具体 Gradle task 根据仓库当前 `AGENTS.md` / CONTRIBUTING 文档执行。

---

# 26. 推荐开发阶段

## Phase 0 — Repo Survey

Agent 必须先完成，不允许直接写代码。

输出一份短调查：

```text
1. 当前 MessageListScreen 文件路径
2. 当前 MessageList ViewModel/Presenter/Store 路径
3. MessageListItem domain/UI model
4. 当前 legacy bridge
5. Message List setting 存储位置
6. sender search 的入口
7. core:android:contact 当前 API
8. Message List DI wiring
9. 当前测试目录和测试模式
```

如果当前 upstream 已经完成更多 Compose migration，则以当前代码为准。

## Phase 1 — Email Sender Aggregation MVP

实现：

- aggregation setting；
- normalized From address；
- ContactMessageAggregator；
- ContactGroup UI model；
- Compose group row；
- mode toggle；
- load more 合并；
- sender group click；
- tests。

此阶段 **不依赖 Contacts 权限**。

## Phase 2 — Android Contact Enhancement

实现：

- `ContactIdentityResolver`；
- `:core:android:contact` integration；
- cache；
- contact name/photo；
- multiple-email → same contact merge；
- fallback；
- tests。

## Phase 3 — UX Polish

- multi-account indicator；
- empty states；
- accessibility；
- previews；
- animation；
- group detail title；
- settings copy。

## Phase 4 — Optional Full-store Aggregation

只有产品确认需要“未 load more 就显示完整历史 count”时才做。

这一步可能涉及 storage query / repository 能力扩展，应单独设计，不要塞入 MVP。

---

# 27. 推荐提交拆分

不要一次提交几千行大 PR。

建议本地 agent 分 commit：

```text
1. feat(message-list): add aggregation mode model and preference
2. feat(message-list): add sender identity normalization and aggregation domain logic
3. test(message-list): cover sender aggregation behavior
4. feat(message-list): render contact group rows in compose
5. feat(message-list): wire contact group navigation
6. feat(message-list): support sender aggregation in unified inbox and pagination
7. feat(contacts): resolve android contact identities for sender groups
8. test(contacts): cover contact identity resolution
9. feat(message-list): polish contact aggregation UX
```

每个 commit 都应保持可编译。

---

# 28. Definition of Done

第一阶段完成标准：

- [ ] 用户可以在 Message List 中切换“普通列表 / 联系人聚合”；
- [ ] 相同 From 邮箱地址只显示一个组；
- [ ] 不同邮箱地址即使 displayName 相同也不会误合并；
- [ ] 组按最新邮件时间排序；
- [ ] 展示最新 Subject；
- [ ] 展示 message count；
- [ ] 展示 unread count；
- [ ] 点击组可看到该 sender 的邮件；
- [ ] Load more 后旧邮件正确并入已有组；
- [ ] Unified Inbox 可用；
- [ ] Sent / Drafts / Outbox 不产生“全部聚到自己”的糟糕体验；
- [ ] 联系人模式关闭时原 Message List 零行为回归；
- [ ] 单元测试通过；
- [ ] Compose/UI 测试通过；
- [ ] Thunderbird build 通过；
- [ ] K-9 Mail build 通过；
- [ ] 没有新增直接跨 feature internal 依赖；
- [ ] 没有修改协议层。

第二阶段完成标准：

- [ ] 已授权 Contacts 时可显示系统联系人名称/头像；
- [ ] 同一 Android Contact 的多个邮箱地址合并为一个组；
- [ ] Contacts 未授权时无功能损失；
- [ ] Contacts Provider 慢时不阻塞 Message List 首屏；
- [ ] 联系人查询具备缓存；
- [ ] 联系人查询失败 graceful fallback。

---

# 29. Agent 执行指令（可直接作为任务 Prompt）

```text
你正在修改 thunderbird/thunderbird-android。

目标：实现“联系人聚合 / sender aggregation”Message List 模式，体验类似网易邮箱大师：同一发件人的邮件在列表中聚成一个组，点击后查看该发件人的邮件。

先不要写代码。第一步阅读：
- 根目录 AGENTS.md
- Message List 相关 ADR / architecture docs
- :feature:mail:message:list:api
- :feature:mail:message:list:internal
- 当前 Message List legacy bridge
- :core:android:contact
- 当前 Message List preference/config 实现
- 当前 sender search 实现

先输出 Repo Survey，列出：
1. MessageListScreen 实际路径
2. state/store/viewmodel 实际路径
3. MessageListItem model
4. legacy data bridge
5. config/preferences
6. sender search
7. contact API
8. DI wiring
9. tests

然后按本文 Phase 1 实现。

核心约束：
- 不改 IMAP/POP3/SMTP
- 不重写同步
- 不把 contact grouping 混入 conversation threading
- 新逻辑优先在 feature:mail:message:list
- 不新增 feature internal 跨模块依赖
- UI 使用 Compose
- Coroutine/Flow
- Koin
- 使用当前项目 design system
- 联系人模式关闭时原行为完全不变

V1 group key：normalize(From email address)。
不要按 displayName group。
不要做 Gmail alias normalization。
无合法 From 的邮件用独立 stable fallback key，不能全部合成 Unknown。

分页：必须基于当前完整 loaded message set 重新聚合；load more 后相同 sender 并入原 group。

联系人组第一版禁用 destructive swipe/batch action。

点击 sender group 优先复用现有“search messages from sender”能力。

完成 Phase 1 后运行相关 lint/test/build，再进入 Phase 2：复用 :core:android:contact，把同一 Android Contact 的多个邮箱地址合成同一组；联系人解析必须异步、缓存、失败 fallback，不能阻塞首屏。

每个阶段完成后输出：
- 修改文件
- 关键设计
- 测试结果
- 已知限制
- 下一步
```

---

# 30. 风险清单

## 风险 1：联系人查询拖慢首屏

对策：先 email grouping，联系人解析异步增强 + cache。

## 风险 2：分页产生重复 sender group

对策：聚合当前完整 loaded state，不按 page 独立 group。

## 风险 3：误把相同 displayName 合并

对策：displayName 只展示，不参与 V1 identity key。

## 风险 4：Sent folder 全部聚成自己

对策：V1 在 Sent/Drafts/Outbox 禁用或自动回退 normal。

## 风险 5：Compose 新 Message List 与 legacy bridge 变化

对策：Phase 0 Survey，严格以 checkout 当前代码为准。

## 风险 6：联系人多个邮箱的详情 query

对策：V1 先 email group；V2 使用多 sender OR filter，优先复用现有 Search abstraction。

## 风险 7：跨账户相同 sender

对策：Unified Inbox 使用 sender/contact key 跨账户聚合，同时保留 accountIds。

---

# 31. 上游参考

执行时应优先检查当前仓库内容。本文制定时参考的上游信息：

- Thunderbird Android repository:
  https://github.com/thunderbird/thunderbird-android
- AI Agent Guide / architecture rules:
  https://github.com/thunderbird/thunderbird-android/blob/main/AGENTS.md
- Project Structure ADR:
  https://github.com/thunderbird/thunderbird-android/blob/main/docs/engineering/adr/0007-project-structure.md
- API/Internal split ADR:
  https://github.com/thunderbird/thunderbird-android/blob/main/docs/engineering/adr/0009-api-internal-split.md
- MessageListScreen Compose migration issue:
  https://github.com/thunderbird/thunderbird-android/issues/10573
- Message List legacy data bridge milestone:
  https://github.com/thunderbird/thunderbird-android/issues/10617
- Contact name lookup performance history:
  https://github.com/thunderbird/thunderbird-android/issues/8970

---

# 32. 最终架构摘要

```text
                         ┌─────────────────────┐
                         │ Existing mail sync  │
                         │ IMAP / POP / JMAP   │
                         └──────────┬──────────┘
                                    │
                                    ▼
                         ┌─────────────────────┐
                         │ Message List source │
                         │ current/legacy      │
                         └──────────┬──────────┘
                                    │ Flow/state
                                    ▼
                         ┌─────────────────────┐
                         │ MessageList domain  │
                         └──────────┬──────────┘
                                    │
                  ┌─────────────────┴────────────────┐
                  │ aggregationMode == CONTACT       │
                  ▼                                  ▼
        ┌──────────────────────┐           aggregationMode == NONE
        │ SenderIdentity       │                     │
        │ + normalize email    │                     ▼
        └──────────┬───────────┘           Existing Message List
                   │
                   ▼
        ┌──────────────────────┐
        │ Contact resolver     │
        │ optional / async     │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ ContactAggregator    │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ ContactGroupUiModel  │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ Compose LazyColumn   │
        │ Contact group rows   │
        └──────────┬───────────┘
                   │ click
                   ▼
        ┌──────────────────────┐
        │ Existing sender      │
        │ search/message list  │
        └──────────────────────┘
```

这套设计的核心原则是：**联系人聚合只改变 Message List 的呈现和查询入口，不侵入邮件协议与同步内核。**
