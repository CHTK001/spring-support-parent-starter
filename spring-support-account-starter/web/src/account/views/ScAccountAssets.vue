<template>
  <div class="account-assets">
    <div class="stage">
    <div v-if="loadError" class="alert">
      <span>账号列表加载失败：{{ loadError }}</span>
      <button type="button" @click="loadAll">重试</button>
    </div>

    <div class="ws" :style="{ '--idx-width': railWidth + 'px' }">
      <!-- ==================== 左侧：分组索引 ==================== -->
      <aside class="idx" aria-label="资产索引">
        <div class="idx-tools">
          <label class="search">
            <ReIcon class="search-icon" name="Search" :size="13" />
            <input v-model="keyword" :placeholder="searchPlaceholder" />
          </label>
          <button type="button" class="icon-btn" title="刷新" aria-label="刷新" @click="loadAll">
            <ReIcon name="RefreshLeft" :size="14" />
          </button>
          <!-- 批量选择模式开关：进模式后单击索引行即勾选，不必每行右键 / 按 Ctrl -->
          <button
            v-if="isAccountSegment"
            type="button"
            class="icon-btn"
            :class="{ 'is-on': batchMode }"
            :title="batchMode ? '退出批量选择（Esc）' : '批量选择'"
            :aria-label="batchMode ? '退出批量选择' : '批量选择'"
            :aria-pressed="batchMode"
            @click="toggleBatchMode"
          >
            <ReIcon name="Select" :size="14" />
          </button>
        </div>

        <!-- 批量操作条：进入批量选择后出现，贴在搜索框下方，图标按钮 + 提示压成一行 -->
        <div v-if="isAccountSegment && batchMode" class="batch-bar">
          <span class="batch-count" :class="{ 'is-empty': !selectedAccountIds.length }">
            <template v-if="selectedAccountIds.length">已选 <b>{{ selectedAccountIds.length }}</b> 项</template>
            <template v-else>单击左侧账号勾选</template>
          </span>
          <span class="batch-tools">
            <ReButton
              layout="square"
              size="small"
              icon="CircleCheck"
              tip="批量校验"
              :loading="batchBusy"
              :disabled="!selectedAccountIds.length"
              @click="batchVerify"
            />
            <ReButton
              layout="square"
              size="small"
              icon="SwitchButton"
              tip="批量启用"
              :loading="batchBusy"
              :disabled="!selectedAccountIds.length"
              @click="batchSetStatus(1)"
            />
            <ReButton
              layout="square"
              size="small"
              icon="TurnOff"
              tip="批量停用"
              :loading="batchBusy"
              :disabled="!selectedAccountIds.length"
              @click="batchSetStatus(0)"
            />
            <ReButton
              layout="square"
              size="small"
              icon="Delete"
              type="danger"
              tip="批量删除"
              :loading="batchBusy"
              :disabled="!selectedAccountIds.length"
              @click="batchRemove"
            />
          </span>
          <button type="button" class="batch-clear" @click="toggleBatchMode">取消</button>
        </div>

        <!-- 行内统计：数字即筛选入口，压缩成一条不抢版面 -->
        <div class="stat-row">
          <button
            v-for="cell in kpiCells"
            :key="cell.key"
            type="button"
            class="stat"
            :class="{ 'is-on': cell.on }"
            :title="cell.hint"
            @click="cell.run()"
          >
            <b :class="cell.tone">{{ cell.value }}</b>
            <span>{{ cell.label }}</span>
          </button>
        </div>

        <div v-if="statusFilter !== 'all'" class="idx-filter">
          仅看「{{ statusFilterLabel }}」
          <button type="button" @click="statusFilter = 'all'">清除</button>
        </div>

        <div v-if="segmentError" class="idx-error">{{ segmentError }}</div>

        <div class="idx-scroll" @scroll.passive="onIndexScroll">
          <section v-for="group in visibleIndexGroups" :key="group.key" class="idx-group">
            <header class="idx-group-head">
              <ReIcon :name="group.icon" :size="12" />
              <span>{{ group.label }}</span>
              <b>{{ group.rows.length }}</b>
            </header>
            <button
              v-for="row in group.rows"
              :key="row.id"
              type="button"
              class="idx-row"
              :class="{ 'is-on': row.selected, 'is-off': row.status === 0, 'is-checked': row.checked }"
              :title="row.name"
              @click="selectIndexRow(group, row, $event)"
              @dblclick="openIndexRow(group, row)"
              @contextmenu.prevent="openIndexMenu($event, group, row)"
            >
              <span v-if="batchMode && row.checkable" class="idx-box" :class="{ 'is-on': row.checked }" aria-hidden="true">
                <ReIcon v-if="row.checked" name="Select" :size="10" />
              </span>
              <i v-else class="dot" :class="'is-' + row.tone"></i>
              <span class="idx-main">
                <span class="idx-name">{{ row.name }}</span>
                <span class="idx-sub">{{ row.sub }}</span>
              </span>
              <span class="idx-meta">
                <span v-if="row.tag" class="idx-tag" :class="'is-' + row.tagTone">{{ row.tag }}</span>
                <span class="idx-time">{{ row.meta }}</span>
              </span>
            </button>
            <p v-if="group.totalRows === 0" class="idx-empty">暂无{{ group.label }}，点下方新建</p>
          </section>
          <div v-if="loadedIndexRows < tableCount" class="idx-more">
            滚动加载更多（{{ loadedIndexRows }} / {{ tableCount }}）
          </div>
        </div>

        <div class="idx-foot">
          <span>{{ tableCount }} 项</span>
          <ReButton show-text size="small" icon="Plus" type="primary" @click="openCreate">{{ createLabel }}</ReButton>
        </div>
      </aside>

      <!-- 分栏手柄：拖拽改索引宽度，双击复位，偏好存本地 -->
      <ReRailSplit
        v-model:width="railWidth"
        class="ws-split"
        storage-key="account.rail.width.v1"
        :default-width="304"
        label="拖拽调整资产索引宽度"
      />

      <!-- ==================== 右侧：详情工作区 ==================== -->
      <section class="main" aria-label="资产详情">
        <!-- ---------- 账号 ---------- -->
        <template v-if="isAccountSegment && activeAccount">
          <header class="main-head">
            <span class="main-mark" :class="'is-' + kindClass(activeAccount.kind)">
              <ReIcon :name="kindIcon(activeAccount.kind)" :size="16" />
            </span>
            <div class="main-title">
              <b :title="activeAccount.name">{{ activeAccount.name || '未命名账号' }}</b>
              <span>
                {{ kindLabel(activeAccount.kind) }} · {{ activeAccount.code || '—' }} ·
                {{ activeAccount.owner ? `仅本人（${activeAccount.owner}）` : '部署级共享' }}
              </span>
            </div>
            <ReTag v-if="activeAccount.defaultAccount" type="warning" effect="plain" size="small">默认</ReTag>
            <ReTag v-if="isTempMail(activeAccount)" type="warning" effect="plain" size="small">临时邮箱</ReTag>
            <ReTag :type="activeAccount.status === 0 ? 'info' : 'success'" effect="plain" size="small">
              {{ activeAccount.status === 0 ? '停用' : '启用' }}
            </ReTag>
          </header>

          <nav class="main-tabs" role="tablist" aria-label="账号信息">
            <button
              v-for="tab in accountTabs"
              :key="tab.value"
              type="button"
              role="tab"
              class="main-tab"
              :class="{ 'is-on': mainTab === tab.value }"
              :aria-selected="mainTab === tab.value"
              @click="mainTab = tab.value"
            >
              {{ tab.label }}
              <b v-if="tab.value === 'mailbox' && unreadOf(activeAccount.accountId)" class="main-tab-badge">
                {{ unreadOf(activeAccount.accountId) }}
              </b>
            </button>
          </nav>

          <div class="main-body">
            <!-- 概览 -->
            <div v-if="mainTab === 'overview'" class="pane">
              <div class="facts">
                <div class="fact">
                  <span class="k">登录名 / 地址</span>
                  <span class="v mono">{{ activeAccount.username || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">服务地址</span>
                  <span class="v mono">{{ endpointText(activeAccount) || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">厂商</span>
                  <span class="v">{{ activeAccount.vendor || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">关联渠道</span>
                  <span class="v">{{ activeAccount.channelKey || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">归属</span>
                  <span class="v">{{ activeAccount.owner ? `仅本人（${activeAccount.owner}）` : '部署级共享' }}</span>
                </div>
                <div class="fact">
                  <span class="k">同种类默认</span>
                  <span class="v">{{ activeAccount.defaultAccount ? '是' : '否' }}</span>
                </div>
                <div class="fact">
                  <span class="k">创建时间</span>
                  <span class="v">{{ formatTime(activeAccount.createdAt) }}</span>
                </div>
                <div class="fact">
                  <span class="k">更新时间</span>
                  <span class="v">{{ formatTime(activeAccount.updatedAt) }}</span>
                </div>
                <div v-if="isTempMail(activeAccount)" class="fact">
                  <span class="k">临时邮箱租期</span>
                  <span class="v">{{ tempRemainText(activeAccount) }}</span>
                </div>
              </div>
              <div v-if="activeAccount.verifyMessage" class="note" :class="'is-' + verifyTone(activeAccount.verifyStatus)">
                <ReIcon name="InfoFilled" :size="13" />
                <span>{{ activeAccount.verifyMessage }}</span>
              </div>
            </div>

            <!-- 凭据 -->
            <div v-else-if="mainTab === 'credential'" class="pane">
              <div class="cred-card" :class="hasCredential ? 'is-ok' : 'is-warn'">
                <ReIcon :name="hasCredential ? 'CircleCheckFilled' : 'WarningFilled'" :size="16" />
                <div>
                  <b>{{ hasCredential ? '凭据已配置' : '凭据尚未配置' }}</b>
                  <span>
                    {{
                      referencedSecretId
                        ? '校验时使用引用的密钥；账号自身凭据可留空。敏感值只进不出，编辑时留空表示不修改。'
                        : '敏感值只进不出：读取接口只返回「是否已配置」，编辑时留空表示不修改。'
                    }}
                  </span>
                </div>
              </div>

              <!-- 引用的密钥：回填「密钥管理」里那一条的真实数据，不重复展示裸 UUID -->
              <div class="secret-ref">
                <header class="secret-ref-head">
                  <ProviderLogo v-if="referencedSecret" :provider="referencedSecret.provider" :size="18" />
                  <b>{{ referencedSecret ? referencedSecret.name || '未命名密钥' : '未引用密钥管理里的密钥' }}</b>
                  <ReTag
                    v-if="referencedSecret"
                    :type="referencedSecret.status === 0 ? 'info' : 'success'"
                    effect="plain"
                    size="small"
                  >
                    {{ referencedSecret.status === 0 ? '停用' : '启用' }}
                  </ReTag>
                  <button type="button" class="secret-ref-link" @click="openSecretAssets">去密钥管理</button>
                </header>
                <div v-if="referencedSecret" class="facts">
                  <div class="fact is-wide">
                    <span class="k">AccessKeyId</span>
                    <span class="v mono">{{ referencedSecret.accessKey || '—' }}</span>
                  </div>
                  <div class="fact">
                    <span class="k">用途 / 厂商</span>
                    <span class="v">{{ providerLabel(referencedSecret.provider) }}</span>
                  </div>
                  <div class="fact">
                    <span class="k">AccessKeySecret</span>
                    <span class="v">{{ referencedSecret.hasSecret ? '已配置' : '待补' }}</span>
                  </div>
                  <div class="fact">
                    <span class="k">Region</span>
                    <span class="v">{{ referencedSecret.region || '—' }}</span>
                  </div>
                  <div class="fact">
                    <span class="k">服务地址</span>
                    <span class="v mono">{{ referencedSecret.endpoint || '厂商默认' }}</span>
                  </div>
                  <div class="fact is-wide">
                    <span class="k">最近更新</span>
                    <span class="v">{{ formatTime(referencedSecret.updatedAt) }}</span>
                  </div>
                </div>
                <p v-else class="pane-tip">
                  {{
                    referencedSecretId
                      ? `引用的密钥不在可见列表里（${referencedSecretId}），可能已被删除或不属于当前账号。`
                      : '凭据直接填在本账号里；要在多处复用同一把钥匙，去「密钥管理」新建后在编辑弹窗里选择。'
                  }}
                </p>
              </div>

              <h4 class="pane-title">类型专属配置</h4>
              <div v-if="configRows.length" class="facts">
                <div v-for="item in configRows" :key="item.key" class="fact">
                  <span class="k">{{ item.label }}</span>
                  <span class="v">{{ item.value }}</span>
                </div>
              </div>
              <ReEmpty v-else :image-size="48" description="暂无类型专属配置" />
            </div>

            <!-- 校验记录 -->
            <div v-else-if="mainTab === 'verify'" class="pane">
              <div class="verify-hero" :class="'is-' + verifyTone(activeAccount.verifyStatus)">
                <ReIcon :name="activeAccount.verifyStatus === 'VALID' ? 'CircleCheckFilled' : 'WarningFilled'" :size="18" />
                <div>
                  <b>校验{{ verifyLabel(activeAccount.verifyStatus) }}</b>
                  <span>{{ activeAccount.verifyAt ? `最近一次：${formatTime(activeAccount.verifyAt)}` : '尚未执行过校验' }}</span>
                </div>
              </div>
              <div class="facts">
                <div class="fact">
                  <span class="k">校验状态</span>
                  <span class="v">
                    <ReTag :type="verifyTagType(activeAccount.verifyStatus)" effect="plain">
                      {{ verifyLabel(activeAccount.verifyStatus) }}
                    </ReTag>
                  </span>
                </div>
                <div class="fact">
                  <span class="k">最近校验</span>
                  <span class="v">{{ formatTime(activeAccount.verifyAt) }}</span>
                </div>
                <div class="fact">
                  <span class="k">校验说明</span>
                  <span class="v">{{ activeAccount.verifyMessage || '—' }}</span>
                </div>
              </div>
              <p class="pane-tip">校验会真实发起一次握手：API 密钥校验凭据是否存在，邮箱走 IMAP 登录，短信走模板可用性检查。</p>
            </div>

            <!-- 收信箱 -->
            <div v-else class="pane">
              <div class="mail-hero">
                <span class="main-mark" :class="'is-' + kindClass(activeAccount.kind)">
                  <ReIcon :name="kindIcon(activeAccount.kind)" :size="16" />
                </span>
                <div class="mail-copy">
                  <b>{{ activeAccount.kind === 'EMAIL' ? '收 / 发信箱' : '短信收 / 发信箱' }}</b>
                  <span>
                    {{ activeAccount.kind === 'EMAIL'
                      ? '收信走 IMAP，发信走 SMTP，全部在应用内抽屉完成，不跳外部门户；打开即拉最新邮件，未读排在前面。'
                      : '发信走 MessagePush；未安装短信收信能力时抽屉会给出降级提示。' }}
                  </span>
                </div>
                <ReButton type="primary" icon="Message" show-text @click="openConsole(activeAccount)">
                  {{ activeAccount.kind === 'EMAIL' ? '打开邮箱' : '打开短信' }}
                </ReButton>
              </div>
              <div class="facts">
                <div class="fact">
                  <span class="k">登录名</span>
                  <span class="v mono">{{ activeAccount.username || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">服务地址</span>
                  <span class="v mono">{{ endpointText(activeAccount) || '—' }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部操作：纯图标 + 悬浮提示，不占横向版面（文字只在 tooltip 里出现） -->
          <footer class="main-foot">
            <ReButton
              text
              :show-text="false"
              icon="CircleCheck"
              :icon-size="28"
              tip="校验凭据"
              :loading="verifyingId === activeAccount.accountId"
              @click="onVerify(activeAccount)"
            />
            <ReButton
              text
              :show-text="false"
              icon="EditPen"
              :icon-size="28"
              tip="编辑"
              @click="openEdit(activeAccount)"
            />
            <ReButton
              text
              :show-text="false"
              icon="Key"
              :icon-size="28"
              tip="收录到密钥管理"
              @click="collectSecretOf(activeAccount)"
            />
            <ReButton
              text
              :show-text="false"
              icon="CopyDocument"
              :icon-size="28"
              tip="复制为新账号"
              @click="duplicateAccount(activeAccount)"
            />
            <ReButton
              v-if="hasMailbox(activeAccount)"
              text
              :show-text="false"
              :icon="activeAccount.kind === 'EMAIL' ? 'Message' : 'ChatDotRound'"
              :icon-size="28"
              :tip="activeAccount.kind === 'EMAIL' ? '打开邮箱' : '打开短信'"
              @click="openConsole(activeAccount)"
            />
            <span class="foot-gap"></span>
            <ReButton
              text
              :show-text="false"
              icon="Star"
              :icon-size="28"
              tip="设为默认"
              :disabled="Boolean(activeAccount.defaultAccount)"
              @click="setDefault(activeAccount)"
            />
            <ReButton
              text
              :show-text="false"
              :icon="activeAccount.status === 0 ? 'SwitchButton' : 'TurnOff'"
              :icon-size="28"
              :tip="activeAccount.status === 0 ? '启用' : '停用'"
              @click="toggleStatus(activeAccount)"
            />
            <ReButton
              text
              :show-text="false"
              icon="Delete"
              :icon-size="28"
              type="danger"
              tip="删除"
              @click="remove(activeAccount)"
            />
          </footer>
        </template>

        <!-- ---------- AppKey ---------- -->
        <template v-else-if="segment === 'APPKEY' && activeAppKey">
          <header class="main-head">
            <span class="main-mark is-key"><ReIcon name="Key" :size="16" /></span>
            <div class="main-title">
              <b :title="activeAppKey.name">{{ activeAppKey.name || '未命名密钥' }}</b>
              <span>访问密钥 · {{ activeAppKey.owner ? '仅本人' : '部署级共享' }}</span>
            </div>
            <ReTag :type="activeAppKey.status === 0 ? 'info' : 'success'" effect="plain" size="small">
              {{ activeAppKey.status === 0 ? '停用' : '启用' }}
            </ReTag>
          </header>

          <nav class="main-tabs" role="tablist" aria-label="访问密钥信息">
            <button
              v-for="tab in keyTabs"
              :key="tab.value"
              type="button"
              role="tab"
              class="main-tab"
              :class="{ 'is-on': mainTab === tab.value }"
              :aria-selected="mainTab === tab.value"
              @click="mainTab = tab.value"
            >
              {{ tab.label }}
            </button>
          </nav>

          <div class="main-body">
            <div v-if="mainTab === 'overview'" class="pane">
              <div class="facts">
                <div class="fact">
                  <span class="k">AppKey</span>
                  <span class="v mono">{{ activeAppKey.appKey || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">状态</span>
                  <span class="v">{{ activeAppKey.status === 0 ? '停用' : '启用' }}</span>
                </div>
                <div class="fact">
                  <span class="k">归属</span>
                  <span class="v">{{ activeAppKey.owner ? `仅本人（${activeAppKey.owner}）` : '部署级共享' }}</span>
                </div>
                <div class="fact">
                  <span class="k">创建时间</span>
                  <span class="v">{{ formatTime(activeAppKey.createdAt) }}</span>
                </div>
                <div class="fact">
                  <span class="k">更新时间</span>
                  <span class="v">{{ formatTime(activeAppKey.updatedAt) }}</span>
                </div>
              </div>
            </div>

            <div v-else-if="mainTab === 'secret'" class="pane">
              <div class="cred-card is-ok">
                <ReIcon name="Lock" :size="16" />
                <div>
                  <b>Secret 只进不出</b>
                  <span>Secret 明文只在创建或轮换后弹一次，之后任何读取接口都不会再返回。</span>
                </div>
              </div>
              <div class="facts">
                <div class="fact">
                  <span class="k">AppKey</span>
                  <span class="v mono">{{ activeAppKey.appKey || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">IP 白名单</span>
                  <span class="v mono">{{ activeAppKey.ipWhitelist || '不限制' }}</span>
                </div>
                <div class="fact">
                  <span class="k">备注</span>
                  <span class="v">{{ activeAppKey.remark || '—' }}</span>
                </div>
              </div>
            </div>

            <div v-else class="pane">
              <div class="facts">
                <div class="fact">
                  <span class="k">最近使用</span>
                  <span class="v">{{ formatTime(activeAppKey.lastUsedAt) }}</span>
                </div>
                <div class="fact">
                  <span class="k">过期</span>
                  <span class="v" :class="{ 'is-bad': expired(activeAppKey.expireAt) }">
                    {{ expireText(activeAppKey.expireAt) }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- 与账号 / Cookie 一致：底部只留图标 + 悬浮提示，文字只在 tooltip 里出现 -->
          <footer class="main-foot">
            <ReButton
              text
              :show-text="false"
              icon="CopyDocument"
              :icon-size="28"
              tip="复制 AppKey"
              @click="copyKey(activeAppKey)"
            />
            <ReButton
              text
              :show-text="false"
              icon="Refresh"
              :icon-size="28"
              tip="轮换 Secret（旧 Secret 立即失效）"
              @click="rotateKey(activeAppKey)"
            />
            <ReButton
              text
              :show-text="false"
              icon="EditPen"
              :icon-size="28"
              tip="编辑"
              @click="openEditKey(activeAppKey)"
            />
            <span class="foot-gap"></span>
            <ReButton
              text
              :show-text="false"
              :icon="activeAppKey.status === 0 ? 'SwitchButton' : 'TurnOff'"
              :icon-size="28"
              :tip="activeAppKey.status === 0 ? '启用' : '停用'"
              @click="toggleKeyStatus(activeAppKey)"
            />
            <ReButton
              text
              :show-text="false"
              icon="Delete"
              :icon-size="28"
              type="danger"
              tip="删除"
              @click="removeKey(activeAppKey)"
            />
          </footer>
        </template>

        <!-- ---------- Cookie ---------- -->
        <template v-else-if="segment === 'COOKIE' && activeCookie">
          <header class="main-head">
            <span class="main-mark is-cookie"><ReIcon name="Postcard" :size="16" /></span>
            <div class="main-title">
              <b :title="activeCookie.name">{{ activeCookie.name || '未命名 Cookie' }}</b>
              <span>Cookie · {{ activeCookie.domain || '—' }}{{ activeCookie.path || '/' }}</span>
            </div>
            <ReTag :type="activeCookie.status === 0 ? 'info' : 'success'" effect="plain" size="small">
              {{ activeCookie.status === 0 ? '停用' : '启用' }}
            </ReTag>
          </header>

          <nav class="main-tabs" role="tablist" aria-label="Cookie 信息">
            <button
              v-for="tab in cookieTabs"
              :key="tab.value"
              type="button"
              role="tab"
              class="main-tab"
              :class="{ 'is-on': mainTab === tab.value }"
              :aria-selected="mainTab === tab.value"
              @click="mainTab = tab.value"
            >
              {{ tab.label }}
            </button>
          </nav>

          <div class="main-body">
            <div v-if="mainTab === 'overview'" class="pane">
              <div class="facts">
                <div class="fact">
                  <span class="k">域名</span>
                  <span class="v mono">{{ activeCookie.domain || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">路径</span>
                  <span class="v mono">{{ activeCookie.path || '/' }}</span>
                </div>
                <div class="fact">
                  <span class="k">来源</span>
                  <span class="v">{{ activeCookie.source || '—' }}</span>
                </div>
                <div class="fact">
                  <span class="k">归属</span>
                  <span class="v">{{ activeCookie.owner ? `仅本人（${activeCookie.owner}）` : '部署级共享' }}</span>
                </div>
                <div class="fact">
                  <span class="k">创建时间</span>
                  <span class="v">{{ formatTime(activeCookie.createdAt) }}</span>
                </div>
                <div class="fact">
                  <span class="k">更新时间</span>
                  <span class="v">{{ formatTime(activeCookie.updatedAt) }}</span>
                </div>
              </div>
            </div>

            <div v-else-if="mainTab === 'content'" class="pane">
              <div class="cred-card" :class="activeCookie.hasValue ? 'is-ok' : 'is-warn'">
                <ReIcon :name="activeCookie.hasValue ? 'CircleCheckFilled' : 'WarningFilled'" :size="16" />
                <div>
                  <b>{{ activeCookie.hasValue ? 'Cookie 内容已配置' : 'Cookie 内容尚未配置' }}</b>
                  <span>内容只进不出：读取接口只返回「是否已配置」，编辑时留空表示不修改。</span>
                </div>
              </div>
              <div class="facts">
                <div class="fact">
                  <span class="k">备注</span>
                  <span class="v">{{ activeCookie.remark || '—' }}</span>
                </div>
              </div>
            </div>

            <div v-else class="pane">
              <div class="facts">
                <div class="fact">
                  <span class="k">过期</span>
                  <span class="v" :class="{ 'is-bad': expired(activeCookie.expireAt) }">
                    {{ expireText(activeCookie.expireAt) }}
                  </span>
                </div>
                <div class="fact">
                  <span class="k">状态</span>
                  <span class="v">{{ activeCookie.status === 0 ? '停用' : '启用' }}</span>
                </div>
              </div>
            </div>
          </div>

          <footer class="main-foot">
            <ReButton
              text
              :show-text="false"
              icon="EditPen"
              :icon-size="28"
              tip="编辑 Cookie"
              @click="openEditCookie(activeCookie)"
            />
            <span class="foot-gap"></span>
            <ReButton
              text
              :show-text="false"
              :icon="activeCookie.status === 0 ? 'SwitchButton' : 'TurnOff'"
              :icon-size="28"
              :tip="activeCookie.status === 0 ? '启用' : '停用'"
              @click="toggleCookieStatus(activeCookie)"
            />
            <ReButton
              text
              :show-text="false"
              icon="Delete"
              :icon-size="28"
              type="danger"
              tip="删除"
              @click="removeCookie(activeCookie)"
            />
          </footer>
        </template>

        <div v-else class="main-blank">
          <ReEmpty :image-size="64" :description="blankHint" />
          <p>单击左侧一条资产查看详情，双击打开完整编辑，右键有完整操作。</p>
        </div>
      </section>

      <!-- 右键菜单（索引行与表格共用一套动作） -->
      <div v-if="ctxMenu" class="ctx-layer" @click="ctxMenu = null" @contextmenu.prevent="ctxMenu = null">
        <ul class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }" @click.stop>
          <li
            v-for="item in ctxMenu.items"
            :key="item.key"
            class="ctx-item"
            :class="{ 'is-divided': item.divided, 'is-disabled': item.disabled }"
            @click="runCtx(item)"
          >
            <ReIcon v-if="item.icon" :name="ctxIcon(item.icon)" :size="13" />
            <span>{{ item.label }}</span>
          </li>
        </ul>
      </div>

    </div>
    </div>

    <!-- 新增 / 编辑账号 -->
    <AccountFormDialog v-model="formVisible" :account="editing" :draft="draft" @saved="onSaved" />

    <!-- 新增 / 编辑 AppKey -->
    <AppKeyDialog v-model="keyFormVisible" :app-key="editingKey" @saved="onKeySaved" />

    <!-- 新增 / 编辑 Cookie -->
    <CookieDialog v-model="cookieFormVisible" :cookie="editingCookie" @saved="onCookieSaved" />

    <!-- 收 / 发信箱：邮箱走 IMAP / SMTP，短信走 MessagePush -->
    <MailboxDrawer
      v-model="mailboxVisible"
      :account="mailboxAccount"
      :console-url="mailboxAccount ? accountConsoleUrl(mailboxAccount) : ''"
      @unread="onMailboxUnread"
    />

    <!-- 轮换后的一次性明文 -->
    <ReDialog v-model="secretVisible" title="新的 Secret" width="520px" append-to-body destroy-on-close>
      <div class="secret-box">
        <p class="secret-tip">「{{ secretName }}」的 Secret 已轮换，<b>关闭后无法再次查看</b>。</p>
        <div class="secret-value">{{ secretValue }}</div>
      </div>
      <template #footer>
        <ReButton @click="secretVisible = false">关闭</ReButton>
        <ReButton type="primary" @click="copySecret">复制 Secret</ReButton>
      </template>
    </ReDialog>
  </div>
</template>

<script setup lang="ts" name="ScAccountAssets">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  ChatDotRound,
  CircleCheck,
  CopyDocument,
  Delete,
  EditPen,
  Message,
  Refresh,
  Star,
  SwitchButton,
} from '@element-plus/icons-vue';
import { message } from '@repo/core';
import ReButton from '@repo/ui/re-button';
import ReDialog from '@repo/ui/re-dialog';
import ReEmpty from '@repo/ui/re-empty';
import ReIcon from '@repo/ui/re-icon';
import ReTag from '@repo/ui/re-tag';
import ReRailSplit, { readRailWidth } from '@repo/ui/re-rail-split';

import AccountFormDialog from '../components/AccountFormDialog.vue';
import MailboxDrawer from '../components/MailboxDrawer.vue';
import AppKeyDialog from './components/AppKeyDialog.vue';
import CookieDialog from './components/CookieDialog.vue';
import ProviderLogo from './components/ProviderLogo.vue';
import {
  batchDeleteAccounts,
  batchUpdateAccountStatus,
  batchVerifyAccounts,
  claimTempMailbox,
  collectAccountSecret,
  deleteAccount,
  deleteAccountAppKey,
  deleteAccountCookie,
  destroyTempMailbox,
  isTempMail,
  listAccountAppKeys,
  listAccountCookies,
  listAccounts,
  listAccountSecrets,
  rotateAccountAppKey,
  syncAccountSmsTemplates,
  tempMailExpireAt,
  updateAccount,
  updateAccountAppKey,
  updateAccountCookie,
  verifyAccount,
  type AccountBatchResult,
  type AccountKind,
  type AccountVerifyStatus,
  type SysAccount,
  type SysAccountAppKey,
  type SysAccountCookie,
  type SysAccountSecret,
} from '../api/account';
import { providerLabel } from '../secretProviders';
import { accountConsoleUrl } from '../utils/accountLinks';
import { confirmThen } from '../../shared/confirmThen';
import { copyText } from '../../shared/clipboard';

/**
 * 账号资产 —— monitor 的统一账号工作台（设计方向 B）。
 *
 * <p><b>为什么是工作台而不是纯台账</b>：账号总量小，但「凭据有没有配、校验过没有、
 * 归谁所有、能不能直接打开控制台」这些问题每次都要看。纯台账逼用户点开弹窗逐条确认；
 * 工作台把选中行的关键状态常驻在右侧检查器里，单击即看，双击才进完整详情。</p>
 *
 * <p><b>三类资产</b>：统一账号（设备密钥 / 邮箱 / 短信）、AppKey、Cookie 共用一套
 * KPI 带 + 分段页签 + 检查器，因为它们回答的是同一类问题——「这个凭据还能不能用」。
 * 三张表各自保留自己的列，不做字段对齐，避免把不同语义的字段硬塞进同一列。
 * 厂商密钥（AccessKey / AccessKeySecret）不进本页，由独立的「密钥管理」页按类型维护，
 * 账号表单引用时按类型选择。</p>
 *
 * <p><b>敏感值</b>：账号 secret / AppKey secret / Cookie 值都只进不出。列表与检查器
 * 只显示「是否已配置」，AppKey 的明文只在创建或轮换后弹一次。</p>
 */

/** 当前分段：账号三种 + AppKey + Cookie（密钥由「密钥管理」独立页维护） */
type Segment = 'ALL' | AccountKind | 'APPKEY' | 'COOKIE' | 'TEMP_MAIL';

/** 账号状态筛选 */
type StatusFilter = 'all' | 'on' | 'pending' | 'invalid';

/** 路由：凭据页签里跳到「密钥管理」看被引用的那条密钥 */
const router = useRouter();

/** 页面视图：账号资产是本页主体，模板管理与消息模板是账号管理下的子功能 */
/** 统一账号 */
const accountList = ref<SysAccount[]>([]);
/** 访问密钥 */
const appKeyList = ref<SysAccountAppKey[]>([]);
/** Cookie */
const cookieList = ref<SysAccountCookie[]>([]);
/** 密钥管理里的厂商密钥（账号 config.secretId 引用其中的一条） */
const secretList = ref<SysAccountSecret[]>([]);

/** 加载中 */
const loading = ref(false);
/** 账号接口错误 */
const loadError = ref('');
/** AppKey / Cookie 接口错误（分段内联提示） */
const appKeyError = ref('');
const cookieError = ref('');

/** 当前分段 */
const segment = ref<Segment>('ALL');
/** 搜索词 */
const keyword = ref('');

/* ---------- 左侧索引宽度（可拖拽，偏好存本地） ---------- */
const RAIL_WIDTH_KEY = 'account.rail.width.v1';
const railWidth = ref(readRailWidth(RAIL_WIDTH_KEY, 304));
/** 账号状态筛选 */
const statusFilter = ref<StatusFilter>('all');

/** 选中的账号 / AppKey / Cookie */
const selectedAccountId = ref('');
const selectedAppKeyId = ref('');
const selectedCookieId = ref('');

/** 正在校验的账号 */
const verifyingId = ref<string | null>(null);

/** 账号表格勾选的账号标识 */
const selectedAccountIds = ref<string[]>([]);
/** 批量选择模式：进模式后单击索引行即勾选，不必再按 Ctrl / 右键 */
const batchMode = ref(false);
/** 批量操作进行中 */
const batchBusy = ref(false);
/** 临时邮箱领取中 */
const tempBusy = ref(false);

/** 账号新增 / 编辑 */
const formVisible = ref(false);
const editing = ref<SysAccount | null>(null);
/** 复制来源（新增时预填，不进入编辑态） */
const draft = ref<SysAccount | null>(null);
/** AppKey 新增 / 编辑 */
const keyFormVisible = ref(false);
const editingKey = ref<SysAccountAppKey | null>(null);
/** Cookie 新增 / 编辑 */
const cookieFormVisible = ref(false);
const editingCookie = ref<SysAccountCookie | null>(null);
/** 一次性明文 */
const secretVisible = ref(false);
const secretValue = ref('');
const secretName = ref('');

/** 收 / 发信箱抽屉（邮箱 IMAP、短信 MessagePush） */
const mailboxVisible = ref(false);
const mailboxAccount = ref<SysAccount | null>(null);
/** 账号 → 收件箱未读数（抽屉打开后回填，用于页签角标；不预拉免得多开 IMAP 会话） */
const unreadByAccount = reactive<Record<string, number>>({});

/** 取某账号的未读数 */
function unreadOf(accountId?: string): number {
  return accountId ? unreadByAccount[accountId] || 0 : 0;
}

/** 抽屉回报的未读数 */
function onMailboxUnread(value: number): void {
  const id = mailboxAccount.value?.accountId;
  if (!id) return;
  unreadByAccount[id] = value || 0;
}

/** 是否为账号类分段 */
const isAccountSegment = computed(
  () =>
    segment.value === 'ALL' ||
    segment.value === 'API_KEY' ||
    segment.value === 'EMAIL' ||
    segment.value === 'SMS' ||
    segment.value === 'TEMP_MAIL',
);

/** 新建按钮文案 */
const createLabel = computed(() => {
  if (segment.value === 'APPKEY') return '新增 AppKey';
  if (segment.value === 'COOKIE') return '新增 Cookie';
  if (segment.value === 'TEMP_MAIL') return '领取临时邮箱';
  return '新增账号';
});

/** 搜索占位 */
const searchPlaceholder = computed(() => {
  if (segment.value === 'APPKEY') return '搜索名称 / AppKey / 备注';
  if (segment.value === 'COOKIE') return '搜索名称 / 域名 / 来源';
  if (segment.value === 'TEMP_MAIL') return '搜索地址 / 名称';
  return '搜索名称 / 编码 / 登录名 / 地址';
});

/** 详情区空态提示 */
const blankHint = computed(() => {
  if (segment.value === 'APPKEY') return '选择一条访问密钥查看详情';
  if (segment.value === 'COOKIE') return '选择一条 Cookie 查看详情';
  if (segment.value === 'TEMP_MAIL') return '选择一条临时邮箱查看详情';
  return '选择一条账号查看详情';
});

/** 分段内联错误 */
const segmentError = computed(() => {
  if (segment.value === 'APPKEY') return appKeyError.value;
  if (segment.value === 'COOKIE') return cookieError.value;
  return '';
});

// ==================== 概览计数 ====================

/** 启用账号数 */
const enabledCount = computed(() => accountList.value.filter(row => row.status !== 0).length);
/** 缺凭据账号数 */
const missingCount = computed(() => accountList.value.filter(row => !row.hasSecret).length);
/** 校验失败账号数 */
const invalidCount = computed(() => accountList.value.filter(row => row.verifyStatus === 'INVALID').length);
/** 临时邮箱数 */
const tempMailCount = computed(() => accountList.value.filter(row => isTempMail(row)).length);
/** 关联渠道数（有 channelKey 的账号覆盖的渠道去重） */
const channelCount = computed(
  () => new Set(accountList.value.map(row => row.channelKey).filter(Boolean)).size,
);

/** 状态筛选文案 */
const statusFilterLabel = computed(() => {
  const map: Record<StatusFilter, string> = {
    all: '全部',
    on: '启用中',
    pending: '凭据待补',
    invalid: '校验失败',
  };
  return map[statusFilter.value];
});

/** KPI 单元格 */
const kpiCells = computed<
  Array<{ key: string; value: number; label: string; tone?: string; on: boolean; hint: string; run: () => void }>
>(() => [
  {
    key: 'accounts',
    value: accountList.value.length,
    label: '统一账号',
    on: isAccountSegment.value && segment.value === 'ALL' && statusFilter.value === 'all',
    hint: '查看全部账号',
    run: () => {
      setSegment('ALL');
      statusFilter.value = 'all';
    },
  },
  {
    key: 'tempmail',
    value: tempMailCount.value,
    label: '临时邮箱',
    tone: tempMailCount.value > 0 ? 'is-ok' : '',
    on: segment.value === 'TEMP_MAIL',
    hint: 'mail.tm 临时收信地址，到期自动清理',
    run: () => setSegment('TEMP_MAIL'),
  },
  {
    key: 'enabled',
    value: enabledCount.value,
    label: '启用',
    tone: enabledCount.value > 0 ? 'is-ok' : '',
    on: isAccountSegment.value && statusFilter.value === 'on',
    hint: '只看启用中的账号',
    run: () => toggleStatusFilter('on'),
  },
  {
    key: 'missing',
    value: missingCount.value,
    label: '凭据待补',
    tone: missingCount.value > 0 ? 'is-warn' : '',
    on: isAccountSegment.value && statusFilter.value === 'pending',
    hint: '只看缺凭据的账号',
    run: () => toggleStatusFilter('pending'),
  },
  {
    key: 'invalid',
    value: invalidCount.value,
    label: '校验失败',
    tone: invalidCount.value > 0 ? 'is-bad' : '',
    on: isAccountSegment.value && statusFilter.value === 'invalid',
    hint: '只看校验失败的账号',
    run: () => toggleStatusFilter('invalid'),
  },
  {
    key: 'channels',
    value: channelCount.value,
    label: '关联渠道',
    on: false,
    hint: '账号覆盖的渠道数',
    run: () => {
      setSegment('ALL');
      statusFilter.value = 'all';
      keyword.value = '';
    },
  },
  {
    key: 'appkeys',
    value: appKeyList.value.length,
    label: 'AppKey',
    on: segment.value === 'APPKEY',
    hint: '外部访问密钥',
    run: () => setSegment('APPKEY'),
  },
  {
    key: 'cookies',
    value: cookieList.value.length,
    label: 'Cookie',
    on: segment.value === 'COOKIE',
    hint: '免登录会话 Cookie',
    run: () => setSegment('COOKIE'),
  },
]);

// ==================== 列表数据 ====================

/** 账号筛选结果 */
const filteredAccounts = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return accountList.value.filter(row => {
    if (isAccountSegment.value && segment.value !== 'ALL') {
      if (segment.value === 'TEMP_MAIL') {
        if (!isTempMail(row)) return false;
      } else if (row.kind !== segment.value) {
        return false;
      }
    }
    if (statusFilter.value === 'on' && row.status === 0) return false;
    if (statusFilter.value === 'pending' && row.hasSecret) return false;
    if (statusFilter.value === 'invalid' && row.verifyStatus !== 'INVALID') return false;
    if (!text) return true;
    return `${row.name || ''} ${row.code || ''} ${row.username || ''} ${row.endpoint || ''}`
      .toLowerCase()
      .includes(text);
  });
});

/** AppKey 筛选结果 */
const filteredAppKeys = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (!text) return appKeyList.value;
  return appKeyList.value.filter(row =>
    `${row.name || ''} ${row.appKey || ''} ${row.remark || ''}`.toLowerCase().includes(text),
  );
});

/** Cookie 筛选结果 */
const filteredCookies = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (!text) return cookieList.value;
  return cookieList.value.filter(row =>
    `${row.name || ''} ${row.domain || ''} ${row.source || ''} ${row.remark || ''}`.toLowerCase().includes(text),
  );
});

/**
 * 索引里的账号结果。
 *
 * <p>和右侧详情不同：索引永远列全部分组，不受当前 {@code segment} 影响，
 * 否则点中一条后左侧就只剩该组，切换别的分组还得回顶栏。</p>
 */
const indexAccounts = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return accountList.value.filter(row => {
    if (statusFilter.value === 'on' && row.status === 0) return false;
    if (statusFilter.value === 'pending' && row.hasSecret) return false;
    if (statusFilter.value === 'invalid' && row.verifyStatus !== 'INVALID') return false;
    if (!text) return true;
    return `${row.name || ''} ${row.code || ''} ${row.username || ''} ${row.endpoint || ''}`
      .toLowerCase()
      .includes(text);
  });
});

/** 索引分组行 */
interface IndexRow {
  /** 行标识 */
  id: string;
  /** 主标题 */
  name: string;
  /** 副标题 */
  sub: string;
  /** 右侧时间 */
  meta: string;
  /** 右侧状态标签 */
  tag: string;
  /** 标签语义 */
  tagTone: 'ok' | 'warn' | 'bad' | 'idle';
  /** 左侧圆点语义 */
  tone: 'ok' | 'warn' | 'bad' | 'idle';
  /** 启停状态（0 停用；无启停概念时为 undefined） */
  status?: number;
  /** 是否选中 */
  selected: boolean;
  /** 是否加入批量选择 */
  checked: boolean;
  /** 是否参与批量选择（账号行才可勾选，AppKey / Cookie 不进批量接口） */
  checkable: boolean;
}

/** 索引分组 */
interface IndexGroup {
  /** 分组标识，同时是分段值 */
  key: Segment;
  /** 分组标题 */
  label: string;
  /** 分组图标 */
  icon: string;
  /** 分组行 */
  rows: IndexRow[];
}

/**
 * 账号行的圆点语义。
 *
 * <p>停用优先于校验状态：一条已停用的账号即便校验有效，也不该显示成绿的。</p>
 */
function accountTone(row: SysAccount): 'ok' | 'warn' | 'bad' | 'idle' {
  if (row.status === 0) return 'idle';
  if (!row.hasSecret) return 'warn';
  return verifyTone(row.verifyStatus);
}

/**
 * 临时邮箱剩余时间文案。
 *
 * @param account 账号
 * @returns 如「剩 23h」「已过期」
 */
function tempRemainText(account: SysAccount): string {
  const expireAt = tempMailExpireAt(account);
  if (!expireAt) return '—';
  const left = expireAt - Date.now();
  if (left <= 0) return '已过期';
  const hours = Math.floor(left / 3600000);
  if (hours >= 1) return `剩 ${hours}h`;
  return `剩 ${Math.max(1, Math.floor(left / 60000))}m`;
}

/** 左侧分组索引 */
const indexGroups = computed<IndexGroup[]>(() => {
  const groups: IndexGroup[] = [];
  const kinds: Array<{ kind: AccountKind; label: string; icon: string }> = [
    { kind: 'API_KEY', label: 'API 密钥', icon: 'Key' },
    { kind: 'EMAIL', label: '邮箱账号', icon: 'Message' },
    { kind: 'SMS', label: '短信账号', icon: 'ChatDotRound' },
  ];

  for (const def of kinds) {
    const rows = indexAccounts.value
      .filter(row => row.kind === def.kind && !isTempMail(row))
      .map<IndexRow>(row => ({
        id: row.accountId,
        name: row.name || row.code || '未命名账号',
        sub: `${row.vendor || kindLabel(row.kind)}${row.username ? ` · ${row.username}` : ''}`,
        meta: formatShort(row.updatedAt),
        tag: row.status === 0 ? '停用' : verifyLabel(row.verifyStatus),
        tagTone: row.status === 0 ? 'idle' : row.hasSecret ? verifyTone(row.verifyStatus) : 'warn',
        tone: accountTone(row),
        status: row.status,
        selected: selectedAccountId.value === row.accountId,
        checked: selectedAccountIds.value.includes(row.accountId),
        checkable: true,
      }));
    groups.push({ key: def.kind, label: def.label, icon: def.icon, rows });
  }

  groups.push({
    key: 'TEMP_MAIL',
    label: '临时邮箱',
    icon: 'Promotion',
    rows: indexAccounts.value
      .filter(row => isTempMail(row))
      .map<IndexRow>(row => {
        const expiredMail = tempMailExpireAt(row) > 0 && tempMailExpireAt(row) < Date.now();
        return {
          id: row.accountId,
          name: row.username || row.name || '临时邮箱',
          sub: `${row.vendor || 'mail.tm'} · 临时收信`,
          meta: tempRemainText(row),
          tag: expiredMail ? '过期' : '临时',
          tagTone: expiredMail ? 'bad' : 'ok',
          tone: expiredMail ? 'bad' : 'ok',
          status: row.status,
          selected: selectedAccountId.value === row.accountId,
          checked: selectedAccountIds.value.includes(row.accountId),
          checkable: true,
        };
      }),
  });

  if (statusFilter.value === 'all') {
    groups.push({
      key: 'APPKEY',
      label: 'AppKey',
      icon: 'Key',
      rows: filteredAppKeys.value.map<IndexRow>(row => ({
        id: row.appKeyId,
        name: row.name || row.appKey || '未命名密钥',
        sub: row.appKey || '—',
        meta: formatShort(row.lastUsedAt),
        tag: row.status === 0 ? '停用' : expired(row.expireAt) ? '过期' : '启用',
        tagTone: row.status === 0 ? 'idle' : expired(row.expireAt) ? 'bad' : 'ok',
        tone: row.status === 0 ? 'idle' : expired(row.expireAt) ? 'bad' : 'ok',
        status: row.status,
        selected: selectedAppKeyId.value === row.appKeyId,
        checked: false,
        checkable: false,
      })),
    });
    groups.push({
      key: 'COOKIE',
      label: 'Cookie',
      icon: 'Postcard',
      rows: filteredCookies.value.map<IndexRow>(row => ({
        id: row.cookieId,
        name: row.name || row.domain || '未命名 Cookie',
        sub: `${row.domain || '—'}${row.path || '/'}`,
        meta: formatShort(row.updatedAt),
        tag: row.status === 0 ? '停用' : expired(row.expireAt) ? '过期' : '启用',
        tagTone: row.status === 0 ? 'idle' : expired(row.expireAt) ? 'bad' : 'ok',
        tone: row.status === 0 ? 'idle' : expired(row.expireAt) ? 'bad' : 'ok',
        status: row.status,
        selected: selectedCookieId.value === row.cookieId,
        checked: false,
        checkable: false,
      })),
    });
  }
  return groups;
});

/** 索引里的总行数 */
const tableCount = computed(() => indexGroups.value.reduce((sum, group) => sum + group.rows.length, 0));

/** 索引每次滚动加载的行数 */
const RAIL_PAGE = 30;
/** 已加载行数（跨分组累计） */
const railLimit = ref(RAIL_PAGE);

/**
 * 索引可见分组：按分组顺序累计切片。
 *
 * <p>保留原有分组结构（含空分组），只截断每组行数；{@code totalRows} 是切片前的
 * 行数，模板用它区分「本来就没有行」和「有行但还没滚动加载到」。</p>
 */
const visibleIndexGroups = computed(() => {
  let remaining = railLimit.value;
  return indexGroups.value.map((group) => {
    const totalRows = group.rows.length;
    const rows = remaining > 0 ? group.rows.slice(0, remaining) : [];
    remaining -= rows.length;
    return { ...group, rows, totalRows };
  });
});

/** 已加载行数 */
const loadedIndexRows = computed(() =>
  visibleIndexGroups.value.reduce((sum, group) => sum + group.rows.length, 0),
);

/** 索引滚动到底部时加载更多 */
function onIndexScroll(e: Event): void {
  const el = e.target as HTMLElement;
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 24 && railLimit.value < tableCount.value) {
    railLimit.value += RAIL_PAGE;
  }
}

/** 搜索 / 状态筛选 / 切分段时回到第一屏 */
watch([keyword, statusFilter, segment], () => {
  railLimit.value = RAIL_PAGE;
});

/** 选中账号 */
const activeAccount = computed(
  () => accountList.value.find(row => row.accountId === selectedAccountId.value) ?? null,
);
/** 选中 AppKey */
const activeAppKey = computed(
  () => appKeyList.value.find(row => row.appKeyId === selectedAppKeyId.value) ?? null,
);
/** 选中 Cookie */
const activeCookie = computed(
  () => cookieList.value.find(row => row.cookieId === selectedCookieId.value) ?? null,
);

/**
 * 是否具备收发信箱（仅邮箱 / 短信账号）。
 *
 * <p>这里不再依赖「控制台地址能否解析」：邮箱与短信都能在应用内打开真实信箱，
 * 所以只要种类对就允许进入；控制台地址只作为抽屉里的兜底提示。</p>
 *
 * @param row 账号
 * @returns 是否可以打开信箱
 */
function hasMailbox(row: SysAccount | null | undefined): boolean {
  return row?.kind === 'EMAIL' || row?.kind === 'SMS';
}

/**
 * 账号引用的密钥（{@code config.secretId} 指向「密钥管理」里的那一条）。
 *
 * <p>凭据页签要回答的是「这条账号到底在用哪把钥匙」，所以这里回填密钥本体的
 * 名称 / 厂商 / AccessKeyId / 状态；密钥被删或没权限看时退回 null，页面仍显示 secretId。</p>
 */
const referencedSecret = computed(() => {
  const config = (activeAccount.value?.config || {}) as Record<string, unknown>;
  const secretId = String(config.secretId ?? '').trim();
  if (!secretId) return null;
  return secretList.value.find(item => item.secretId === secretId) ?? null;
});

/** 引用密钥的原始 id（密钥不在可见列表里时的兜底展示） */
const referencedSecretId = computed(() => {
  const config = (activeAccount.value?.config || {}) as Record<string, unknown>;
  return String(config.secretId ?? '').trim();
});

/**
 * 凭据是否「可用」。
 *
 * <p>两种来源都算配置过：账号自身填的明文凭据（{@code hasSecret}），
 * 或 {@code config.secretId} 引用的密钥库条目。只看 {@code hasSecret} 会让
 * 纯引用型账号被误判成「凭据尚未配置」。</p>
 */
const hasCredential = computed(() => Boolean(activeAccount.value?.hasSecret || referencedSecretId.value));

/** 跳到密钥管理查看被引用的那条密钥 */
function openSecretAssets(): void {
  void router.push({ name: 'SecretAssets' });
}

/** 类型专属配置展示行（{@code secretId} 已由「引用的密钥」单独展示，不重复列 UUID） */
const configRows = computed(() => {
  const config = (activeAccount.value?.config || {}) as Record<string, unknown>;
  const labels: Record<string, string> = {
    port: '端口',
    ssl: 'SSL / TLS',
    from: '默认发件人',
    signName: '短信签名',
    templateCode: '模板编码',
    region: '区域',
    webUrl: '控制台地址',
  };
  return Object.entries(config)
    .filter(([key, value]) => key !== 'secretId' && value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({
      key,
      label: labels[key] || key,
      value: typeof value === 'boolean' ? (value ? '是' : '否') : String(value),
    }));
});

// ==================== 加载 ====================

/**
 * 加载四份数据。
 *
 * <p>用 {@code allSettled}：AppKey / Cookie / 密钥接口在未登录时可能 403，
 * 不该因此把账号列表一起打成空白页。密钥只用来回填「凭据」页签里被引用的那一条，
 * 取不到就退化成只显示 {@code secretId}，不影响账号本体。</p>
 */
async function loadAll(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  const [accounts, keys, cookies, secrets] = await Promise.allSettled([
    listAccounts(),
    listAccountAppKeys(),
    listAccountCookies(),
    listAccountSecrets(),
  ]);
  if (accounts.status === 'fulfilled') {
    accountList.value = accounts.value ?? [];
  } else {
    loadError.value = errText(accounts.reason);
    message.error('账号加载失败：' + loadError.value);
  }
  if (keys.status === 'fulfilled') {
    appKeyList.value = keys.value ?? [];
    appKeyError.value = '';
  } else {
    appKeyError.value = '访问密钥加载失败：' + errText(keys.reason);
  }
  if (cookies.status === 'fulfilled') {
    cookieList.value = cookies.value ?? [];
    cookieError.value = '';
  } else {
    cookieError.value = 'Cookie 加载失败：' + errText(cookies.reason);
  }
  secretList.value = secrets.status === 'fulfilled' ? (secrets.value ?? []) : [];
  loading.value = false;
  // 数据整体替换后旧勾选已经没有对应行，继续保留只会让「已选 N 项」与实际不符
  clearAccountSelection();
  syncSelection();
}

/**
 * 把选中项对齐到当前分段的第一行。
 *
 * <p>刷新后保留原选中项；它被删掉或换了分段时才重新挑一条，
 * 避免每次刷新都把检查器弹回第一条。</p>
 */
function syncSelection(): void {
  if (isAccountSegment.value) {
    const rows = filteredAccounts.value;
    if (!rows.some(row => row.accountId === selectedAccountId.value)) {
      selectedAccountId.value = rows[0]?.accountId ?? '';
    }
    return;
  }
  if (segment.value === 'APPKEY') {
    const rows = filteredAppKeys.value;
    if (!rows.some(row => row.appKeyId === selectedAppKeyId.value)) {
      selectedAppKeyId.value = rows[0]?.appKeyId ?? '';
    }
    return;
  }
  const rows = filteredCookies.value;
  if (!rows.some(row => row.cookieId === selectedCookieId.value)) {
    selectedCookieId.value = rows[0]?.cookieId ?? '';
  }
}

// ==================== 分段与筛选 ====================

/**
 * 切换分段。
 *
 * @param value 目标分段
 */
function setSegment(value: Segment): void {
  segment.value = value;
  statusFilter.value = 'all';
  clearAccountSelection();
  syncSelection();
}

/**
 * 切换账号状态筛选（再点一次回到全部）。
 *
 * @param value 目标状态
 */
function toggleStatusFilter(value: StatusFilter): void {
  if (!isAccountSegment.value) segment.value = 'ALL';
  statusFilter.value = statusFilter.value === value ? 'all' : value;
  syncSelection();
}

watch([segment, keyword, statusFilter], syncSelection);

// 换了资产就回到概览：留在「收信箱」去看一条密钥会得到奇怪的空面板
watch([selectedAccountId, selectedAppKeyId, selectedCookieId], () => {
  mainTab.value = 'overview';
});

// ==================== 行选中 ====================

/** 详情页签取值：账号 / AppKey / Cookie 三套页签共用一个游标 */
type TabValue =
  | 'overview'
  | 'credential'
  | 'verify'
  | 'mailbox'
  | 'secret'
  | 'usage'
  | 'content'
  | 'expire';

/** 当前账号页签 */
const mainTab = ref<TabValue>('overview');

/** 账号页签项 */
const accountTabs: Array<{ value: TabValue; label: string }> = [
  { value: 'overview', label: '概览' },
  { value: 'credential', label: '凭据' },
  { value: 'verify', label: '校验记录' },
  { value: 'mailbox', label: '收信箱' },
];

/** AppKey 页签项 */
const keyTabs: Array<{ value: TabValue; label: string }> = [
  { value: 'overview', label: '概览' },
  { value: 'secret', label: 'Secret' },
  { value: 'usage', label: '使用与期限' },
];

/** Cookie 页签项 */
const cookieTabs: Array<{ value: TabValue; label: string }> = [
  { value: 'overview', label: '概览' },
  { value: 'content', label: '内容' },
  { value: 'expire', label: '期限' },
];

/** 右键菜单状态 */
const ctxMenu = ref<{
  /** 横坐标 */
  x: number;
  /** 纵坐标 */
  y: number;
  /** 分组 */
  group: IndexGroup;
  /** 行 */
  row: IndexRow;
  /** 菜单项 */
  items: Array<{ key: string; label: string; icon?: string; divided?: boolean; disabled?: boolean }>;
} | null>(null);

/**
 * 定位到某一行：只切右侧详情，不动批量勾选。
 *
 * <p>右键菜单与单击共用这段：右键只负责弹出菜单，勾选交给菜单项，
 * 否则在批量模式里右键一行会先被勾上，菜单首项跟着变成「取消批量选择」。</p>
 *
 * @param group 分组
 * @param row 行
 */
function focusIndexRow(group: IndexGroup, row: IndexRow): void {
  segment.value = group.key;
  if (group.key === 'APPKEY') {
    selectedAppKeyId.value = row.id;
  } else if (group.key === 'COOKIE') {
    selectedCookieId.value = row.id;
  } else {
    selectedAccountId.value = row.id;
  }
  mainTab.value = 'overview';
}

/**
 * 选中索引行。
 *
 * <p>单击只切换右侧详情，不改左侧分组结构：点一条 AppKey 不会让账号分组消失。
 * 批量选择模式下（点工具栏「批量选择」进入）单击账号行即勾选 / 取消勾选；
 * 未进模式时按住 Ctrl / ⌘ 单击也能直接进模式并勾上当前行。</p>
 *
 * @param group 分组
 * @param row 行
 * @param event 鼠标事件
 */
function selectIndexRow(group: IndexGroup, row: IndexRow, event?: MouseEvent): void {
  focusIndexRow(group, row);
  if (group.key === 'APPKEY' || group.key === 'COOKIE') return;
  if (batchMode.value || event?.ctrlKey || event?.metaKey || event?.shiftKey) {
    batchMode.value = true;
    toggleBatchSelect(row.id);
  }
}

/**
 * 双击索引行：直接进完整编辑。
 *
 * @param group 分组
 * @param row 行
 */
function openIndexRow(group: IndexGroup, row: IndexRow): void {
  if (group.key === 'APPKEY') {
    openEditKey(appKeyList.value.find(item => item.appKeyId === row.id) ?? null);
    return;
  }
  if (group.key === 'COOKIE') {
    openEditCookie(cookieList.value.find(item => item.cookieId === row.id) ?? null);
    return;
  }
  if (group.key === 'TEMP_MAIL') {
    openConsole(accountList.value.find(item => item.accountId === row.id) ?? null);
    return;
  }
  openEdit(accountList.value.find(item => item.accountId === row.id) ?? null);
}

/**
 * 打开索引行右键菜单。
 *
 * @param event 鼠标事件
 * @param group 分组
 * @param row 行
 */
function openIndexMenu(event: MouseEvent, group: IndexGroup, row: IndexRow): void {
  focusIndexRow(group, row);
  const items = group.key === 'APPKEY'
    ? [
        { key: 'copy-key', label: '复制 AppKey', icon: 'CopyDocument' },
        { key: 'rotate-key', label: '轮换 Secret', icon: 'Refresh', divided: true },
        { key: 'edit', label: '编辑密钥', icon: 'EditPen' },
        { key: 'status', label: row.status === 0 ? '启用密钥' : '停用密钥', icon: 'SwitchButton' },
        { key: 'delete', label: '删除密钥', icon: 'Delete', divided: true },
      ]
    : group.key === 'COOKIE'
      ? [
          { key: 'edit', label: '编辑 Cookie', icon: 'EditPen' },
          { key: 'status', label: row.status === 0 ? '启用 Cookie' : '停用 Cookie', icon: 'SwitchButton', divided: true },
          { key: 'delete', label: '删除 Cookie', icon: 'Delete' },
        ]
      : group.key === 'TEMP_MAIL'
        ? [
            { key: 'open-console', label: '打开邮箱', icon: 'Message' },
            { key: 'copy-address', label: '复制邮箱地址', icon: 'CopyDocument' },
            { key: 'destroy-temp', label: '销毁临时邮箱', icon: 'Delete', divided: true },
          ]
          : [
          { key: 'batch-select', label: selectedAccountIds.value.includes(row.id) ? '取消批量选择' : '批量选择', icon: 'Select' },
          { key: 'edit', label: '编辑账号', icon: 'EditPen' },
          { key: 'verify', label: '校验凭据', icon: 'CircleCheck' },
          { key: 'collect-secret', label: '收录到密钥管理', icon: 'Key' },
          ...(group.key === 'EMAIL' || group.key === 'SMS'
            ? [{ key: 'open-console', label: group.key === 'EMAIL' ? '打开邮箱' : '打开短信', icon: 'Message', divided: true }]
            : []),
          ...(group.key === 'SMS'
            ? [{ key: 'sync-sms-templates', label: '同步短信模板', icon: 'Refresh' }]
            : []),
          { key: 'duplicate', label: '复制为新账号', icon: 'CopyDocument', divided: true },
          { key: 'copy-code', label: '复制账号编码', icon: 'CopyDocument' },
          {
            key: 'default',
            label: '设为同种类默认',
            icon: 'Star',
          },
          { key: 'status', label: row.status === 0 ? '启用账号' : '停用账号', icon: 'SwitchButton' },
          { key: 'delete', label: '删除账号', icon: 'Delete', divided: true },
        ];
  ctxMenu.value = { x: event.clientX, y: event.clientY, group, row, items };
}

/**
 * 右键菜单图标名。
 *
 * @param name 图标名
 * @returns 同名图标
 */
function ctxIcon(name: string): string {
  return name;
}

/**
 * 执行右键菜单项。
 *
 * @param item 菜单项
 */
function runCtx(item: { key: string; disabled?: boolean }): void {
  const menu = ctxMenu.value;
  ctxMenu.value = null;
  if (!menu || item.disabled) return;
  const { group, row } = menu;
  if (group.key === 'APPKEY') {
    const target = appKeyList.value.find(entry => entry.appKeyId === row.id);
    if (!target) return;
    if (item.key === 'copy-key') void copyKey(target);
    else if (item.key === 'rotate-key') void rotateKey(target);
    else if (item.key === 'edit') openEditKey(target);
    else if (item.key === 'status') void toggleKeyStatus(target);
    else if (item.key === 'delete') void removeKey(target);
    return;
  }
  if (group.key === 'COOKIE') {
    const target = cookieList.value.find(entry => entry.cookieId === row.id);
    if (!target) return;
    if (item.key === 'edit') openEditCookie(target);
    else if (item.key === 'status') void toggleCookieStatus(target);
    else if (item.key === 'delete') void removeCookie(target);
    return;
  }
  const target = accountList.value.find(entry => entry.accountId === row.id);
  if (!target) return;
  if (item.key === 'batch-select') toggleBatchSelect(target.accountId);
  else if (item.key === 'edit') openEdit(target);
  else if (item.key === 'verify') void onVerify(target);
  else if (item.key === 'collect-secret') void collectSecretOf(target);
  else if (item.key === 'open-console') openConsole(target);
  else if (item.key === 'sync-sms-templates') void syncSmsTemplatesOf(target);
  else if (item.key === 'copy-address') void copyAddress(target);
  else if (item.key === 'destroy-temp') void destroyTemp(target);
  else if (item.key === 'copy-code') void copyCode(target);
  else if (item.key === 'duplicate') duplicateAccount(target);
  else if (item.key === 'default') void setDefault(target);
  else if (item.key === 'status') void toggleStatus(target);
  else if (item.key === 'delete') void remove(target);
}

// ==================== 批量操作 ====================

/** 清空勾选并退出批量模式（数据刷新 / 切分段 / 批量执行后必须清，避免把已消失的 id 提交上去） */
function clearAccountSelection(): void {
  selectedAccountIds.value = [];
  batchMode.value = false;
}

/** 切换批量选择模式：进模式后单击索引行即勾选，取消则连同勾选一起清掉 */
function toggleBatchMode(): void {
  if (batchMode.value) clearAccountSelection();
  else batchMode.value = true;
}

/**
 * 切换一行账号的批量勾选状态。
 *
 * <p>工具栏「批量选择」、右键菜单项与 Ctrl / ⌘ 单击走同一条路径：只改勾选集，不动右侧详情。
 * 勾选不该顺带把详情滚回顶部，否则连着勾十条要点十次回来。</p>
 *
 * @param accountId 账号标识
 */
function toggleBatchSelect(accountId: string): void {
  const ids = selectedAccountIds.value;
  if (ids.includes(accountId)) {
    selectedAccountIds.value = ids.filter(id => id !== accountId);
    return;
  }
  selectedAccountIds.value = [...ids, accountId];
  // 勾上任何一条就进模式：右键菜单 / Ctrl 单击进入后，后续单击行可以直接继续勾
  batchMode.value = true;
}

/**
 * 批量结果提示。
 *
 * <p>部分成功必须如实说明：后端逐条执行、失败不回滚，只报「成功」会让用户
 * 以为全部生效。失败明细只在确有失败时展开，避免正常路径刷屏。</p>
 *
 * @param action 动作名（如「批量校验」）
 * @param result 批量结果
 * @returns 是否全部成功
 */
function reportBatch(action: string, result: AccountBatchResult): boolean {
  const succeeded = result?.succeeded ?? 0;
  const failed = result?.failed ?? 0;
  if (failed === 0) {
    message.success(`${action}完成：${succeeded} 项`);
    return true;
  }
  const detail = (result?.failures || [])
    .slice(0, 3)
    .map(item => `${shortId(item.accountId)}：${item.message}`)
    .join('；');
  const more = (result?.failures?.length || 0) > 3 ? ` 等 ${result.failures.length} 项` : '';
  if (succeeded === 0) {
    message.error(`${action}全部失败：${detail}${more}`);
  } else {
    message.warning(`${action}部分成功：成功 ${succeeded} 项，失败 ${failed} 项（${detail}${more}）`);
  }
  return false;
}

/**
 * 账号短标识（失败明细里用，避免一屏全是 UUID）。
 *
 * @param accountId 账号标识
 * @returns 前 8 位
 */
function shortId(accountId: string): string {
  return accountId ? accountId.slice(0, 8) : '未知账号';
}

/**
 * 批量校验勾选账号。
 */
async function batchVerify(): Promise<void> {
  const ids = selectedAccountIds.value;
  if (!ids.length || batchBusy.value) return;
  batchBusy.value = true;
  try {
    const result = await batchVerifyAccounts(ids);
    reportBatch('批量校验', result);
    clearAccountSelection();
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '批量校验失败');
  } finally {
    batchBusy.value = false;
  }
}

/**
 * 批量启用 / 停用勾选账号。
 *
 * @param status 目标状态（1 启用 / 0 停用）
 */
async function batchSetStatus(status: number): Promise<void> {
  const ids = selectedAccountIds.value;
  if (!ids.length || batchBusy.value) return;
  batchBusy.value = true;
  try {
    const result = await batchUpdateAccountStatus(ids, status);
    reportBatch(status === 1 ? '批量启用' : '批量停用', result);
    clearAccountSelection();
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '批量更新状态失败');
  } finally {
    batchBusy.value = false;
  }
}

/**
 * 批量删除勾选账号（逻辑删除）。
 */
async function batchRemove(): Promise<void> {
  const ids = selectedAccountIds.value;
  if (!ids.length || batchBusy.value) return;
  const confirmed = await confirmThen(
    message.confirm,
    `确认删除勾选的 ${ids.length} 个账号？引用它们的设备 / 模板会失去凭据来源。`,
    '批量删除账号',
  );
  if (!confirmed) return;
  batchBusy.value = true;
  try {
    const result = await batchDeleteAccounts(ids);
    reportBatch('批量删除', result);
    clearAccountSelection();
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '批量删除失败');
  } finally {
    batchBusy.value = false;
  }
}

// ==================== 账号操作 ====================

/** 打开新增账号 */
function openCreate(): void {
  if (segment.value === 'APPKEY') {
    editingKey.value = null;
    keyFormVisible.value = true;
    return;
  }
  if (segment.value === 'COOKIE') {
    editingCookie.value = null;
    cookieFormVisible.value = true;
    return;
  }
  if (segment.value === 'TEMP_MAIL') {
    void claimTemp();
    return;
  }
  editing.value = null;
  draft.value = null;
  formVisible.value = true;
}

/**
 * 领取一个临时邮箱。
 *
 * <p>没有表单：地址、密码、租期都由服务端生成，用户只需要「拿到一个能用一次
 * 的收信地址」。领取后自动选中新行并打开地址复制提示。</p>
 */
async function claimTemp(): Promise<void> {
  if (tempBusy.value) return;
  tempBusy.value = true;
  try {
    const saved = await claimTempMailbox();
    await loadAll();
    selectedAccountId.value = saved.accountId;
    segment.value = 'TEMP_MAIL';
    message.success(`已领取临时邮箱：${saved.username || ''}`);
  } catch (e: any) {
    message.error(e?.message || '领取临时邮箱失败');
  } finally {
    tempBusy.value = false;
  }
}

/**
 * 销毁临时邮箱（远端 + 本地）。
 *
 * @param row 临时邮箱账号
 */
async function destroyTemp(row: SysAccount): Promise<void> {
  const confirmed = await confirmThen(
    message.confirm,
    `确认销毁临时邮箱「${row.username || row.name}」？该地址会立即失效，已收邮件一并丢弃。`,
    '销毁临时邮箱',
  );
  if (!confirmed) return;
  try {
    await destroyTempMailbox(row.accountId);
    message.success('临时邮箱已销毁');
    if (selectedAccountId.value === row.accountId) selectedAccountId.value = '';
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '销毁临时邮箱失败');
  }
}

/** 复制临时邮箱地址 */
async function copyAddress(row: SysAccount): Promise<void> {
  const ok = await copyText(row.username || '');
  if (ok) message.success('邮箱地址已复制');
  else message.error('复制失败');
}

/** 打开账号编辑 */
function openEdit(row: SysAccount | null): void {
  if (!row) return;
  editing.value = row;
  draft.value = null;
  formVisible.value = true;
}

/**
 * 复制为新账号。
 *
 * <p>把原账号作为预填草稿交给新增弹窗：不进入编辑态、不带凭据、不继承默认标记。
 * 「照着同一个服务商再建一个邮箱」是高频操作，逐字段重填成本高且容易填错。</p>
 *
 * @param row 源账号
 */
function duplicateAccount(row: SysAccount | null): void {
  if (!row) return;
  editing.value = null;
  draft.value = row;
  formVisible.value = true;
}

/** 账号保存后刷新并回指 */
function onSaved(saved: SysAccount): void {
  if (saved?.accountId) selectedAccountId.value = saved.accountId;
  void loadAll();
}

/**
 * 打开收 / 发信箱。
 *
 * <p>不再跳向外部门户：邮箱走 IMAP 收信 + SMTP 发信，短信走 MessagePush，
 * 全部在应用内抽屉里完成，因此这里只负责把账号交给抽屉。</p>
 *
 * @param row 账号
 */
function openConsole(row: SysAccount | null): void {
  if (!row) return;
  if (!hasMailbox(row)) {
    message.warning('该账号种类没有收 / 发信箱');
    return;
  }
  mailboxAccount.value = row;
  mailboxVisible.value = true;
}

/**
 * 校验凭据。
 *
 * @param row 账号
 */
async function onVerify(row: SysAccount | null): Promise<void> {
  if (!row || verifyingId.value) return;
  verifyingId.value = row.accountId;
  try {
    const result = await verifyAccount(row.accountId);
    if (result.status === 'VALID') {
      message.success(result.message || '校验通过');
    } else if (result.status === 'INVALID') {
      message.error(result.message || '校验失败');
    } else {
      message.info(result.message || '该账号种类暂未安装校验能力');
    }
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '校验失败');
  } finally {
    verifyingId.value = null;
  }
}

/**
 * 把账号内联凭据收录到密钥管理（反哺）。
 *
 * <p>账号里直接填写的 AccessKey / Secret 常常散落多份，收录后统一存进
 * {@code sys_account_secret} 并按账号种类标注类型（API_KEY→通用 API /
 * EMAIL→邮件 / SMS→短信），账号改为引用这条密钥；之后再改密钥只需改一处。</p>
 *
 * @param row 账号
 */
async function collectSecretOf(row: SysAccount | null): Promise<void> {
  if (!row) return;
  try {
    const saved = await collectAccountSecret(row.accountId);
    message.success(`已收录到密钥管理：${saved.name || ''}`);
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '收录到密钥管理失败');
  }
}

/**
 * 同步短信账号对应厂商的云端模板。
 *
 * <p>厂商由账号 vendor 决定，后端走 MessagePush SPI 的 listTemplates()，
 * 前端不区分阿里 / 腾讯 / 百度。</p>
 *
 * @param row 短信账号
 */
async function syncSmsTemplatesOf(row: SysAccount | null): Promise<void> {
  if (!row) return;
  try {
    const result = await syncAccountSmsTemplates(row.accountId);
    message.success(
      `模板同步完成：新增 ${result.created}，更新 ${result.updated}，跳过 ${result.skipped}（${result.provider}）`,
    );
  } catch (e: any) {
    message.error(e?.message || '同步短信模板失败');
  }
}

/**
 * 复制账号编码。
 *
 * @param row 账号
 */
async function copyCode(row: SysAccount): Promise<void> {
  const ok = await copyText(row.code);
  if (ok) message.success('账号编码已复制');
  else message.error('复制失败');
}

/**
 * 设为同种类默认账号（后端会清掉同种类的其它默认）。
 *
 * @param row 账号
 */
async function setDefault(row: SysAccount): Promise<void> {
  try {
    await updateAccount(row.accountId, { ...row, defaultAccount: true });
    message.success('已设为同种类默认账号');
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '设置失败');
  }
}

