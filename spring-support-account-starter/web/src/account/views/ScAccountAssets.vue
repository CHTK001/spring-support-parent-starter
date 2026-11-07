<template>
  <div class="account-assets">
    <!-- 视图切换：账号资产是本页主体；模板管理与消息模板是账号管理下的子功能，
         不再单独占侧边栏菜单，也不单独成应用。 -->
    <div class="views" role="tablist" aria-label="账号管理视图">
      <button
        v-for="item in views"
        :key="item.value"
        type="button"
        role="tab"
        class="views-item"
        :class="{ 'is-on': view === item.value }"
        :aria-selected="view === item.value"
        @click="view = item.value"
      >
        <ReIcon :name="item.icon" :size="14" />
        <span>{{ item.label }}</span>
      </button>
    </div>

    <template v-if="view === 'accounts'">
    <!-- KPI 带：数字既是概览也是筛选项，点一下就把列表收窄到那一类 -->
    <div class="kpi-band">
      <button
        v-for="cell in kpiCells"
        :key="cell.key"
        type="button"
        class="kpi"
        :class="{ 'is-on': cell.on }"
        :title="cell.hint"
        @click="cell.run()"
      >
        <b :class="cell.tone">{{ cell.value }}</b>
        <span>{{ cell.label }}</span>
      </button>
    </div>

    <!-- 分段页签 + 工具行 -->
    <div class="bar">
      <div class="seg" role="tablist" aria-label="账号种类">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          role="tab"
          class="seg-item"
          :class="{ 'is-on': segment === tab.value }"
          :aria-selected="segment === tab.value"
          @click="setSegment(tab.value)"
        >
          {{ tab.label }}
          <b>{{ tab.count }}</b>
        </button>
      </div>

      <label class="search">
        <ReIcon class="search-icon" name="Search" :size="13" />
        <input v-model="keyword" :placeholder="searchPlaceholder" />
      </label>

      <button v-if="statusFilter !== 'all'" type="button" class="chip-clear" @click="statusFilter = 'all'">
        清除状态筛选
      </button>

      <span class="bar-count">{{ tableCount }} 项</span>

      <span class="bar-actions">
        <ReButton show-text icon="RefreshLeft" plain :loading="loading" @click="loadAll">刷新</ReButton>
        <ReButton show-text icon="Plus" type="primary" @click="openCreate">{{ createLabel }}</ReButton>
      </span>
    </div>

    <div v-if="loadError" class="alert">
      <span>账号列表加载失败：{{ loadError }}</span>
      <button type="button" @click="loadAll">重试</button>
    </div>

    <div class="wb">
      <!-- ==================== 台账区 ==================== -->
      <section class="table-panel">
        <!-- 账号（ALL / API_KEY / EMAIL / SMS） -->
        <ReTable
          v-if="isAccountSegment"
          :data="accountTableRows"
          :loading="loading"
          row-key="accountId"
          border
          stripe
          class="account-table"
          empty-text="暂无账号，点右上「新增账号」创建"
          :settings="{ highlightCurrentRow: true }"
          :config="{ pagination: { enable: false }, toolbar: { enable: false } }"
          :context-menu="accountMenu"
          @row-click="onAccountRowClick"
          @cell-click="onCellClick"
          @context-menu-select="onAccountMenuSelect"
        >
          <ReTableColumn label="账号" min-width="200">
            <template #default="{ row }">
              <div class="name-cell">
                <i class="cred-bar" :class="row.hasSecret ? 'is-ok' : 'is-warn'"></i>
                <div class="name-text">
                  <div class="name-line">
                    <span class="name" :title="row.name">{{ row.name || '未命名' }}</span>
                    <ReTag v-if="row.defaultAccount" type="warning" size="small" effect="plain">默认</ReTag>
                  </div>
                  <div class="code mono">{{ row.code || '—' }}</div>
                </div>
              </div>
            </template>
          </ReTableColumn>
          <ReTableColumn label="种类" width="100">
            <template #default="{ row }">
              <ReTag :type="kindTagType(row.kind)" effect="plain">{{ kindLabel(row.kind) }}</ReTag>
            </template>
          </ReTableColumn>
          <ReTableColumn label="登录名 / 地址" min-width="168">
            <template #default="{ row }">
              <div v-if="row.username" class="mono ellipsis" :title="row.username">{{ row.username }}</div>
              <div v-if="row.endpoint" class="mono dim ellipsis" :title="endpointText(row)">
                {{ endpointText(row) }}
              </div>
              <span v-if="!row.username && !row.endpoint" class="dim">—</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="凭据" width="86" align="center">
            <template #default="{ row }">
              <ReTag :type="row.hasSecret ? 'success' : 'warning'" effect="plain">
                {{ row.hasSecret ? '已配置' : '待配置' }}
              </ReTag>
            </template>
          </ReTableColumn>
          <ReTableColumn label="校验" width="96" align="center">
            <template #default="{ row }">
              <el-tooltip :content="row.verifyMessage || '尚未校验'" placement="top" :show-after="200">
                <ReTag :type="verifyTagType(row.verifyStatus)" effect="plain">
                  {{ verifyLabel(row.verifyStatus) }}
                </ReTag>
              </el-tooltip>
            </template>
          </ReTableColumn>
          <ReTableColumn label="更新" width="100">
            <template #default="{ row }">
              <span class="dim time-text">{{ formatShort(row.updatedAt || row.createdAt) }}</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="操作" width="140" fixed="right" align="right">
            <template #default="{ row }">
              <span class="ops" @click.stop>
                <ReButton v-if="row.kind === 'EMAIL' || row.kind === 'SMS'" link @click.stop="openConsole(row)">
                  {{ row.kind === 'EMAIL' ? '打开邮箱' : '打开短信' }}
                </ReButton>
                <ReButton v-else link @click.stop="openDetail(row)">详情</ReButton>
                <ReButton link type="primary" @click.stop="openEdit(row)">编辑</ReButton>
              </span>
            </template>
          </ReTableColumn>
        </ReTable>

        <!-- AppKey -->
        <ReTable
          v-else-if="segment === 'APPKEY'"
          :data="appKeyTableRows"
          :loading="loading"
          row-key="appKeyId"
          border
          stripe
          class="account-table"
          empty-text="暂无访问密钥，点右上「新增 AppKey」创建"
          :settings="{ highlightCurrentRow: true }"
          :config="{ pagination: { enable: false }, toolbar: { enable: false } }"
          :context-menu="appKeyMenu"
          @row-click="onAppKeyRowClick"
          @cell-click="onCellClick"
          @context-menu-select="onAppKeyMenuSelect"
        >
          <ReTableColumn label="名称" min-width="180">
            <template #default="{ row }">
              <div class="name-cell">
                <i class="cred-bar" :class="row.status === 0 ? 'is-off' : 'is-ok'"></i>
                <div class="name-text">
                  <div class="name-line">
                    <span class="name" :title="row.name">{{ row.name || '未命名' }}</span>
                  </div>
                  <div class="code mono">{{ row.remark || '—' }}</div>
                </div>
              </div>
            </template>
          </ReTableColumn>
          <ReTableColumn label="AppKey" min-width="170">
            <template #default="{ row }">
              <span class="mono ellipsis" :title="row.appKey">{{ row.appKey || '—' }}</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="归属" width="120">
            <template #default="{ row }">
              <span class="dim ellipsis">{{ row.owner ? '仅本人' : '部署级共享' }}</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="状态" width="86" align="center">
            <template #default="{ row }">
              <ReTag :type="row.status === 0 ? 'info' : 'success'" effect="plain">
                {{ row.status === 0 ? '停用' : '启用' }}
              </ReTag>
            </template>
          </ReTableColumn>
          <ReTableColumn label="过期" width="110">
            <template #default="{ row }">
              <span class="dim" :class="{ 'is-expired': expired(row.expireAt) }">{{ expireText(row.expireAt) }}</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="最近使用" width="110">
            <template #default="{ row }">
              <span class="dim time-text">{{ formatShort(row.lastUsedAt) }}</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="操作" width="120" fixed="right" align="right">
            <template #default="{ row }">
              <span class="ops" @click.stop>
                <ReButton link @click.stop="rotateKey(row)">轮换</ReButton>
                <ReButton link type="primary" @click.stop="openEditKey(row)">编辑</ReButton>
              </span>
            </template>
          </ReTableColumn>
        </ReTable>

        <!-- Cookie -->
        <ReTable
          v-else
          :data="cookieTableRows"
          :loading="loading"
          row-key="cookieId"
          border
          stripe
          class="account-table"
          empty-text="暂无 Cookie，点右上「新增 Cookie」创建"
          :settings="{ highlightCurrentRow: true }"
          :config="{ pagination: { enable: false }, toolbar: { enable: false } }"
          :context-menu="cookieMenu"
          @row-click="onCookieRowClick"
          @cell-click="onCellClick"
          @context-menu-select="onCookieMenuSelect"
        >
          <ReTableColumn label="名称" min-width="180">
            <template #default="{ row }">
              <div class="name-cell">
                <i class="cred-bar" :class="row.status === 0 ? 'is-off' : row.hasValue ? 'is-ok' : 'is-warn'"></i>
                <div class="name-text">
                  <div class="name-line">
                    <span class="name" :title="row.name">{{ row.name || '未命名' }}</span>
                  </div>
                  <div class="code mono">{{ row.remark || '—' }}</div>
                </div>
              </div>
            </template>
          </ReTableColumn>
          <ReTableColumn label="域名" min-width="170">
            <template #default="{ row }">
              <div class="mono ellipsis" :title="row.domain">{{ row.domain || '—' }}</div>
              <div class="dim mono">{{ row.path || '/' }}</div>
            </template>
          </ReTableColumn>
          <ReTableColumn label="来源" width="110">
            <template #default="{ row }">
              <span class="dim">{{ row.source || '—' }}</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="归属" width="110">
            <template #default="{ row }">
              <span class="dim">{{ row.owner ? '仅本人' : '部署级共享' }}</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="内容" width="86" align="center">
            <template #default="{ row }">
              <ReTag :type="row.hasValue ? 'success' : 'warning'" effect="plain">
                {{ row.hasValue ? '已配置' : '待配置' }}
              </ReTag>
            </template>
          </ReTableColumn>
          <ReTableColumn label="过期" width="110">
            <template #default="{ row }">
              <span class="dim" :class="{ 'is-expired': expired(row.expireAt) }">{{ expireText(row.expireAt) }}</span>
            </template>
          </ReTableColumn>
          <ReTableColumn label="操作" width="100" fixed="right" align="right">
            <template #default="{ row }">
              <span class="ops" @click.stop>
                <ReButton link type="primary" @click.stop="openEditCookie(row)">编辑</ReButton>
              </span>
            </template>
          </ReTableColumn>
        </ReTable>

        <div v-if="segmentError" class="seg-error">{{ segmentError }}</div>
      </section>

      <!-- ==================== 检查器 ==================== -->
      <aside class="inspector">
        <!-- 账号 -->
        <template v-if="isAccountSegment && activeAccount">
          <header class="ins-head">
            <span class="ins-mark" :class="'is-' + kindClass(activeAccount.kind)">
              <ReIcon :name="kindIcon(activeAccount.kind)" :size="15" />
            </span>
            <div class="ins-title">
              <b :title="activeAccount.name">{{ activeAccount.name || '未命名账号' }}</b>
              <span>{{ kindLabel(activeAccount.kind) }} · {{ activeAccount.code || '—' }}</span>
            </div>
            <ReTag :type="activeAccount.status === 0 ? 'info' : 'success'" effect="plain" size="small">
              {{ activeAccount.status === 0 ? '停用' : '启用' }}
            </ReTag>
          </header>

          <div class="ins-flags">
            <span class="flag" :class="activeAccount.hasSecret ? 'is-ok' : 'is-warn'">
              {{ activeAccount.hasSecret ? '凭据已配置' : '凭据待补' }}
            </span>
            <span class="flag" :class="'is-' + verifyTone(activeAccount.verifyStatus)">
              校验{{ verifyLabel(activeAccount.verifyStatus) }}
            </span>
            <span v-if="activeAccount.defaultAccount" class="flag is-brand">同种类默认</span>
          </div>

          <dl class="ins-list">
            <div>
              <dt>登录名</dt>
              <dd class="mono">{{ activeAccount.username || '—' }}</dd>
            </div>
            <div>
              <dt>服务地址</dt>
              <dd class="mono">{{ endpointText(activeAccount) || '—' }}</dd>
            </div>
            <div>
              <dt>厂商</dt>
              <dd>{{ activeAccount.vendor || '—' }}</dd>
            </div>
            <div>
              <dt>关联渠道</dt>
              <dd>{{ activeAccount.channelKey || '—' }}</dd>
            </div>
            <div>
              <dt>归属</dt>
              <dd>{{ activeAccount.owner ? `仅本人（${activeAccount.owner}）` : '部署级共享' }}</dd>
            </div>
          </dl>

          <div v-if="activeAccount.verifyMessage" class="ins-note" :class="'is-' + verifyTone(activeAccount.verifyStatus)">
            {{ activeAccount.verifyMessage }}
          </div>

          <div class="ins-actions">
            <ReButton
              show-text
              icon="CircleCheck"
              size="small"
              :loading="verifyingId === activeAccount.accountId"
              @click="onVerify(activeAccount)"
            >
              校验
            </ReButton>
            <ReButton show-text icon="EditPen" size="small" @click="openEdit(activeAccount)">编辑</ReButton>
            <ReButton
              v-if="hasMailbox(activeAccount)"
              show-text
              :icon="activeAccount.kind === 'EMAIL' ? 'Message' : 'ChatDotRound'"
              size="small"
              @click="openConsole(activeAccount)"
            >
              {{ activeAccount.kind === 'EMAIL' ? '打开邮箱' : '打开短信' }}
            </ReButton>
            <ReButton
              show-text
              icon="Star"
              size="small"
              :disabled="Boolean(activeAccount.defaultAccount)"
              @click="setDefault(activeAccount)"
            >
              设为默认
            </ReButton>
            <ReButton show-text icon="SwitchButton" size="small" @click="toggleStatus(activeAccount)">
              {{ activeAccount.status === 0 ? '启用' : '停用' }}
            </ReButton>
            <ReButton show-text icon="Delete" size="small" type="danger" plain @click="remove(activeAccount)">删除</ReButton>
          </div>

          <div class="ins-meta">
            <span>创建 {{ formatTime(activeAccount.createdAt) }}</span>
            <span>更新 {{ formatTime(activeAccount.updatedAt) }}</span>
          </div>
        </template>

        <!-- AppKey -->
        <template v-else-if="segment === 'APPKEY' && activeAppKey">
          <header class="ins-head">
            <span class="ins-mark is-key">
              <ReIcon name="Key" :size="15" />
            </span>
            <div class="ins-title">
              <b :title="activeAppKey.name">{{ activeAppKey.name || '未命名密钥' }}</b>
              <span>访问密钥 · {{ activeAppKey.owner ? '仅本人' : '部署级共享' }}</span>
            </div>
            <ReTag :type="activeAppKey.status === 0 ? 'info' : 'success'" effect="plain" size="small">
              {{ activeAppKey.status === 0 ? '停用' : '启用' }}
            </ReTag>
          </header>

          <div class="ins-flags">
            <span class="flag is-brand">Secret 只进不出</span>
            <span class="flag" :class="expired(activeAppKey.expireAt) ? 'is-warn' : 'is-ok'">
              {{ expireText(activeAppKey.expireAt) }}
            </span>
          </div>

          <dl class="ins-list">
            <div>
              <dt>AppKey</dt>
              <dd class="mono">{{ activeAppKey.appKey || '—' }}</dd>
            </div>
            <div>
              <dt>最近使用</dt>
              <dd>{{ formatShort(activeAppKey.lastUsedAt) }}</dd>
            </div>
            <div>
              <dt>IP 白名单</dt>
              <dd class="mono">{{ activeAppKey.ipWhitelist || '不限制' }}</dd>
            </div>
            <div>
              <dt>备注</dt>
              <dd>{{ activeAppKey.remark || '—' }}</dd>
            </div>
          </dl>

          <div class="ins-actions">
            <ReButton show-text icon="CopyDocument" size="small" @click="copyKey(activeAppKey)">复制 AppKey</ReButton>
            <ReButton show-text icon="Refresh" size="small" @click="rotateKey(activeAppKey)">轮换 Secret</ReButton>
            <ReButton show-text icon="EditPen" size="small" @click="openEditKey(activeAppKey)">编辑</ReButton>
            <ReButton show-text icon="SwitchButton" size="small" @click="toggleKeyStatus(activeAppKey)">
              {{ activeAppKey.status === 0 ? '启用' : '停用' }}
            </ReButton>
            <ReButton show-text icon="Delete" size="small" type="danger" plain @click="removeKey(activeAppKey)">
              删除
            </ReButton>
          </div>

          <div class="ins-meta">
            <span>创建 {{ formatTime(activeAppKey.createdAt) }}</span>
            <span>更新 {{ formatTime(activeAppKey.updatedAt) }}</span>
          </div>
        </template>

        <!-- Cookie -->
        <template v-else-if="segment === 'COOKIE' && activeCookie">
          <header class="ins-head">
            <span class="ins-mark is-cookie">
              <ReIcon name="Postcard" :size="15" />
            </span>
            <div class="ins-title">
              <b :title="activeCookie.name">{{ activeCookie.name || '未命名 Cookie' }}</b>
              <span>{{ activeCookie.domain || '—' }}{{ activeCookie.path || '/' }}</span>
            </div>
            <ReTag :type="activeCookie.status === 0 ? 'info' : 'success'" effect="plain" size="small">
              {{ activeCookie.status === 0 ? '停用' : '启用' }}
            </ReTag>
          </header>

          <div class="ins-flags">
            <span class="flag" :class="activeCookie.hasValue ? 'is-ok' : 'is-warn'">
              {{ activeCookie.hasValue ? '内容已配置' : '内容待配置' }}
            </span>
            <span class="flag" :class="expired(activeCookie.expireAt) ? 'is-warn' : 'is-ok'">
              {{ expireText(activeCookie.expireAt) }}
            </span>
          </div>

          <dl class="ins-list">
            <div>
              <dt>域名</dt>
              <dd class="mono">{{ activeCookie.domain || '—' }}</dd>
            </div>
            <div>
              <dt>路径</dt>
              <dd class="mono">{{ activeCookie.path || '/' }}</dd>
            </div>
            <div>
              <dt>来源</dt>
              <dd>{{ activeCookie.source || '—' }}</dd>
            </div>
            <div>
              <dt>归属</dt>
              <dd>{{ activeCookie.owner ? `仅本人（${activeCookie.owner}）` : '部署级共享' }}</dd>
            </div>
            <div>
              <dt>备注</dt>
              <dd>{{ activeCookie.remark || '—' }}</dd>
            </div>
          </dl>

          <div class="ins-actions">
            <ReButton show-text icon="EditPen" size="small" @click="openEditCookie(activeCookie)">编辑</ReButton>
            <ReButton show-text icon="SwitchButton" size="small" @click="toggleCookieStatus(activeCookie)">
              {{ activeCookie.status === 0 ? '启用' : '停用' }}
            </ReButton>
            <ReButton show-text icon="Delete" size="small" type="danger" plain @click="removeCookie(activeCookie)">
              删除
            </ReButton>
          </div>

          <div class="ins-meta">
            <span>创建 {{ formatTime(activeCookie.createdAt) }}</span>
            <span>更新 {{ formatTime(activeCookie.updatedAt) }}</span>
          </div>
        </template>

        <div v-else class="ins-blank">
          <ReEmpty :image-size="56" :description="inspectorHint" />
          <p>单击一行查看详情，双击打开完整详情弹窗，右键有完整操作。</p>
        </div>
      </aside>
    </div>
    </template>

    <!-- 模板管理：模板库 / 设计器 / 预览 / 打印机 / 调用日志，原「模板管理」模块整块并入 -->
    <div v-else-if="view === 'templates'" class="embed-pane">
      <TemplateView />
    </div>

    <!-- 消息模板：模板渲染与推送前预览 -->
    <div v-else class="embed-pane">
      <MessageTemplateView />
    </div>

    <!-- 账号详情 -->
    <ReDialog v-model="showDetail" :title="detailTitle" width="760" :height="620" destroy-on-close compact>
      <div v-if="activeAccount" class="detail-body">
        <section class="detail-section">
          <h4 class="section-title">基础信息</h4>
          <ReRow :gutter="16">
            <ReCol :span="8"><div class="kv"><span class="k">名称</span><span class="v">{{ activeAccount.name || '—' }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">编码</span><span class="v mono">{{ activeAccount.code || '—' }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">种类</span><span class="v">{{ kindLabel(activeAccount.kind) }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">厂商 / 服务商</span><span class="v">{{ activeAccount.vendor || '—' }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">登录名 / 地址</span><span class="v mono">{{ activeAccount.username || '—' }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">服务地址</span><span class="v mono">{{ activeAccount.endpoint || '—' }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">归属</span><span class="v">{{ activeAccount.owner ? `仅本人（${activeAccount.owner}）` : '部署级共享' }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">状态</span><span class="v">{{ activeAccount.status === 0 ? '停用' : '启用' }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">同种类默认</span><span class="v">{{ activeAccount.defaultAccount ? '是' : '否' }}</span></div></ReCol>
          </ReRow>
        </section>

        <section class="detail-section">
          <h4 class="section-title">
            凭据与校验
            <span class="section-extra">
              <ReTag :type="activeAccount.hasSecret ? 'success' : 'warning'" effect="plain" size="small">
                {{ activeAccount.hasSecret ? '凭据已配置' : '凭据待配置' }}
              </ReTag>
            </span>
          </h4>
          <ReRow :gutter="16">
            <ReCol :span="8"><div class="kv"><span class="k">校验状态</span><span class="v">{{ verifyLabel(activeAccount.verifyStatus) }}</span></div></ReCol>
            <ReCol :span="8"><div class="kv"><span class="k">最近校验</span><span class="v">{{ formatTime(activeAccount.verifyAt) }}</span></div></ReCol>
            <ReCol :span="24"><div class="kv wide"><span class="k">校验说明</span><span class="v">{{ activeAccount.verifyMessage || '—' }}</span></div></ReCol>
          </ReRow>
          <div class="detail-tip">凭据明文只进不出：读取接口只回传「是否已配置」，编辑时留空表示不修改。</div>
        </section>

        <section class="detail-section">
          <h4 class="section-title">类型专属配置</h4>
          <div v-if="configRows.length" class="config-grid">
            <div v-for="item in configRows" :key="item.key" class="kv wide">
              <span class="k">{{ item.label }}</span>
              <span class="v">{{ item.value }}</span>
            </div>
          </div>
          <ReEmpty v-else :image-size="56" description="暂无类型专属配置" />
        </section>

        <section class="detail-section">
          <h4 class="section-title">时间</h4>
          <ReRow :gutter="16">
            <ReCol :span="12"><div class="kv"><span class="k">创建时间</span><span class="v">{{ formatTime(activeAccount.createdAt) }}</span></div></ReCol>
            <ReCol :span="12"><div class="kv"><span class="k">更新时间</span><span class="v">{{ formatTime(activeAccount.updatedAt) }}</span></div></ReCol>
          </ReRow>
        </section>
      </div>

      <template #footer>
        <ReButton v-if="activeAccount && hasMailbox(activeAccount)" @click="openConsole(activeAccount)">
          {{ activeAccount?.kind === 'EMAIL' ? '打开邮箱' : '打开短信' }}
        </ReButton>
        <ReButton :loading="verifyingId === activeAccount?.accountId" @click="onVerify(activeAccount)">校验凭据</ReButton>
        <ReButton type="primary" @click="openEdit(activeAccount)">编辑</ReButton>
      </template>
    </ReDialog>

    <!-- 新增 / 编辑账号 -->
    <AccountFormDialog v-model="formVisible" :account="editing" @saved="onSaved" />

    <!-- 新增 / 编辑 AppKey -->
    <AppKeyDialog v-model="keyFormVisible" :app-key="editingKey" @saved="onKeySaved" />

    <!-- 新增 / 编辑 Cookie -->
    <CookieDialog v-model="cookieFormVisible" :cookie="editingCookie" @saved="onCookieSaved" />

    <!-- 收 / 发信箱：邮箱走 IMAP / SMTP，短信走 MessagePush -->
    <MailboxDrawer
      v-model="mailboxVisible"
      :account="mailboxAccount"
      :console-url="mailboxAccount ? accountConsoleUrl(mailboxAccount) : ''"
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
import { computed, onMounted, ref, watch } from 'vue';
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
  View,
} from '@element-plus/icons-vue';
import { message } from '@repo/core';
import ReButton from '@repo/ui/re-button';
import ReDialog from '@repo/ui/re-dialog';
import ReEmpty from '@repo/ui/re-empty';
import ReIcon from '@repo/ui/re-icon';
import ReTable from '@repo/ui/re-table';
import type {
  ReTableCellClickPayload,
  ReTableContextMenuContext,
  ReTableContextMenuItem,
} from '@repo/ui/re-table';
import ReTag from '@repo/ui/re-tag';

import AccountFormDialog from '../components/AccountFormDialog.vue';
import MailboxDrawer from '../components/MailboxDrawer.vue';
import MessageTemplateView from '../../template/views/MessageTemplateView.vue';
import TemplateView from '../../template/views/TemplateView.vue';
import AppKeyDialog from './components/AppKeyDialog.vue';
import CookieDialog from './components/CookieDialog.vue';
import {
  deleteAccount,
  deleteAccountAppKey,
  deleteAccountCookie,
  listAccountAppKeys,
  listAccountCookies,
  listAccounts,
  rotateAccountAppKey,
  updateAccount,
  updateAccountAppKey,
  updateAccountCookie,
  verifyAccount,
  type AccountKind,
  type AccountVerifyStatus,
  type SysAccount,
  type SysAccountAppKey,
  type SysAccountCookie,
} from '../api/account';
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
 * <p><b>四种资产</b>：统一账号（设备密钥 / 邮箱 / 短信）、AppKey、Cookie 共用一套
 * KPI 带 + 分段页签 + 检查器，因为它们回答的是同一类问题——「这个凭据还能不能用」。
 * 三张表各自保留自己的列，不做字段对齐，避免把不同语义的字段硬塞进同一列。</p>
 *
 * <p><b>敏感值</b>：账号 secret / AppKey secret / Cookie 值都只进不出。列表与检查器
 * 只显示「是否已配置」，AppKey 的明文只在创建或轮换后弹一次。</p>
 */

/** 当前分段：账号三种 + AppKey + Cookie */
type Segment = 'ALL' | AccountKind | 'APPKEY' | 'COOKIE';

/** 账号状态筛选 */
type StatusFilter = 'all' | 'on' | 'pending' | 'invalid';

/** 页面视图：账号资产是本页主体，模板管理与消息模板是账号管理下的子功能 */
type AccountView = 'accounts' | 'templates' | 'messages';

/** 当前视图 */
const view = ref<AccountView>('accounts');

/** 视图切换项 */
const views: Array<{ value: AccountView; label: string; icon: string }> = [
  { value: 'accounts', label: '账号资产', icon: 'User' },
  { value: 'templates', label: '模板管理', icon: 'MagicStick' },
  { value: 'messages', label: '消息模板', icon: 'ChatDotRound' },
];

/** 统一账号 */
const accountList = ref<SysAccount[]>([]);
/** 访问密钥 */
const appKeyList = ref<SysAccountAppKey[]>([]);
/** Cookie */
const cookieList = ref<SysAccountCookie[]>([]);

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
/** 账号状态筛选 */
const statusFilter = ref<StatusFilter>('all');

/** 选中的账号 / AppKey / Cookie */
const selectedAccountId = ref('');
const selectedAppKeyId = ref('');
const selectedCookieId = ref('');

/** 正在校验的账号 */
const verifyingId = ref<string | null>(null);

/** 详情弹窗 */
const showDetail = ref(false);
/** 账号新增 / 编辑 */
const formVisible = ref(false);
const editing = ref<SysAccount | null>(null);
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

/** 是否为账号类分段 */
const isAccountSegment = computed(
  () => segment.value === 'ALL' || segment.value === 'API_KEY' || segment.value === 'EMAIL' || segment.value === 'SMS',
);

/** 新建按钮文案 */
const createLabel = computed(() => {
  if (segment.value === 'APPKEY') return '新增 AppKey';
  if (segment.value === 'COOKIE') return '新增 Cookie';
  return '新增账号';
});

/** 搜索占位 */
const searchPlaceholder = computed(() => {
  if (segment.value === 'APPKEY') return '搜索名称 / AppKey / 备注';
  if (segment.value === 'COOKIE') return '搜索名称 / 域名 / 来源';
  return '搜索名称 / 编码 / 登录名 / 地址';
});

/** 检查器空态提示 */
const inspectorHint = computed(() => {
  if (segment.value === 'APPKEY') return '选择一条访问密钥查看详情';
  if (segment.value === 'COOKIE') return '选择一条 Cookie 查看详情';
  return '选择一条账号查看详情';
});

/** 分段内联错误 */
const segmentError = computed(() =>
  segment.value === 'APPKEY' ? appKeyError.value : segment.value === 'COOKIE' ? cookieError.value : '',
);

// ==================== 概览计数 ====================

/** 启用账号数 */
const enabledCount = computed(() => accountList.value.filter(row => row.status !== 0).length);
/** 缺凭据账号数 */
const missingCount = computed(() => accountList.value.filter(row => !row.hasSecret).length);
/** 校验失败账号数 */
const invalidCount = computed(() => accountList.value.filter(row => row.verifyStatus === 'INVALID').length);
/** 关联渠道数（有 channelKey 的账号覆盖的渠道去重） */
const channelCount = computed(
  () => new Set(accountList.value.map(row => row.channelKey).filter(Boolean)).size,
);

/** 分段页签（数量恒显示，避免点选后消失） */
const tabs = computed<Array<{ value: Segment; label: string; count: number }>>(() => [
  { value: 'ALL', label: '全部', count: accountList.value.length },
  { value: 'API_KEY', label: '密钥', count: accountList.value.filter(row => row.kind === 'API_KEY').length },
  { value: 'EMAIL', label: '邮箱', count: accountList.value.filter(row => row.kind === 'EMAIL').length },
  { value: 'SMS', label: '短信', count: accountList.value.filter(row => row.kind === 'SMS').length },
  { value: 'APPKEY', label: 'AppKey', count: appKeyList.value.length },
  { value: 'COOKIE', label: 'Cookie', count: cookieList.value.length },
]);

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
    if (isAccountSegment.value && segment.value !== 'ALL' && row.kind !== segment.value) return false;
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

/** ReTable 需要对象数组 */
const accountTableRows = computed<Array<Record<string, unknown>>>(
  () => filteredAccounts.value as unknown as Array<Record<string, unknown>>,
);
const appKeyTableRows = computed<Array<Record<string, unknown>>>(
  () => filteredAppKeys.value as unknown as Array<Record<string, unknown>>,
);
const cookieTableRows = computed<Array<Record<string, unknown>>>(
  () => filteredCookies.value as unknown as Array<Record<string, unknown>>,
);

/** 当前表格行数 */
const tableCount = computed(() => {
  if (segment.value === 'APPKEY') return filteredAppKeys.value.length;
  if (segment.value === 'COOKIE') return filteredCookies.value.length;
  return filteredAccounts.value.length;
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

/** 详情标题 */
const detailTitle = computed(() => {
  if (!activeAccount.value) return '账号详情';
  return `${kindLabel(activeAccount.value.kind)} · ${activeAccount.value.name || activeAccount.value.code || ''}`;
});

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

/** 类型专属配置展示行 */
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
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({
      key,
      label: labels[key] || key,
      value: typeof value === 'boolean' ? (value ? '是' : '否') : String(value),
    }));
});

// ==================== 加载 ====================

/**
 * 加载三份数据。
 *
 * <p>用 {@code allSettled}：AppKey / Cookie 接口在未登录时可能 403，
 * 不该因此把账号列表一起打成空白页。</p>
 */
async function loadAll(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  const [accounts, keys, cookies] = await Promise.allSettled([
    listAccounts(),
    listAccountAppKeys(),
    listAccountCookies(),
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
  loading.value = false;
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

// ==================== 行选中 ====================

/**
 * 账号行单击。
 *
 * @param row 行数据
 */
function onAccountRowClick(row: Record<string, unknown>): void {
  selectedAccountId.value = String(row.accountId ?? '');
}

/**
 * AppKey 行单击。
 *
 * @param row 行数据
 */
function onAppKeyRowClick(row: Record<string, unknown>): void {
  selectedAppKeyId.value = String(row.appKeyId ?? '');
}

/**
 * Cookie 行单击。
 *
 * @param row 行数据
 */
function onCookieRowClick(row: Record<string, unknown>): void {
  selectedCookieId.value = String(row.cookieId ?? '');
}

/** 双击（第二次 click 的 detail=2）打开账号详情 */
function onCellClick(payload: ReTableCellClickPayload): void {
  if (payload.event?.detail !== 2) return;
  if (segment.value === 'APPKEY') {
    openEditKey(payload.row as unknown as SysAccountAppKey);
    return;
  }
  if (segment.value === 'COOKIE') {
    openEditCookie(payload.row as unknown as SysAccountCookie);
    return;
  }
  openDetail(payload.row as unknown as SysAccount);
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
  editing.value = null;
  formVisible.value = true;
}

/** 打开账号编辑 */
function openEdit(row: SysAccount | null): void {
  if (!row) return;
  editing.value = row;
  formVisible.value = true;
}

/** 打开账号详情 */
function openDetail(row: SysAccount | null): void {
  if (!row) return;
  selectedAccountId.value = row.accountId;
  showDetail.value = true;
}

/** 账号保存后刷新并回指 */
function onSaved(saved: SysAccount): void {
  if (saved?.accountId) selectedAccountId.value = saved.accountId;
  void loadAll();
}

/**
 * 账号行右键菜单。
 *
 * @param ctx 右键上下文
 * @returns 菜单项
 */
function accountMenu(ctx: ReTableContextMenuContext): ReTableContextMenuItem[] {
  const row = ctx.row as unknown as SysAccount;
  const items: ReTableContextMenuItem[] = [
    { key: 'detail', label: '账号详情', icon: View },
    { key: 'edit', label: '编辑账号', icon: EditPen },
    { key: 'verify', label: '校验凭据', icon: CircleCheck },
  ];
  if (row.kind === 'EMAIL' || row.kind === 'SMS') {
    items.push({
      key: 'open-console',
      label: row.kind === 'EMAIL' ? '打开邮箱' : '打开短信',
      icon: row.kind === 'EMAIL' ? Message : ChatDotRound,
      divided: true,
      disabled: !hasMailbox(row),
    });
  }
  items.push(
    { key: 'copy-code', label: '复制账号编码', icon: CopyDocument, divided: true },
    {
      key: 'default',
      label: row.defaultAccount ? '已是同种类默认' : '设为同种类默认',
      icon: Star,
      disabled: Boolean(row.defaultAccount),
    },
    { key: 'status', label: row.status === 0 ? '启用账号' : '停用账号', icon: SwitchButton },
    { key: 'delete', label: '删除账号', icon: Delete, divided: true },
  );
  return items;
}

/** 账号右键选中 */
function onAccountMenuSelect(item: ReTableContextMenuItem, ctx: ReTableContextMenuContext): void {
  const row = ctx.row as unknown as SysAccount;
  selectedAccountId.value = row.accountId;
  switch (item.key) {
    case 'detail':
      openDetail(row);
      break;
    case 'edit':
      openEdit(row);
      break;
    case 'verify':
      void onVerify(row);
      break;
    case 'open-console':
      openConsole(row);
      break;
    case 'copy-code':
      void copyCode(row);
      break;
    case 'default':
      void setDefault(row);
      break;
    case 'status':
      void toggleStatus(row);
      break;
    case 'delete':
      void remove(row);
      break;
  }
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

/** AppKey 行右键菜单 */
function appKeyMenu(ctx: ReTableContextMenuContext): ReTableContextMenuItem[] {
  const row = ctx.row as unknown as SysAccountAppKey;
  return [
    { key: 'copy', label: '复制 AppKey', icon: CopyDocument },
    { key: 'rotate', label: '轮换 Secret', icon: Refresh, divided: true },
    { key: 'edit', label: '编辑密钥', icon: EditPen },
    { key: 'status', label: row.status === 0 ? '启用密钥' : '停用密钥', icon: SwitchButton },
    { key: 'delete', label: '删除密钥', icon: Delete, divided: true },
  ];
}

/** AppKey 右键选中 */
function onAppKeyMenuSelect(item: ReTableContextMenuItem, ctx: ReTableContextMenuContext): void {
  const row = ctx.row as unknown as SysAccountAppKey;
  selectedAppKeyId.value = row.appKeyId;
  switch (item.key) {
    case 'copy':
      void copyKey(row);
      break;
    case 'rotate':
      void rotateKey(row);
      break;
    case 'edit':
      openEditKey(row);
      break;
    case 'status':
      void toggleKeyStatus(row);
      break;
    case 'delete':
      void removeKey(row);
      break;
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

/** Cookie 行右键菜单 */
function cookieMenu(ctx: ReTableContextMenuContext): ReTableContextMenuItem[] {
  const row = ctx.row as unknown as SysAccountCookie;
  return [
    { key: 'edit', label: '编辑 Cookie', icon: EditPen },
    { key: 'status', label: row.status === 0 ? '启用 Cookie' : '停用 Cookie', icon: SwitchButton, divided: true },
    { key: 'delete', label: '删除 Cookie', icon: Delete },
  ];
}

/** Cookie 右键选中 */
function onCookieMenuSelect(item: ReTableContextMenuItem, ctx: ReTableContextMenuContext): void {
  const row = ctx.row as unknown as SysAccountCookie;
  selectedCookieId.value = row.cookieId;
  switch (item.key) {
    case 'edit':
      openEditCookie(row);
      break;
    case 'status':
      void toggleCookieStatus(row);
      break;
    case 'delete':
      void removeCookie(row);
      break;
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
 * 账号种类标签色。
 *
 * @param kind 种类
 * @returns ReTag type
 */
function kindTagType(kind?: AccountKind): 'warning' | 'primary' | 'success' | 'info' {
  if (kind === 'API_KEY') return 'warning';
  if (kind === 'EMAIL') return 'primary';
  if (kind === 'SMS') return 'success';
  return 'info';
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

onMounted(loadAll);
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

/* ---------- 页面视图切换（账号资产 / 模板管理 / 消息模板） ---------- */
.views {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  align-self: flex-start;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-container);
  flex: none;
}
.views-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  font: inherit;
  font-size: 12.5px;
  color: var(--text-secondary);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.16s var(--ease-out), color 0.16s var(--ease-out);
}
.views-item:hover {
  background: var(--bg-fill);
  color: var(--text-primary);
}
.views-item.is-on {
  background: var(--brand-50);
  color: var(--brand-600, #5a4bd9);
  font-weight: 600;
}

/* ---------- 内嵌子视图（模板管理 / 消息模板） ---------- */
.embed-pane {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}
.embed-pane > * {
  flex: 1;
  min-width: 0;
  min-height: 0;
}

/* ---------- KPI 带 ---------- */
.kpi-band {
  display: flex;
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  flex: none;
}
.kpi {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 9px 14px;
  border: 0;
  border-right: 1px solid var(--border-subtle);
  background: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s var(--ease-out);
}
.kpi:last-child {
  border-right: 0;
}
.kpi:hover {
  background: var(--bg-fill);
}
.kpi.is-on {
  background: var(--brand-50);
  box-shadow: inset 0 -2px 0 var(--brand-500);
}
.kpi b {
  font-size: 20px;
  font-weight: 650;
  line-height: 1.15;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}
.kpi b.is-ok {
  color: var(--status-success);
}
.kpi b.is-warn {
  color: var(--status-warning);
}
.kpi b.is-bad {
  color: var(--status-error);
}
.kpi span {
  font-size: 11px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- 分段 + 工具行 ---------- */
.bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: none;
}
.seg {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-container);
}
.seg-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  font: inherit;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.16s var(--ease-out), color 0.16s var(--ease-out);
}
.seg-item b {
  padding: 0 4px;
  border-radius: 4px;
  background: var(--bg-fill);
  font-size: 10.5px;
  font-weight: 600;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
.seg-item:hover {
  background: var(--bg-fill);
  color: var(--text-primary);
}
.seg-item.is-on {
  background: var(--brand-50);
  color: var(--brand-600, #5a4bd9);
  font-weight: 600;
}
.seg-item.is-on b {
  background: var(--bg-container);
  color: var(--brand-500, #6d5dfc);
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
.chip-clear {
  padding: 4px 10px;
  border: 1px solid var(--sev-p2-border);
  border-radius: 999px;
  background: var(--sev-p2-bg);
  font: inherit;
  font-size: 11.5px;
  color: var(--sev-p2-text);
  cursor: pointer;
  white-space: nowrap;
}
.bar-count {
  font-size: 11px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
.bar-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

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

/* ---------- 工作台 ---------- */
.wb {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
  align-items: stretch;
}
.table-panel {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.seg-error {
  padding: 8px 10px;
  border: 1px dashed var(--sev-p0-border);
  border-radius: var(--radius-md);
  color: var(--sev-p0-text);
  font-size: 11.5px;
  flex: none;
}

/* ---------- 表格 ---------- */
.account-table {
  --el-table-border-color: var(--border-subtle);
}
.account-table :deep(.el-table__header th) {
  background: var(--bg-elevated);
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.03em;
}
.account-table :deep(.el-table__cell) {
  padding: 6px 0;
}

/* 行首凭据健康色条：绿=已配置，琥珀=缺凭据，灰=停用 */
.name-cell {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}
.cred-bar {
  flex: none;
  width: 3px;
  height: 24px;
  border-radius: 2px;
  background: var(--status-success);
}
.cred-bar.is-warn {
  background: var(--status-warning);
}
.cred-bar.is-ok {
  background: var(--status-success);
}
.cred-bar.is-off {
  background: var(--border-default);
}
.name-text {
  min-width: 0;
}
.name-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.code {
  font-size: 11px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mono {
  font-family: var(--font-mono, ui-monospace, SFMono-Regular, Menlo, Consolas, monospace);
  font-size: 11px;
}
.ellipsis {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dim {
  color: var(--text-tertiary);
}
.dim.is-expired {
  color: var(--status-error);
}
.time-text {
  font-size: 11px;
  white-space: nowrap;
}
.ops {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* ---------- 检查器 ---------- */
.inspector {
  flex: none;
  width: 304px;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 13px 14px 15px;
  background: var(--bg-container);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
.ins-head {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}
.ins-mark {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: #fff;
}
.ins-mark.is-key {
  background: #f0a020;
}
.ins-mark.is-mail {
  background: #5b8def;
}
.ins-mark.is-sms {
  background: #2fbf71;
}
.ins-mark.is-cookie {
  background: #8b5cf6;
}
.ins-title {
  flex: 1;
  min-width: 0;
}
.ins-title b {
  display: block;
  font-size: 13.5px;
  font-weight: 650;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ins-title span {
  display: block;
  font-size: 11px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ins-flags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.flag {
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid var(--border-default);
  font-size: 10.5px;
  color: var(--text-tertiary);
  background: var(--bg-fill);
}
.flag.is-ok {
  border-color: var(--sev-p3-border);
  background: var(--sev-p3-bg);
  color: var(--sev-p3-text);
}
.flag.is-warn {
  border-color: var(--sev-p2-border);
  background: var(--sev-p2-bg);
  color: var(--sev-p2-text);
}
.flag.is-bad {
  border-color: var(--sev-p0-border);
  background: var(--sev-p0-bg);
  color: var(--sev-p0-text);
}
.flag.is-brand {
  border-color: var(--brand-100, #ece9ff);
  background: var(--brand-50);
  color: var(--brand-600, #5a4bd9);
}

.ins-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 0;
  padding: 4px 0;
  border-top: 1px solid var(--border-subtle);
  border-bottom: 1px solid var(--border-subtle);
}
.ins-list > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 4px 0;
  min-width: 0;
}
.ins-list dt {
  flex: 0 0 62px;
  font-size: 11px;
  color: var(--text-tertiary);
}
.ins-list dd {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 12px;
  color: var(--text-primary);
  word-break: break-all;
}

.ins-note {
  padding: 8px 10px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  background: var(--bg-fill);
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-secondary);
}
.ins-note.is-bad {
  border-color: var(--sev-p0-border);
  background: var(--sev-p0-bg);
  color: var(--sev-p0-text);
}
.ins-note.is-ok {
  border-color: var(--sev-p3-border);
  background: var(--sev-p3-bg);
  color: var(--sev-p3-text);
}

.ins-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ins-meta {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.ins-blank {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  text-align: center;
}
.ins-blank p {
  margin: 0;
  max-width: 210px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-tertiary);
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

/* ---------- 详情 ---------- */
.detail-body {
  padding: 4px 4px 8px;
}
.detail-section {
  margin-bottom: 20px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--text-tertiary);
}
.section-title::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-subtle);
}
.section-extra {
  font-weight: 400;
  letter-spacing: 0;
}
.kv {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  min-width: 0;
}
.kv .k {
  flex: 0 0 84px;
  color: var(--text-tertiary);
}
.kv .v {
  color: var(--text-primary);
  word-break: break-all;
}
.detail-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 4px 16px;
}

/* ---------- 窄屏 ---------- */
@media (max-width: 1100px) {
  .inspector {
    width: 268px;
  }
}
@media (max-width: 900px) {
  .wb {
    flex-direction: column;
  }
  .inspector {
    width: auto;
    max-height: 340px;
  }
  .kpi-band {
    flex-wrap: wrap;
  }
  .kpi {
    flex: 1 1 30%;
    border-bottom: 1px solid var(--border-subtle);
  }
}

@media (prefers-reduced-motion: reduce) {
  .kpi,
  .seg-item {
    transition: none;
  }
}
</style>