/**
 * 启用 / 停用账号。
 *
 * @param row 账号
 */
async function toggleStatus(row: SysAccount): Promise<void> {
  const next = row.status === 0 ? 1 : 0;
  try {
    await updateAccount(row.accountId, { ...row, status: next });
    message.success(next === 1 ? '账号已启用' : '账号已停用');
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '状态更新失败');
  }
}

/**
 * 删除账号（逻辑删除）。
 *
 * @param row 账号
 */
async function remove(row: SysAccount): Promise<void> {
  const confirmed = await confirmThen(
    message.confirm,
    `确认删除账号「${row.name || row.code || row.accountId}」？引用它的设备 / 模板会失去凭据来源。`,
    '删除账号',
  );
  if (!confirmed) return;
  try {
    await deleteAccount(row.accountId);
    message.success('账号已删除');
    if (selectedAccountId.value === row.accountId) selectedAccountId.value = '';
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '删除失败');
  }
}

// ==================== AppKey 操作 ====================

/** 打开 AppKey 编辑 */
function openEditKey(row: SysAccountAppKey | null): void {
  editingKey.value = row;
  keyFormVisible.value = true;
}

/** AppKey 保存后刷新并回指 */
function onKeySaved(): void {
  void loadAll();
}

/**
 * 复制 AppKey。
 *
 * @param row 访问密钥
 */
async function copyKey(row: SysAccountAppKey): Promise<void> {
  const ok = await copyText(row.appKey);
  if (ok) message.success('AppKey 已复制');
  else message.error('复制失败');
}

/**
 * 轮换 Secret（明文只在响应里出现一次）。
 *
 * @param row 访问密钥
 */
async function rotateKey(row: SysAccountAppKey): Promise<void> {
  const confirmed = await confirmThen(
    message.confirm,
    `确认轮换「${row.name || row.appKey}」的 Secret？旧 Secret 立即失效，调用方需要同步更新。`,
    '轮换 Secret',
  );
  if (!confirmed) return;
  try {
    const saved = await rotateAccountAppKey(row.appKeyId);
    if (saved?.plainSecret) {
      secretName.value = saved.name || row.name || row.appKey || '';
      secretValue.value = saved.plainSecret;
      secretVisible.value = true;
    } else {
      message.success('Secret 已轮换');
    }
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '轮换失败');
  }
}

/**
 * 启用 / 停用 AppKey。
 *
 * @param row 访问密钥
 */
async function toggleKeyStatus(row: SysAccountAppKey): Promise<void> {
  const next = row.status === 0 ? 1 : 0;
  try {
    await updateAccountAppKey(row.appKeyId, {
      appKeyId: row.appKeyId,
      name: row.name,
      status: next,
      expireAt: row.expireAt ?? null,
      ipWhitelist: row.ipWhitelist ?? null,
      remark: row.remark ?? null,
    });
    message.success(next === 1 ? '访问密钥已启用' : '访问密钥已停用');
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '状态更新失败');
  }
}

/**
 * 删除 AppKey。
 *
 * @param row 访问密钥
 */
async function removeKey(row: SysAccountAppKey): Promise<void> {
  const confirmed = await confirmThen(
    message.confirm,
    `确认删除访问密钥「${row.name || row.appKey}」？调用方会立即失去访问权限。`,
    '删除访问密钥',
  );
  if (!confirmed) return;
  try {
    await deleteAccountAppKey(row.appKeyId);
    message.success('访问密钥已删除');
    if (selectedAppKeyId.value === row.appKeyId) selectedAppKeyId.value = '';
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '删除失败');
  }
}

// ==================== Cookie 操作 ====================

/** 打开 Cookie 编辑 */
function openEditCookie(row: SysAccountCookie | null): void {
  editingCookie.value = row;
  cookieFormVisible.value = true;
}

/** Cookie 保存后刷新 */
function onCookieSaved(): void {
  void loadAll();
}

/**
 * 启用 / 停用 Cookie。
 *
 * @param row Cookie
 */
async function toggleCookieStatus(row: SysAccountCookie): Promise<void> {
  const next = row.status === 0 ? 1 : 0;
  try {
    await updateAccountCookie(row.cookieId, {
      cookieId: row.cookieId,
      name: row.name,
      domain: row.domain,
      path: row.path,
      source: row.source ?? null,
      status: next,
      expireAt: row.expireAt ?? null,
      remark: row.remark ?? null,
      cookieValue: null,
    });
    message.success(next === 1 ? 'Cookie 已启用' : 'Cookie 已停用');
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '状态更新失败');
  }
}

/**
 * 删除 Cookie。
 *
 * @param row Cookie
 */
async function removeCookie(row: SysAccountCookie): Promise<void> {
  const confirmed = await confirmThen(
    message.confirm,
    `确认删除 Cookie「${row.name || row.domain}」？引用它的免登录渠道会失去会话。`,
    '删除 Cookie',
  );
  if (!confirmed) return;
  try {
    await deleteAccountCookie(row.cookieId);
    message.success('Cookie 已删除');
    if (selectedCookieId.value === row.cookieId) selectedCookieId.value = '';
    await loadAll();
  } catch (e: any) {
    message.error(e?.message || '删除失败');
  }
}

// ==================== 一次性明文 ====================

/** 复制轮换后的 Secret */
async function copySecret(): Promise<void> {
  const ok = await copyText(secretValue.value);
  if (ok) message.success('Secret 已复制');
  else message.error('复制失败，请手动选中文本');
}

// ==================== 展示工具 ====================

/**
 * 账号种类中文名。
 *
 * @param kind 种类
 * @returns 中文名
 */
function kindLabel(kind?: AccountKind): string {
  return ({ API_KEY: 'API 密钥', EMAIL: '邮箱账号', SMS: '短信账号' } as Record<string, string>)[kind || ''] || '未知';
}

/**
 * 种类图标。
 *
 * @param kind 种类
 * @returns ReIcon 名称
 */
function kindIcon(kind?: AccountKind): string {
  if (kind === 'EMAIL') return 'Message';
  if (kind === 'SMS') return 'ChatDotRound';
  return 'Key';
}

/**
 * 种类色块。
 *
 * @param kind 种类
 * @returns CSS 修饰名
 */
function kindClass(kind?: AccountKind): string {
  if (kind === 'EMAIL') return 'mail';
  if (kind === 'SMS') return 'sms';
  return 'key';
}

/**
 * 校验状态中文名。
 *
 * @param status 状态
 * @returns 中文名
 */
function verifyLabel(status?: AccountVerifyStatus): string {
  return ({ VALID: '有效', INVALID: '失败', UNKNOWN: '未校验' } as Record<string, string>)[status || ''] || '未校验';
}

/**
 * 校验状态标签色。
 *
 * @param status 状态
 * @returns ReTag type
 */
function verifyTagType(status?: AccountVerifyStatus): 'success' | 'danger' | 'info' {
  if (status === 'VALID') return 'success';
  if (status === 'INVALID') return 'danger';
  return 'info';
}

/**
 * 校验状态语义色（检查器用）。
 *
 * @param status 状态
 * @returns CSS 修饰名
 */
function verifyTone(status?: AccountVerifyStatus): 'ok' | 'bad' | 'idle' {
  if (status === 'VALID') return 'ok';
  if (status === 'INVALID') return 'bad';
  return 'idle';
}

/**
 * 服务地址展示：有端口时拼在地址后面。
 *
 * @param row 账号
 * @returns 展示文本
 */
function endpointText(row: SysAccount): string {
  const port = (row.config as Record<string, unknown> | undefined)?.port;
  return port ? `${row.endpoint}:${port}` : row.endpoint || '';
}

/**
 * 是否已过期。
 *
 * @param at 毫秒时间戳
 * @returns 有值且早于现在为 true
 */
function expired(at?: number | null): boolean {
  return Boolean(at) && Number(at) <= Date.now();
}

/**
 * 到期文案。
 *
 * @param at 毫秒时间戳
 * @returns 长期 / 已过期 / N 天后到期
 */
function expireText(at?: number | null): string {
  if (!at) return '长期';
  const diff = Number(at) - Date.now();
  if (!Number.isFinite(diff) || diff <= 0) return '已过期';
  const hours = Math.ceil(diff / 3_600_000);
  return hours < 48 ? `${hours} 小时后过期` : `${Math.ceil(diff / 86_400_000)} 天后过期`;
}

/**
 * 毫秒时间格式化为「MM-DD HH:mm」。
 *
 * @param value 毫秒时间戳
 * @returns 短时间；空值返回 —
 */
function formatShort(value?: number | null): string {
  if (!value) return '—';
  const date = new Date(value);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * 毫秒时间格式化。
 *
 * @param value 毫秒时间戳
 * @returns yyyy-MM-dd HH:mm:ss；空值返回 —
 */
function formatTime(value?: number | null): string {
  if (!value) return '—';
  const date = new Date(value);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * 异常文案。
 *
 * @param error 异常
 * @returns 可读文本
 */
function errText(error: unknown): string {
  if (error instanceof Error) return error.message;
  return String(error ?? '未知错误');
}

/**
 * Esc 退出批量选择。
 *
 * <p>批量模式开着但一条没勾时，用户的第一反应是按 Esc 而不是回来点「取消」；
 * 这里只在模式确实开着时处理，不拦截弹窗自己的 Esc。</p>
 *
 * @param event 键盘事件
 */
function onWindowKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && batchMode.value) clearAccountSelection();
}

onMounted(() => {
  void loadAll();
  window.addEventListener('keydown', onWindowKeydown);
});
onBeforeUnmount(() => window.removeEventListener('keydown', onWindowKeydown));
</script>

<style scoped>
/* 沿用 koi 令牌（--bg-container / --border-subtle / --text-* / --radius-*），
   与主机资产、设备资产、渠道管理同一套底色、圆角与状态色。 */
.account-assets {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 10px;
}

/* ---------- 内容舞台 ---------- */
.stage {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* ---------- 工作台：左索引 + 右详情 ---------- */
.ws {
  flex: 1;
  min-height: 0;
  display: flex;
  /* 间距交给分栏手柄本身，避免 gap + 手柄叠出一段空档 */
  gap: 0;
  align-items: stretch;
}

/* ---------- 左侧分组索引 ---------- */
.idx {
  /* 宽度由分栏手柄写入 --idx-width（可拖拽，存本地偏好） */
  width: var(--idx-width, 304px);
  /* 批量条按索引栏自身宽度换行，而不是按视口宽度 */
  container-type: inline-size;
  container-name: rail;
  flex: none;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.idx-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 10px 8px;
  flex: none;
}
.idx-tools .search {
  flex: 1;
  min-width: 0;
}
.icon-btn {
  flex: none;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-container);
  color: var(--text-tertiary);
  cursor: pointer;
  transition: color 0.15s var(--ease-out), border-color 0.15s var(--ease-out);
}
.icon-btn:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}
/* 批量选择开关打开时的按下态：和图标的灰底区分开，一眼能看出现在点行是「勾选」不是「查看」 */
.icon-btn.is-on {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

/* 行内统计：数字即筛选入口，压缩成两行，不抢索引版面 */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin: 0 10px 8px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--border-subtle);
  flex: none;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  padding: 5px 8px;
  border: 0;
  background: var(--bg-container);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s var(--ease-out);
}
.stat:hover {
  background: var(--bg-fill);
}
.stat.is-on {
  background: var(--el-color-primary-light-9);
}
.stat b {
  font-size: 15px;
  font-weight: 650;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}
.stat b.is-ok {
  color: var(--status-success);
}
.stat b.is-warn {
  color: var(--status-warning);
}
.stat b.is-bad {
  color: var(--status-error);
}
.stat span {
  font-size: 10.5px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.idx-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 10px 8px;
  padding: 5px 9px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--radius-sm);
  background: var(--el-color-primary-light-9);
  font-size: 11px;
  color: var(--el-color-primary-dark-2);
  flex: none;
}
.idx-filter button {
  margin-left: auto;
  border: 0;
  background: none;
  font: inherit;
  font-size: 11px;
  color: inherit;
  cursor: pointer;
  text-decoration: underline;
}
.idx-error {
  margin: 0 10px 8px;
  padding: 6px 9px;
  border: 1px dashed var(--sev-p0-border);
  border-radius: var(--radius-sm);
  font-size: 11px;
  color: var(--sev-p0-text);
  flex: none;
}

.idx-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 6px 6px;
}
.idx-group + .idx-group {
  margin-top: 4px;
}
.idx-group-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 6px 4px;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-tertiary);
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--bg-container);
}
.idx-group-head b {
  margin-left: auto;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--bg-fill);
  font-size: 10px;
  font-weight: 600;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
.idx-row {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s var(--ease-out), border-color 0.15s var(--ease-out);
}
.idx-row:hover {
  background: var(--bg-fill);
}
.idx-row.is-on {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}
/* 已勾选：底色 + 名称变主色，扫一眼就能数清勾了哪几条（不只靠左侧 14px 方块） */
.idx-row.is-checked {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}
.idx-row.is-checked .idx-name {
  color: var(--el-color-primary-dark-2);
}
.idx-row.is-off .idx-name {
  color: var(--text-tertiary);
}
.dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-quaternary, var(--border-default));
}
.dot.is-ok {
  background: var(--status-success);
}
.dot.is-warn {
  background: var(--status-warning);
}
.dot.is-bad {
  background: var(--status-error);
}
.idx-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.idx-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.idx-sub {
  font-size: 10.5px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.idx-meta {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.idx-tag {
  padding: 0 5px;
  border-radius: 999px;
  font-size: 10px;
  line-height: 16px;
  background: var(--bg-fill);
  color: var(--text-tertiary);
  white-space: nowrap;
}
.idx-tag.is-ok {
  background: var(--sev-p3-bg);
  color: var(--sev-p3-text);
}
.idx-tag.is-warn {
  background: var(--sev-p2-bg);
  color: var(--sev-p2-text);
}
.idx-tag.is-bad {
  background: var(--sev-p0-bg);
  color: var(--sev-p0-text);
}
.idx-time {
  font-size: 10px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
/* 批量模式下的勾选框：替掉左侧状态圆点，点整行即切换 */
.idx-box {
  flex: none;
  width: 14px;
  height: 14px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-default);
  border-radius: 3px;
  background: var(--bg-container);
  color: #fff;
}
.idx-box.is-on {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
}
.idx-empty {
  margin: 0;
  padding: 4px 8px 8px;
  font-size: 11px;
  color: var(--text-tertiary);
}
/* 滚动分页：还有未加载的行时给一条提示（滚到底自动追加） */
.idx-more {
  padding: 8px 8px 12px;
  font-size: 11px;
  text-align: center;
  color: var(--text-tertiary);
}
.idx-foot {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-container);
}
.idx-foot > span {
  font-size: 11px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
.idx-foot > :last-child {
  margin-left: auto;
}

/* ---------- 右侧详情工作区 ---------- */
.main {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}
.main-head {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px 10px;
  min-width: 0;
}
.main-mark {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: #fff;
}
.main-mark.is-key {
  background: #f0a020;
}
.main-mark.is-mail {
  background: #5b8def;
}
.main-mark.is-sms {
  background: #2fbf71;
}
.main-mark.is-cookie {
  background: #8b5cf6;
}
.main-title {
  flex: 1;
  min-width: 0;
}
.main-title b {
  display: block;
  font-size: 14px;
  font-weight: 650;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.main-title span {
  display: block;
  font-size: 11.5px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.main-tabs {
  flex: none;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-subtle);
}
.main-tab {
  position: relative;
  padding: 7px 10px 8px;
  border: 0;
  background: none;
  font: inherit;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s var(--ease-out);
}
.main-tab:hover {
  color: var(--text-primary);
}
.main-tab.is-on {
  color: var(--el-color-primary);
  font-weight: 600;
}
.main-tab-badge {
  display: inline-block;
  min-width: 15px;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--status-error, #f56c6c);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 14px;
  text-align: center;
  vertical-align: 1px;
}
.main-tab.is-on::after {
  content: '';
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--el-color-primary);
}

.main-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
}
.pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pane-title {
  margin: 2px 0 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--text-tertiary);
}
.pane-tip {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--text-tertiary);
}

.facts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 2px 18px;
}
.fact {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
  padding: 5px 0;
  border-bottom: 1px dashed var(--border-subtle);
}
/* 长值（如 AccessKeyId）跨两列，避免被挤成两三行 */
.fact.is-wide {
  grid-column: span 2;
}
.fact .k {
  flex: 0 0 84px;
  font-size: 11.5px;
  color: var(--text-tertiary);
  white-space: nowrap;
}
/* 密钥的键名比中文标签宽，单独给固定列宽，标签不折断 */
.secret-ref .fact .k {
  flex: 0 0 104px;
}
.fact .v {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: var(--text-primary);
  word-break: break-all;
}
.fact .v.is-bad {
  color: var(--status-error);
}

.cred-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-fill);
  color: var(--text-secondary);
}
.cred-card div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cred-card b {
  font-size: 12.5px;
  color: var(--text-primary);
}
.cred-card span {
  font-size: 11.5px;
  line-height: 1.5;
}
.cred-card.is-ok {
  border-color: var(--sev-p3-border);
  background: var(--sev-p3-bg);
  color: var(--sev-p3-text);
}
.cred-card.is-ok b {
  color: var(--sev-p3-text);
}
.cred-card.is-warn {
  border-color: var(--sev-p2-border);
  background: var(--sev-p2-bg);
  color: var(--sev-p2-text);
}
.cred-card.is-warn b {
  color: var(--sev-p2-text);
}

/* 凭据页签：账号引用的那条密钥（只读回填，与「密钥管理」同源） */
.secret-ref {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-container);
}
.secret-ref-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.secret-ref-head b {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.secret-ref-link {
  flex: none;
  padding: 0;
  font-size: 11.5px;
  color: var(--el-color-primary);
  background: none;
  border: 0;
  cursor: pointer;
}
.secret-ref-link:hover {
  text-decoration: underline;
}

.verify-hero {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-fill);
}
.verify-hero div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.verify-hero b {
  font-size: 13px;
  color: var(--text-primary);
}
.verify-hero span {
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.verify-hero.is-ok {
  border-color: var(--sev-p3-border);
  background: var(--sev-p3-bg);
}
.verify-hero.is-bad {
  border-color: var(--sev-p0-border);
  background: var(--sev-p0-bg);
}

.note {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 8px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-fill);
  font-size: 11.5px;
  line-height: 1.55;
  color: var(--text-secondary);
}
.note.is-bad {
  border-color: var(--sev-p0-border);
  background: var(--sev-p0-bg);
  color: var(--sev-p0-text);
}
.note.is-ok {
  border-color: var(--sev-p3-border);
  background: var(--sev-p3-bg);
  color: var(--sev-p3-text);
}

.mail-hero {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--bg-fill);
}
.mail-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.mail-copy b {
  font-size: 13px;
  color: var(--text-primary);
}
.mail-copy span {
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

.main-foot {
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 10px 16px;
  border-top: 1px solid var(--border-subtle);
  background: var(--bg-container);
}
.foot-gap {
  flex: 1;
}
.main-blank {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 24px;
  text-align: center;
}
.main-blank p {
  margin: 0;
  max-width: 280px;
  font-size: 11.5px;
  line-height: 1.65;
  color: var(--text-tertiary);
}

/* ---------- 右键菜单 ---------- */
.ctx-layer {
  position: fixed;
  inset: 0;
  z-index: 3000;
}
.ctx-menu {
  position: fixed;
  min-width: 172px;
  margin: 0;
  padding: 4px;
  list-style: none;
  background: var(--bg-elevated, var(--bg-container));
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md, 0 8px 24px rgb(0 0 0 / 12%));
}
.ctx-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 9px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-primary);
  cursor: pointer;
}
.ctx-item:hover {
  background: var(--bg-fill);
}
.ctx-item.is-divided {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--border-subtle);
  border-radius: 0 0 var(--radius-sm) var(--radius-sm);
}
.ctx-item.is-disabled {
  color: var(--text-tertiary);
  cursor: not-allowed;
}

.search {
  flex: 1;
  min-width: 170px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-container);
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}
.search:focus-within {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}
.search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font: inherit;
  font-size: 12px;
  color: var(--text-primary);
}
.search input::placeholder {
  color: var(--text-tertiary);
}
.search-icon {
  flex: none;
  color: var(--text-tertiary);
}
/* ---------- 加载失败提示 ---------- */
.alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border: 1px solid var(--sev-p0-border);
  border-radius: var(--radius-md);
  background: var(--sev-p0-bg);
  color: var(--sev-p0-text);
  font-size: 12px;
  flex: none;
}
.alert button {
  border: 1px solid currentColor;
  border-radius: var(--radius-sm);
  background: transparent;
  color: inherit;
  padding: 2px 10px;
  font: inherit;
  font-size: 11.5px;
  cursor: pointer;
}

/* ---------- 批量操作条（Ctrl/⌘ 单击索引行进入多选，贴在搜索框下方） ---------- */
.batch-bar {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 4px;
  flex: none;
  margin: 0 10px 8px;
  padding: 4px 8px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--radius-md);
  background: var(--el-color-primary-light-9);
  font-size: 12px;
  line-height: 18px;
  color: var(--text-secondary);
}
.batch-count {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.batch-count b {
  color: var(--el-color-primary-dark-2);
  font-variant-numeric: tabular-nums;
}
/* 还没勾选时给一句操作提示，别只留一个「已选 0 项」 */
.batch-count.is-empty {
  color: var(--text-tertiary);
}
/* 类名避开 koi 全局 .batch-actions（那是通栏样式，带 8/12 内边距与边框，会把这行撑成两行） */
.batch-tools {
  display: inline-flex;
  align-items: center;
  flex: none;
}
.batch-clear {
  flex: none;
  border: 0;
  background: none;
  font: inherit;
  font-size: 11.5px;
  line-height: 18px;
  padding: 0;
  color: var(--text-tertiary);
  cursor: pointer;
  text-decoration: underline;
}
.batch-clear:hover {
  color: var(--text-primary);
}

/* 索引栏被拖窄（默认 304px，最小 200px）时一行放不下 4 个图标按钮 + 取消：
   折成「计数 + 取消」/「图标按钮」两行，避免按钮被裁掉或横向溢出 */
@container rail (max-width: 262px) {
  .batch-bar {
    flex-wrap: wrap;
  }
  .batch-count {
    flex: 1 1 auto;
  }
  .batch-clear {
    order: 1;
    margin-left: auto;
  }
  .batch-tools {
    order: 2;
    flex: 1 0 100%;
  }
}

/* ---------- 等宽文本（地址、编码、AppKey） ---------- */
.mono {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
  font-size: 11.5px;
}

/* ---------- 一次性明文 ---------- */
.secret-box {
  padding: 2px 2px 4px;
}
.secret-tip {
  margin: 0 0 10px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-secondary);
}
.secret-tip b {
  color: var(--status-warning);
}
.secret-value {
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-fill);
  font-family: var(--font-mono, ui-monospace, Consolas, monospace);
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-primary);
  word-break: break-all;
  user-select: all;
}

/* ---------- 索引行高度吃满、窄屏降为上下结构 ---------- */
@media (max-width: 940px) {
  .ws {
    flex-direction: column;
  }
  .idx {
    width: auto;
    max-height: 340px;
  }
  /* 上下堆叠时没有左右分栏，手柄一并收起 */
  .ws-split {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .stat,
  .idx-row,
  .main-tab,
  .icon-btn,
  .ctx-item {
    transition: none;
  }
}
</style>
