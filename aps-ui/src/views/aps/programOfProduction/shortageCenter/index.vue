<template>
  <section class="workbench">
    <!-- 两条线冲突话术：首次进入完整展示一次，可关闭（方案 7.4 的培训口径落进界面） -->
    <el-alert
      v-if="!hintDismissed"
      class="lines-hint"
      type="info"
      show-icon
      closable
      @close="dismissHint"
      title="齐套线回答“这单能不能干”（按订单需求扣在库与可用在途），水位线回答“该不该补货”（按周对比安全库存）。"
      description="同一物料可以“本单齐套”同时“低于安全水位”——这不是数据错误，是两个问题：前者找采购催料，后者走补货审批。"
    />

    <header>
      <h2>缺料预警</h2>
      <div class="header-actions">
        <el-button v-permission="'aps:shortage:config'" :loading="busy" @click="runScan">手工扫描</el-button>
        <el-button :loading="busy" type="primary" plain @click="refreshAll">刷新</el-button>
      </div>
    </header>

    <!-- 汇总条：数据来自 items 响应的 summary（与筛选无关）；数据质量组从台账接口取数 -->
    <div class="summary-bar">
      <div class="line-chips">
        <el-button size="small" :type="activeGroups.includes('ORDER') ? 'primary' : ''" plain @click="toggleGroup('ORDER')">
          齐套线 {{ summaryValue('ORDER', 'active') }}
        </el-button>
        <el-tag
          v-for="sev in ['P1','P2','P3','P4']" :key="sev"
          :type="severityMeta(sev).type" :effect="filters.severity === sev ? 'dark' : 'light'"
          class="chip" style="cursor:pointer" @click="chipFilter(sev)"
        >{{ sev }} {{ summaryValue('ORDER', sev) || 0 }}</el-tag>
        <el-tag v-if="summaryValue('ORDER', 'NO_SEVERITY')" type="info" effect="plain" class="chip">未定级 {{ summaryValue('ORDER', 'NO_SEVERITY') }}</el-tag>
      </div>
      <div class="line-chips">
        <el-button size="small" :type="activeGroups.includes('WEEK') ? 'primary' : ''" plain @click="toggleGroup('WEEK')">
          水位线 {{ summaryValue('WEEK', 'active') }}
        </el-button>
        <el-tag v-if="summaryValue('WEEK', 'P2')" type="warning" effect="light" class="chip">P2 {{ summaryValue('WEEK', 'P2') }}</el-tag>
        <el-tag v-if="summaryValue('WEEK', 'P3')" type="warning" effect="plain" class="chip">P3 {{ summaryValue('WEEK', 'P3') }}</el-tag>
      </div>
      <el-button v-if="systemCount > 0" size="small" :type="activeGroups.includes('SYSTEM') ? 'warning' : ''" plain @click="toggleGroup('SYSTEM')">
        数据质量 {{ systemCount }} 条待处理
      </el-button>
    </div>

    <el-tabs v-model="mainTab" class="main-tabs">
      <el-tab-pane label="欠料清单" name="items">
        <!-- 筛选对齐套线与水位线共用，全部下推服务端 -->
        <div class="toolbar">
          <el-select v-model="filters.severity" clearable placeholder="等级" style="width:110px" @change="reloadOpenGroups">
            <el-option v-for="sev in ['P1','P2','P3','P4']" :key="sev" :label="sev" :value="sev" />
          </el-select>
          <el-select v-model="filters.status" placeholder="状态（默认未处理）" style="width:175px" @change="reloadOpenGroups">
            <el-option label="未处理（默认）" value="" />
            <el-option label="已确认" value="ACKNOWLEDGED" />
            <el-option label="已静默" value="MUTED" />
            <el-option label="已解决" value="RESOLVED" />
            <el-option label="全部（含已解决）" value="ALL" />
          </el-select>
          <el-input v-model="filters.orderCode" placeholder="订单号（精确匹配）" clearable style="width:190px" @keyup.enter="reloadOpenGroups" @clear="reloadOpenGroups" />
          <el-input v-model="filters.materialCode" placeholder="物料编码（精确匹配）" clearable style="width:190px" @keyup.enter="reloadOpenGroups" @clear="reloadOpenGroups" />
          <el-button :disabled="offset === 0" @click="page(-1)">上一页</el-button>
          <el-button :disabled="!pageFull" @click="page(1)">下一页</el-button>
          <span class="toolbar-total">共 {{ total }} 条</span>
        </div>

        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="error-bar" />

        <el-collapse v-model="activeGroups" @change="onGroupsChange">
          <!-- 齐套线：默认展开 -->
          <el-collapse-item name="ORDER">
            <template #title>
              <span class="group-title">齐套线（按订单，找采购催料）</span>
              <span class="group-count">未处理 {{ summaryValue('ORDER', 'active') }} · P1 {{ summaryValue('ORDER', 'P1') || 0 }} · P2 {{ summaryValue('ORDER', 'P2') || 0 }}</span>
            </template>
            <el-table :data="rows" v-loading="busy" size="small" class="item-table" @row-click="openDetail" row-class-name="clickable">
              <el-table-column label="等级" width="92">
                <template #default="{ row }">
                  <el-tag :type="severityMeta(row.severity).type" :effect="severityMeta(row.severity).plain ? 'plain' : 'dark'" size="small">
                    {{ severityMeta(row.severity).short }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="订单" min-width="130" prop="orderCode" show-overflow-tooltip />
              <el-table-column label="物料" min-width="170">
                <template #default="{ row }">
                  <div class="material-cell"><span>{{ row.materialCode }}</span><span class="muted">{{ row.materialName }}</span></div>
                </template>
              </el-table-column>
              <el-table-column label="缺口" width="120" align="right">
                <template #default="{ row }">
                  <div>缺 {{ formatQuantity(row.gapQuantity) }} {{ row.unit }}</div>
                  <div class="muted">毛需求 {{ formatQuantity(row.requiredQuantity) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="需求时刻" width="120">
                <template #default="{ row }">
                  <span :class="{ overdue: isOverdue(row.requiredAt) }">{{ formatTimeShort(row.requiredAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="供给状态" min-width="160">
                <template #default="{ row }">
                  <el-tag :type="supplyMeta(row.supplyMode).type" size="small" effect="plain">{{ supplyMeta(row.supplyMode).label }}</el-tag>
                  <span class="muted supply-eta">{{ row.supplyMode === 'NONE' || !row.supplyMode ? '—' : formatTimeShort(row.expectedEta) }}
                    <em v-if="etaHint(row.expectedEta)" class="eta-hint">{{ etaHint(row.expectedEta) }}</em></span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="itemStatus(row.status).type" :effect="itemStatus(row.status).effect" size="small">{{ itemStatus(row.status).label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="观测" width="70" align="center">
                <template #default="{ row }">
                  <el-tooltip v-if="row.observedCount > 1" :content="'已被 ' + row.observedCount + ' 轮求解观测到'">
                    <span class="observed">{{ row.observedCount }} 次</span>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" fixed="right">
                <template #default="{ row }">
                  <el-dropdown trigger="click" @command="(action) => onCommand(action, row)" @click.stop>
                    <el-button link type="primary" @click.stop>处理</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="ack">确认（处理中）</el-dropdown-item>
                        <el-dropdown-item command="resolve">解决（登记处理说明）</el-dropdown-item>
                        <el-dropdown-item command="mute" :disabled="row.severity === 'P1'" divided>
                          静默{{ row.severity === 'P1' ? '（P1 不允许）' : '' }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty :description="filtersEmpty ? '当前没有未处理的齐套缺料。生成排产后此处自动汇总。' : '没有匹配的缺料记录，试试清空筛选。'" :image-size="60" />
              </template>
            </el-table>
          </el-collapse-item>

          <!-- 水位线：默认折叠（方案 7.1 硬性要求） -->
          <el-collapse-item name="WEEK">
            <template #title>
              <span class="group-title">水位线（按周，走补货审批）</span>
              <span class="group-count">未处理 {{ summaryValue('WEEK', 'active') }} · P2 {{ summaryValue('WEEK', 'P2') || 0 }} · P3 {{ summaryValue('WEEK', 'P3') || 0 }}</span>
            </template>
            <el-table :data="rows" v-loading="busy" size="small" class="item-table" @row-click="openDetail" row-class-name="clickable">
              <el-table-column label="等级" width="92">
                <template #default="{ row }">
                  <el-tag :type="severityMeta(row.severity).type" :effect="severityMeta(row.severity).plain ? 'plain' : 'dark'" size="small">
                    {{ severityMeta(row.severity).short }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="地点 / 物料" min-width="200">
                <template #default="{ row }">
                  <div class="material-cell"><span>{{ row.locationCode }} · {{ row.materialCode }}</span><span class="muted">{{ ruleName(row.reasonCode) }}{{ row.reference && row.reference !== '-' ? ' · ' + row.reference : '' }}</span></div>
                </template>
              </el-table-column>
              <el-table-column label="发生周" width="110">
                <template #default="{ row }"><span>{{ row.weekAt || formatTimeShort(row.requiredAt) }}</span></template>
              </el-table-column>
              <el-table-column label="缺口" width="120" align="right">
                <template #default="{ row }">
                  <div>{{ formatQuantity(row.gapQuantity) }} {{ row.unit }}</div>
                  <div class="muted">安全水位 {{ formatQuantity(row.safetyStock) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="itemStatus(row.status).type" :effect="itemStatus(row.status).effect" size="small">{{ itemStatus(row.status).label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" fixed="right">
                <template #default="{ row }">
                  <el-dropdown trigger="click" @command="(action) => onCommand(action, row)" @click.stop>
                    <el-button link type="primary" @click.stop>处理</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="ack">确认（处理中）</el-dropdown-item>
                        <el-dropdown-item command="resolve">解决（登记处理说明）</el-dropdown-item>
                        <el-dropdown-item command="mute" :disabled="row.severity === 'P1'" divided>
                          静默{{ row.severity === 'P1' ? '（P1 不允许）' : '' }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="当前没有未处理的水位预警。" :image-size="60" />
              </template>
            </el-table>
          </el-collapse-item>

          <!-- 数据质量与系统级预警：只读展示 + 详情，处理走运维通道 -->
          <el-collapse-item name="SYSTEM" v-if="systemCount > 0">
            <template #title>
              <span class="group-title">数据质量与系统预警</span>
              <span class="group-count">{{ systemCount }} 条</span>
            </template>
            <el-table :data="systemAlerts" v-loading="busy" size="small" @row-click="openAlert" row-class-name="clickable">
              <el-table-column label="等级" width="92">
                <template #default="{ row }">
                  <el-tag :type="severityMeta(row.severity).type" size="small" effect="plain">{{ severityMeta(row.severity).short }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="规则" width="130">
                <template #default="{ row }"><span>{{ ruleName(row.ruleCode) }}</span></template>
              </el-table-column>
              <el-table-column label="标题" min-width="280" prop="title" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="alertStatus(row.status).type" :effect="alertStatus(row.status).effect" size="small">{{ alertStatus(row.status).label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="最近观测" width="130">
                <template #default="{ row }"><span>{{ formatTimeFull(row.lastObservedAt) }}</span></template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>

      <!-- 预警台账：审计与闭环验证，不是日常分诊入口 -->
      <el-tab-pane label="预警台账" name="alerts">
        <div class="toolbar">
          <el-select v-model="alertFilters.severity" clearable placeholder="等级" style="width:110px" @change="loadAlerts">
            <el-option v-for="sev in ['P1','P2','P3','P4']" :key="sev" :label="sev" :value="sev" />
          </el-select>
          <el-select v-model="alertFilters.status" clearable placeholder="状态" style="width:130px" @change="loadAlerts">
            <el-option v-for="(meta, key) in alertStatusMap" :key="key" :label="meta.label" :value="key" />
          </el-select>
          <el-select v-model="alertFilters.ruleCode" clearable filterable placeholder="规则" style="width:190px" @change="loadAlerts">
            <el-option v-for="(name, code) in ruleNames" :key="code" :label="name" :value="code" />
          </el-select>
          <el-button :disabled="alertOffset === 0" @click="alertPage(-1)">上一页</el-button>
          <el-button :disabled="!alertPageFull" @click="alertPage(1)">下一页</el-button>
          <span class="toolbar-total">共 {{ alertTotal }} 条</span>
        </div>
        <el-table :data="alerts" v-loading="busy" size="small" @row-click="openAlert" row-class-name="clickable">
          <el-table-column label="等级" width="92">
            <template #default="{ row }">
              <el-tag :type="severityMeta(row.severity).type" size="small" effect="dark">{{ severityMeta(row.severity).short }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规则" width="150">
            <template #default="{ row }"><span>{{ ruleName(row.ruleCode) }}</span></template>
          </el-table-column>
          <el-table-column label="标题" min-width="320" prop="title" show-overflow-tooltip />
          <el-table-column label="维度" width="90">
            <template #default="{ row }"><span>{{ dimensionName(row.dimension) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tooltip v-if="row.status === 'SUPERSEDED'" content="同缺口等级升高后由新预警接续，旧预警留痕">
                <el-tag :type="alertStatus(row.status).type" :effect="alertStatus(row.status).effect" size="small">{{ alertStatus(row.status).label }}</el-tag>
              </el-tooltip>
              <el-tag v-else :type="alertStatus(row.status).type" :effect="alertStatus(row.status).effect" size="small">{{ alertStatus(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="观测" width="150">
            <template #default="{ row }"><span>{{ row.observedCount }} 次 · 最近 {{ formatTimeShort(row.lastObservedAt) }}</span></template>
          </el-table-column>
          <el-table-column label="推送" width="150">
            <template #default="{ row }">
              <span>{{ row.pushedCount }} 次
                <em v-if="row.pushedCount === 0 && row.status === 'OPEN'" class="eta-hint">未送达</em>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="创建" width="120">
            <template #default="{ row }"><span>{{ formatTimeShort(row.createdAt) }}</span></template>
          </el-table-column>
          <template #empty><el-empty description="没有符合条件的预警。" :image-size="60" /></template>
        </el-table>
      </el-tab-pane>
      <!-- 规则配置：默认值灰显，覆盖只提交改动字段；计划经理与运维 -->
      <el-tab-pane v-if="canConfig" label="规则配置" name="rules">
        <el-alert type="warning" show-icon :closable="false" class="lines-hint"
          title="默认阈值来自通用生产节奏，非本厂事实数据，上线前须按实际节奏标定。" />
        <el-table :data="rules" v-loading="busy" size="small" @row-click="editRule">
          <el-table-column label="规则" min-width="180">
            <template #default="{ row }">
              <div class="material-cell">
                <span>{{ ruleName(row.ruleCode) }}<el-tag v-if="row.overridden" size="small" effect="plain" style="margin-left:6px">已覆盖</el-tag></span>
                <span class="muted mono">{{ row.ruleCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="维度" width="80">
            <template #default="{ row }"><span>{{ dimensionName(row.dimension) }}</span></template>
          </el-table-column>
          <el-table-column label="等级（默认）" width="120">
            <template #default="{ row }">
              <el-tag :type="severityMeta(row.severity).type" :effect="row.overridden && row.override && row.override.severity ? 'dark' : 'light'" size="small">{{ row.severity }}</el-tag>
              <span class="muted"> {{ row.defaultSeverity }}</span>
            </template>
          </el-table-column>
          <el-table-column label="阈值（默认）" width="150">
            <template #default="{ row }">
              <span>{{ row.thresholdValue === null ? '—' : row.thresholdValue + ' ' + row.thresholdUnit }}</span>
              <span class="muted"> {{ row.defaultThresholdValue === null ? '—' : row.defaultThresholdValue + ' ' + row.defaultThresholdUnit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="冷却（默认）" width="120">
            <template #default="{ row }"><span>{{ row.cooldownMinutes }} 分</span><span class="muted"> {{ row.defaultCooldownMinutes }} 分</span></template>
          </el-table-column>
          <el-table-column label="接收角色（默认）" min-width="170">
            <template #default="{ row }"><span>{{ roleNames(row.receivers) }}</span><span class="muted"> {{ roleNames(row.defaultReceivers) }}</span></template>
          </el-table-column>
          <el-table-column label="通道" width="130">
            <template #default="{ row }"><span>{{ channelNames(row.channels) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="pendingRules.includes(row.ruleCode)" @click.stop="editRule(row)">
                {{ pendingRules.includes(row.ruleCode) ? '待启用' : '覆盖' }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="规则目录为空。" :image-size="60" /></template>
        </el-table>
        <div class="muted form-note" style="margin-top:8px">
          覆盖只提交被修改的字段，其余沿用默认；站内台账为必选通道不可去除；当前接口不支持恢复默认，覆盖前请确认。
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 规则覆盖弹窗 -->
    <el-dialog v-model="ruleVisible" :title="'覆盖规则：' + (ruleEdit.code ? ruleName(ruleEdit.code) : '')" width="560px" :close-on-click-modal="false">
      <el-form label-width="110px" v-if="ruleEdit.code">
        <el-form-item label="启用">
          <el-switch v-model="ruleEdit.enabled" />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="ruleEdit.severity" style="width:160px">
            <el-option v-for="sev in ['P1','P2','P3','P4']" :key="sev" :label="sev + ' · ' + severityMeta(sev).label" :value="sev" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="ruleEdit.thresholdTouched !== undefined" label="阈值">
          <el-input-number v-model="ruleEdit.thresholdValue" :min="0" style="width:130px" />
          <el-select v-model="ruleEdit.thresholdUnit" style="width:110px;margin-left:8px">
            <el-option label="天" value="DAYS" /><el-option label="小时" value="HOURS" /><el-option label="分钟" value="MINUTES" />
          </el-select>
        </el-form-item>
        <el-form-item label="冷却期（分）">
          <el-input-number v-model="ruleEdit.cooldownMinutes" :min="0" :max="10080" style="width:160px" />
        </el-form-item>
        <el-form-item label="接收角色">
          <el-select v-model="ruleEdit.receivers" multiple style="width:100%">
            <el-option v-for="(label, code) in roleOptions" :key="code" :label="label" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="通道">
          <el-checkbox-group v-model="ruleEdit.channels">
            <el-checkbox label="LEDGER" disabled>站内台账（必选）</el-checkbox>
            <el-checkbox label="WEBHOOK">Webhook</el-checkbox>
            <el-checkbox label="OUTBOX">拉取出站</el-checkbox>
            <el-checkbox label="EMAIL">邮件</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="建议动作">
          <el-input v-model="ruleEdit.actionHint" maxlength="200" show-word-limit placeholder="留空沿用默认" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitRule">保存覆盖</el-button>
      </template>
    </el-dialog>

    <!-- 条目详情抽屉：五区，自上而下按计划员的追问顺序 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="56%" destroy-on-close>
      <div v-if="detail" class="drawer-body">
        <!-- A 结论区：等级与判断来自规则，事实在下面各区 -->
        <div class="section conclusion">
          <div class="conclusion-head">
            <el-tag :type="severityMeta(detail.severity).type" effect="dark">{{ severityMeta(detail.severity).label }}</el-tag>
            <span class="rule-name">{{ ruleName(detail.reasonCode) }}</span>
            <span v-if="severityMeta(detail.severity).response" class="muted">{{ severityMeta(detail.severity).response }}</span>
            <el-tag :type="itemStatus(detail.status).type" :effect="itemStatus(detail.status).effect" size="small" style="margin-left:auto">{{ itemStatus(detail.status).label }}</el-tag>
          </div>
          <div class="conclusion-title">{{ detailTitle }}</div>
          <el-alert v-if="actionHint" :title="'建议动作：' + actionHint" type="warning" show-icon :closable="false" class="hint-bar" />
          <div class="drawer-actions">
            <el-button size="small" v-permission="'aps:shortage:handle'" @click="onCommand('ack', detail)">确认（处理中）</el-button>
            <el-button size="small" type="primary" v-permission="'aps:shortage:handle'" @click="onCommand('resolve', detail)">解决</el-button>
            <el-tooltip v-if="detail.severity === 'P1'" content="P1 已影响上线，不允许静默" placement="top">
              <span><el-button size="small" disabled>静默</el-button></span>
            </el-tooltip>
            <el-button v-else size="small" v-permission="'aps:shortage:handle'" @click="onCommand('mute', detail)">静默</el-button>
          </div>
        </div>

        <!-- B 缺口瀑布：缺口 = 毛需求 − 在库已扣 − 在途已扣 -->
        <div class="section">
          <div class="section-title">缺口事实</div>
          <div class="waterfall">
            <div v-for="step in waterfall" :key="step.label" class="waterfall-step" :class="{ final: step.final }">
              <div class="muted">{{ step.label }}</div>
              <div class="value">{{ step.text }}</div>
            </div>
          </div>
          <div class="muted cutoff">依据 {{ formatTimeFull(detail.dataCutoff) }} 的数据 · 运行 {{ shortRun(detail.runId) }}</div>
        </div>

        <!-- C 在途明细：隐形缺料的显影处，完整展示不分页 -->
        <div class="section">
          <div class="section-title">在途明细</div>
          <template v-if="detail.skippedLines && detail.skippedLines.length">
            <div class="muted table-caption">因到货晚于交期被跳过（{{ formatQuantity(detail.inTransitSkipped) }} {{ detail.unit }}）</div>
            <el-table :data="detail.skippedLines" size="small" border>
              <el-table-column prop="lineCode" label="在途行" min-width="120" />
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQuantity(row.quantity) }}</template></el-table-column>
              <el-table-column label="到货" width="120"><template #default="{ row }">{{ formatTimeShort(row.deliveryTime) }}</template></el-table-column>
              <el-table-column label="可用（含缓冲）" width="140">
                <template #default="{ row }"><span :class="{ overdue: isOverdue(row.availableAt) }">{{ formatTimeShort(row.availableAt) }}</span></template>
              </el-table-column>
            </el-table>
          </template>
          <div v-else class="muted table-caption">没有被跳过的在途行。</div>
          <template v-if="detail.availableInTransit && detail.availableInTransit.length">
            <div class="muted table-caption">参与分配的在途（已扣 {{ formatQuantity(detail.inTransitConsumed) }} {{ detail.unit }}）</div>
            <el-table :data="detail.availableInTransit" size="small" border>
              <el-table-column prop="lineCode" label="在途行" min-width="120" />
              <el-table-column label="数量" width="100" align="right"><template #default="{ row }">{{ formatQuantity(row.quantity) }}</template></el-table-column>
              <el-table-column label="本次分配" width="100" align="right"><template #default="{ row }">{{ formatQuantity(row.usedQuantity) }}</template></el-table-column>
              <el-table-column label="可用" width="120"><template #default="{ row }">{{ formatTimeShort(row.availableAt) }}</template></el-table-column>
            </el-table>
          </template>
          <el-descriptions :column="2" border size="small" class="supply-desc">
            <el-descriptions-item label="供给状态">
              <el-tag :type="supplyMeta(detail.supplyMode).type" size="small" effect="plain">{{ supplyMeta(detail.supplyMode).label }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="预计到货">
              {{ detail.supplyMode === 'NONE' || !detail.supplyMode ? '—（无到货计划）' : formatTimeFull(detail.expectedEta) }}
              <em v-if="etaHint(detail.expectedEta)" class="eta-hint">{{ etaHint(detail.expectedEta) }}</em>
            </el-descriptions-item>
            <el-descriptions-item label="供应商">{{ detail.supplierCode || '—' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- D 影响面：这单被推了多久、影响哪些任务 -->
        <div class="section">
          <div class="section-title">影响面</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="最早可开工">{{ detail.earliestStart ? formatTimeFull(detail.earliestStart) : '—' }}</el-descriptions-item>
            <el-descriptions-item label="较计划推迟">{{ detail.delayDuration ? formatIsoDuration(detail.delayDuration) : '—' }}</el-descriptions-item>
            <el-descriptions-item label="受影响任务">{{ (detail.downstreamTasks || []).length }} 项</el-descriptions-item>
          </el-descriptions>
          <el-table v-if="detail.downstreamTasks && detail.downstreamTasks.length" :data="detail.downstreamTasks" size="small" border class="tasks-table">
            <el-table-column prop="taskCode" label="任务" min-width="150" show-overflow-tooltip />
            <el-table-column prop="processCode" label="工序" width="90" />
            <el-table-column prop="resourceName" label="资源" width="110" show-overflow-tooltip />
            <el-table-column label="计划开始" width="120"><template #default="{ row }">{{ formatTimeShort(row.plannedStartTime) }}</template></el-table-column>
            <el-table-column label="计划完成" width="120"><template #default="{ row }">{{ formatTimeShort(row.plannedEndTime) }}</template></el-table-column>
          </el-table>
          <div v-else class="muted table-caption">本轮求解尚未关联受影响任务（倒排或求解未完成时为空）。</div>
        </div>

        <!-- E 归属与轨迹：凭什么是我这单背缺口 + 处理留痕 -->
        <div class="section">
          <el-collapse class="inner-collapse">
            <el-collapse-item name="attribution">
              <template #title><span class="section-title inline">归属依据与处理轨迹</span></template>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="扣减位次">第 {{ (detail.detail && detail.detail.orderSequence + 1) || '—' }} 个扣减</el-descriptions-item>
                <el-descriptions-item label="排序规则">{{ (detail.detail && detail.detail.sortRule) || '—' }}</el-descriptions-item>
                <el-descriptions-item label="物料期初库存">{{ detail.detail ? formatQuantity(detail.detail.onHandStart) : '—' }}</el-descriptions-item>
                <el-descriptions-item label="此前订单已占用">{{ detail.detail && detail.detail.onHandReservedEarlier !== undefined ? formatQuantity(detail.detail.onHandReservedEarlier) : '—' }}</el-descriptions-item>
                <el-descriptions-item label="轮到本单时余量">{{ formatQuantity(detail.onHandAvailable) }}</el-descriptions-item>
                <el-descriptions-item label="出现次数">{{ (detail.detail && detail.detail.occurrences) || 1 }} 处</el-descriptions-item>
                <el-descriptions-item label="提前期 / 缓冲期">
                  {{ detail.detail ? (formatIsoDuration(detail.detail.leadTime) + ' / ' + formatIsoDuration(detail.detail.bufferTime)) : '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="连续缺料自">{{ formatTimeFull(detail.firstDetectedAt) }}</el-descriptions-item>
                <el-descriptions-item label="处理人">{{ detail.resolvedBy || '—' }}</el-descriptions-item>
                <el-descriptions-item label="处理说明">{{ detail.resolutionNote || '—' }}</el-descriptions-item>
              </el-descriptions>
              <div class="muted attribution-note">
                多张订单竞争同一批库存时，先处理的订单先占用：扣减位次与排序规则就是本单背缺口的依据。
                换一种排序顺序，背缺口的就是另一张单。
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </el-drawer>

    <!-- 预警详情抽屉：payload 原文与推送记录（闭环证据） -->
    <el-drawer v-model="alertVisible" :title="alertTitle" size="44%" destroy-on-close>
      <div v-if="alert" class="drawer-body">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="等级">{{ severityMeta(alert.severity).label }}</el-descriptions-item>
          <el-descriptions-item label="规则">{{ ruleName(alert.ruleCode) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ alertStatus(alert.status).label }}</el-descriptions-item>
          <el-descriptions-item label="维度">{{ dimensionName(alert.dimension) }}</el-descriptions-item>
          <el-descriptions-item label="观测">{{ alert.observedCount }} 次，最近 {{ formatTimeFull(alert.lastObservedAt) }}</el-descriptions-item>
          <el-descriptions-item label="推送">{{ alert.pushedCount }} 次，最近 {{ formatTimeFull(alert.lastPushedAt) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="首现">{{ formatTimeFull(alert.firstObservedAt) }}</el-descriptions-item>
          <el-descriptions-item label="确认人">{{ alert.acknowledgedBy || '—' }}</el-descriptions-item>
          <el-descriptions-item label="处理说明" :span="2">{{ alert.resolutionNote || '—' }}</el-descriptions-item>
          <el-descriptions-item label="去重键" :span="2"><span class="mono">{{ alert.alertKey }}</span></el-descriptions-item>
        </el-descriptions>
        <div class="section-title" style="margin-top:14px">推送记录（回执即送达证据）</div>
        <el-table :data="alertNotifies" size="small" border v-loading="busy">
          <el-table-column label="渠道" width="100"><template #default="{ row }">{{ channelName(row.channel) }}</template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{ row }">{{ notifyStatus(row.status) }}</template></el-table-column>
          <el-table-column label="尝试" width="60" align="center" prop="attempts" />
          <el-table-column label="送达时刻" width="120"><template #default="{ row }">{{ formatTimeShort(row.sentAt) }}</template></el-table-column>
          <el-table-column label="回执" width="80"><template #default="{ row }">{{ receiptName(row.receiptState) || '—' }}</template></el-table-column>
          <el-table-column label="最近错误" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.lastError || '—' }}</template>
          </el-table-column>
          <template #empty><el-empty description="没有推送记录。" :image-size="50" /></template>
        </el-table>
        <div class="section-title" style="margin-top:14px">推送原文（各渠道一致，服务端模板生成）</div>
        <pre class="payload">{{ payloadText }}</pre>
      </div>
    </el-drawer>

    <!-- 解决 / 静默 弹窗 -->
    <el-dialog v-model="actionVisible" :title="actionTitle" width="480px" :close-on-click-modal="false">
      <el-form label-position="top" v-if="actionTarget">
        <el-form-item v-if="actionType === 'resolve'">
          <template #label>处理说明（必填）</template>
          <el-select v-model="quickReason" placeholder="常用理由快捷填入" clearable style="width:100%;margin-bottom:8px" @change="applyQuickReason">
            <el-option v-for="reason in quickReasons" :key="reason" :label="reason" :value="reason" />
          </el-select>
          <el-input v-model="actionNote" type="textarea" :rows="3" maxlength="500" show-word-limit
            :placeholder="actionType === 'resolve' ? '处理说明（必填）：如已向 SUP-0087 催料，预计 11-30 到货' : '静默理由（必填）'" />
        </el-form-item>
        <el-form-item v-else>
          <template #label>静默理由（必填）</template>
          <el-input v-model="actionNote" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="静默理由（必填）" />
          <div class="muted form-note">静默后本条不再推送、不再出现在默认列表；等级升到 P1 时自动解除。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitAction">{{ actionType === 'resolve' ? '登记并解决' : '静默' }}</el-button>
      </template>
    </el-dialog>

    <!-- 手工扫描结果：计数必须可见，扫描本就是验证手段 -->
    <el-dialog v-model="scanVisible" title="手工扫描结果" width="560px">
      <el-descriptions v-if="scanReport" :column="2" border size="small">
        <el-descriptions-item label="复评条目">{{ scanReport.itemsEvaluated }}</el-descriptions-item>
        <el-descriptions-item label="新建预警">{{ scanReport.alerts.created }}</el-descriptions-item>
        <el-descriptions-item label="重复观测">{{ scanReport.alerts.repeated }}</el-descriptions-item>
        <el-descriptions-item label="等级接续">{{ scanReport.alerts.superseded }}</el-descriptions-item>
        <el-descriptions-item label="关闭预警">{{ scanReport.alerts.resolved }}</el-descriptions-item>
        <el-descriptions-item label="复评失败">{{ scanReport.alerts.failed }}</el-descriptions-item>
        <el-descriptions-item label="认领投递">{{ scanReport.dispatch.claimed }}</el-descriptions-item>
        <el-descriptions-item label="送达">{{ scanReport.dispatch.sent }}</el-descriptions-item>
        <el-descriptions-item label="待重试">{{ scanReport.dispatch.failed }}</el-descriptions-item>
        <el-descriptions-item label="重试耗尽">{{ scanReport.dispatch.dead }}</el-descriptions-item>
        <el-descriptions-item label="限流排队">{{ scanReport.dispatch.deferred }}</el-descriptions-item>
      </el-descriptions>
      <div class="section-title" style="margin-top:12px">数据质量规则</div>
      <div class="quality-notes">
        <div v-for="(note, key) in (scanReport && scanReport.dataQuality) || {}" :key="key" class="quality-note">
          <span class="mono">{{ key }}</span><span>{{ note }}</span>
        </div>
      </div>
      <template #footer><el-button type="primary" @click="scanVisible = false">知道了</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  alertDetail, handleItem, itemDetail, listAlerts, listItems, listNotifies, listRules, saveRule, scan
} from './api'
import { useRoute } from 'vue-router'
import { hasPermission } from '@/util/permission'
import {
  ALERT_STATUS, CHANNEL_NAMES, ITEM_STATUS, NOTIFY_STATUS, RECEIPT_NAMES, RULE_NAMES,
  etaHint, formatIsoDuration, formatQuantity, formatTimeFull, formatTimeShort,
  ruleName, severityMeta, supplyMeta
} from '@/util/shortageFormat'

const PAGE_SIZE = 50
const HINT_KEY = 'shortage.linesHint.dismissed'
const route = useRoute()
const canConfig = hasPermission('aps:shortage:config')
// 依赖外部同步通道的两条规则：通道未落地前不允许通过界面启用。
const pendingRules = ['DATA_STALE', 'SYNC_DEAD_LETTER']
const roleOptions = {
  APS_SCHEDULER: '计划员', APS_PURCHASER: '采购', APS_DEMAND_PLANNER: '物控',
  APS_PLAN_MANAGER: '计划经理', APS_OPS: '运维'
}

const busy = ref(false)
const error = ref('')
const mainTab = ref('items')
const hintDismissed = ref(localStorage.getItem(HINT_KEY) === '1')

// 欠料清单：两条线共用一份数据与筛选；当前展开哪条线，列表就是哪条线。
const activeGroups = ref(['ORDER'])
const currentDimension = computed(() => (activeGroups.value.includes('WEEK') ? 'WEEK' : 'ORDER'))
const filters = reactive({ severity: '', status: '', orderCode: '', materialCode: '' })
const offset = ref(0)
const total = ref(0)
const pageFull = computed(() => offset.value + PAGE_SIZE < total.value)
const rows = ref([])
const summary = ref({})

// 数据质量组：系统级预警住在台账里，从 alerts 接口取数。
const systemAlerts = ref([])
const systemCount = ref(0)

// 台账
const alertFilters = reactive({ severity: '', status: '', ruleCode: '' })
const alerts = ref([])
const alertTotal = ref(0)
const alertOffset = ref(0)
const alertPageFull = computed(() => alertOffset.value + PAGE_SIZE < alertTotal.value)

// 详情抽屉（条目）与预警抽屉
const detailVisible = ref(false)
const detail = ref(null)
const alertVisible = ref(false)
const alert = ref(null)
const alertNotifies = ref([])

// 规则配置
const rules = ref([])
const ruleVisible = ref(false)
const ruleEdit = reactive({ code: '', enabled: true, severity: '', thresholdValue: null, thresholdUnit: 'DAYS', thresholdTouched: undefined, cooldownMinutes: null, receivers: [], channels: [], actionHint: '' })
const ruleBefore = ref(null)

// 处理动作与扫描
const actionVisible = ref(false)
const actionType = ref('resolve')
const actionTarget = ref(null)
const actionNote = ref('')
const quickReason = ref('')
const quickReasons = ['已催料，供应商确认到货', '已改期，交期顺延', '已分批，先齐套部分投产', '订单已取消', '改用替代料']
const scanVisible = ref(false)
const scanReport = ref(null)

const ruleNames = RULE_NAMES
const alertStatusMap = ALERT_STATUS

const filtersEmpty = computed(() => !filters.severity && !filters.status && !filters.orderCode && !filters.materialCode)
const detailTitle = computed(() => {
  if (!detail.value) return '欠料明细'
  const line = detail.value.dimension === 'WEEK'
    ? `${detail.value.locationCode || '—'} · ${detail.value.materialCode}`
    : `订单 ${detail.value.orderCode || '—'} · ${detail.value.materialCode}`
  return `${line} · 缺 ${formatQuantity(detail.value.gapQuantity)} ${detail.value.unit || ''}`
})
const alertTitle = computed(() => (alert.value ? alert.value.title : '预警明细'))
const actionHint = computed(() => {
  // 建议动作取命中规则的口径：P1 用提前期违例话术，其余按规则码映射。
  if (!detail.value) return ''
  return actionHints[detail.value.reasonCode] || ''
})
const actionHints = {
  MRP_GAP_FROZEN: '立即催料或改期，准备分批',
  MRP_GAP_3D: '催料并确认到货',
  MRP_GAP_14D: '纳入催料清单',
  MRP_ETA_PASSED: '核实到货时间，或改交期，或分批',
  MRP_NO_SUPPLY: '无在途也无采购建议，立即下单',
  MRP_LEADTIME_VIOLATION: '在途全部晚于交期，加急或改期',
  DEMAND_SHORTAGE: '走补货审批',
  DEMAND_BELOW_SAFETY: '关注，不强制动作',
  DEMAND_STALE_ETA: '核实交期或取消余量',
  DEMAND_PROPOSAL_BLOCKED: '调整起订量、库容或预算',
  DEMAND_PENDING_RECONCILE: '完成对账'
}

const waterfall = computed(() => {
  if (!detail.value) return []
  const d = detail.value
  const unit = d.unit || ''
  return [
    { label: '毛需求', text: `${formatQuantity(d.requiredQuantity)} ${unit}` },
    { label: '在库已扣', text: `− ${formatQuantity(d.onHandConsumed)} ${unit}` },
    { label: '在途已扣', text: `− ${formatQuantity(d.inTransitConsumed)} ${unit}` },
    { label: '在途被跳过', text: `${formatQuantity(d.inTransitSkipped)} ${unit}` },
    { label: '缺口', text: `${formatQuantity(d.gapQuantity)} ${unit}`, final: true }
  ]
})

const payloadText = computed(() => {
  const payload = alert.value && alert.value.payload
  if (payload === undefined || payload === null) return '—'
  return typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2)
})

function summaryValue(line, key) {
  const value = summary.value[line] && summary.value[line][key]
  return value === undefined ? (key === 'active' ? 0 : undefined) : value
}
function itemStatus(status) { return ITEM_STATUS[status] || { label: status, type: 'info' } }
function alertStatus(status) { return ALERT_STATUS[status] || { label: status, type: 'info' } }
function notifyStatus(status) { return NOTIFY_STATUS[status] || status || '—' }
function receiptName(state) { return state ? RECEIPT_NAMES[state] || state : '' }
function channelName(channel) { return CHANNEL_NAMES[channel] || channel }
function dimensionName(dimension) {
  return { ORDER: '齐套线', WEEK: '水位线', SYSTEM: '系统' }[dimension] || dimension
}
function roleNames(codes) {
  return (codes || []).map(code => roleOptions[code] || code).join('、') || '—'
}
function channelNames(codes) {
  return (codes || []).map(code => CHANNEL_NAMES[code] || code).join('、')
}
function shortRun(runId) { return runId ? String(runId).slice(0, 8) : '—' }
function isOverdue(time) { return time ? etaHint(time).startsWith('已过期') : false }

// 守卫版：按钮动作用。动作完成后的刷新也走它会被 busy 守卫吞掉，所以数据加载一律用 quiet。
async function api(action) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (e) {
    error.value = (e && (e.response?.data?.msg || e.message)) || '请求失败'
  } finally {
    busy.value = false
  }
}

// 无守卫版：只做错误呈现，允许在动作事务内复用刷新。
async function quiet(action) {
  try {
    await action()
  } catch (e) {
    error.value = (e && (e.response?.data?.msg || e.message)) || '请求失败'
  }
}

function itemParams(extra = {}) {
  const params = { dimension: currentDimension.value, offset: offset.value, limit: PAGE_SIZE, ...extra }
  if (filters.severity) params.severity = filters.severity
  if (filters.status && filters.status !== 'ALL') params.status = filters.status
  if (filters.status === 'ALL') params.includeClosed = true
  if (filters.orderCode.trim()) params.orderCode = filters.orderCode.trim()
  if (filters.materialCode.trim()) params.materialCode = filters.materialCode.trim()
  return params
}

async function loadItems() {
  await quiet(async () => {
    const data = await unwrap(listItems(itemParams()))
    rows.value = data.records || []
    total.value = data.total || 0
    summary.value = data.summary || {}
  })
}

async function loadSystem() {
  await quiet(async () => {
    const data = await unwrap(listAlerts({ dimension: 'SYSTEM', status: 'OPEN', limit: PAGE_SIZE }))
    systemAlerts.value = data.records || []
    systemCount.value = data.total || 0
  })
}

async function loadAlerts() {
  await quiet(async () => {
    const params = { offset: alertOffset.value, limit: PAGE_SIZE }
    if (alertFilters.severity) params.severity = alertFilters.severity
    if (alertFilters.status) params.status = alertFilters.status
    if (alertFilters.ruleCode) params.ruleCode = alertFilters.ruleCode
    const data = await unwrap(listAlerts(params))
    alerts.value = data.records || []
    alertTotal.value = data.total || 0
  })
}

async function unwrap(promise) {
  const response = await promise
  if (response.data && response.data.code === 0) return response.data.data
  throw new Error((response.data && response.data.msg) || '请求失败')
}

function toggleGroup(name) {
  const index = activeGroups.value.indexOf(name)
  if (index >= 0) activeGroups.value.splice(index, 1)
  else activeGroups.value.push(name)
}

function onGroupsChange() {
  // 展开即加载对应数据；收起齐套线时若水位线展开，则当前维度切到水位线。
  if (activeGroups.value.includes('WEEK')) loadItems()
  else if (activeGroups.value.includes('ORDER')) loadItems()
  if (activeGroups.value.includes('SYSTEM')) loadSystem()
}

function chipFilter(severity) {
  filters.severity = filters.severity === severity ? '' : severity
  offset.value = 0
  loadItems()
}

function reloadOpenGroups() {
  offset.value = 0
  if (activeGroups.value.includes('ORDER') || activeGroups.value.includes('WEEK')) loadItems()
}

function page(delta) {
  offset.value += delta * PAGE_SIZE
  loadItems()
}

function alertPage(delta) {
  alertOffset.value += delta * PAGE_SIZE
  loadAlerts()
}

function openDetail(row) {
  detailVisible.value = true
  api(async () => {
    detail.value = await unwrap(itemDetail(row.shortageKey))
  })
}

function openAlert(row) {
  alertVisible.value = true
  alert.value = row
  alertNotifies.value = []
  api(async () => {
    alert.value = await unwrap(alertDetail(row.alertId))
    const data = await unwrap(listNotifies(row.alertId))
    alertNotifies.value = data.records || []
  })
}

function onCommand(action, row) {
  if (action === 'ack') {
    api(async () => {
      await unwrap(handleItem(row.shortageKey, 'ack', null))
      ElMessage.success('已确认。请按建议动作处理，解决时回到这里登记。')
      await afterAction(row)
    })
    return
  }
  actionType.value = action
  actionTarget.value = row
  actionNote.value = ''
  quickReason.value = ''
  actionVisible.value = true
}

const actionTitle = computed(() => (actionType.value === 'resolve' ? '解决：登记处理说明' : '静默本条预警'))

function applyQuickReason() {
  if (quickReason.value) actionNote.value = quickReason.value
}

function submitAction() {
  const note = actionNote.value.trim()
  if (!note) {
    ElMessage.warning(actionType.value === 'resolve' ? '请填写处理说明' : '静默必须填写理由')
    return
  }
  api(async () => {
    await unwrap(handleItem(actionTarget.value.shortageKey, actionType.value, note))
    actionVisible.value = false
    ElMessage.success(actionType.value === 'resolve' ? '已解决，未投出的推送已取消' : '已静默，默认列表不再显示')
    await afterAction(actionTarget.value)
  })
}

async function afterAction(row) {
  if (detailVisible.value && detail.value && detail.value.shortageKey === row.shortageKey) {
    detail.value = await unwrap(itemDetail(row.shortageKey))
  }
  await Promise.all([loadItems(), loadSystem()])
}

async function loadRules() {
  await quiet(async () => {
    rules.value = await unwrap(listRules()) || []
  })
}

function editRule(row) {
  if (pendingRules.includes(row.ruleCode)) {
    ElMessage.info('该规则依赖外部同步通道，通道落地前不可配置')
    return
  }
  ruleEdit.code = row.ruleCode
  ruleEdit.enabled = row.enabled
  ruleEdit.severity = row.severity
  ruleEdit.thresholdValue = row.thresholdValue
  ruleEdit.thresholdUnit = row.thresholdUnit || 'DAYS'
  // 原本无阈值的规则也可以新设阈值，编辑时直接给 0 起步。
  ruleEdit.thresholdTouched = row.thresholdValue !== null && row.thresholdValue !== undefined
    ? row.thresholdValue : (row.defaultThresholdValue !== null && row.defaultThresholdValue !== undefined ? row.defaultThresholdValue : 0)
  ruleEdit.cooldownMinutes = row.cooldownMinutes
  ruleEdit.receivers = [...row.receivers]
  ruleEdit.channels = [...row.channels]
  ruleEdit.actionHint = row.actionHint
  ruleBefore.value = JSON.parse(JSON.stringify({
    enabled: ruleEdit.enabled, severity: ruleEdit.severity, thresholdValue: ruleEdit.thresholdValue,
    thresholdUnit: ruleEdit.thresholdUnit, cooldownMinutes: ruleEdit.cooldownMinutes,
    receivers: ruleEdit.receivers, channels: ruleEdit.channels, actionHint: ruleEdit.actionHint
  }))
  ruleVisible.value = true
}

function submitRule() {
  quiet(async () => {
    // 只提交相对当前生效值变化的字段；空字符串与提示语按“未改”处理。
    const body = {}
    if (ruleEdit.enabled !== ruleBefore.value.enabled) body.enabled = ruleEdit.enabled
    if (ruleEdit.severity !== ruleBefore.value.severity) body.severity = ruleEdit.severity
    if (ruleEdit.cooldownMinutes !== ruleBefore.value.cooldownMinutes) body.cooldownMinutes = ruleEdit.cooldownMinutes
    const hint = (ruleEdit.actionHint || '').trim()
    if (hint && hint !== (ruleBefore.value.actionHint || '')) body.actionHint = hint
    if (JSON.stringify(ruleEdit.receivers) !== JSON.stringify(ruleBefore.value.receivers)) body.receivers = ruleEdit.receivers.join(',')
    const channels = ruleEdit.channels.includes('LEDGER') ? ruleEdit.channels : ['LEDGER', ...ruleEdit.channels]
    if (JSON.stringify(channels) !== JSON.stringify(ruleBefore.value.channels)) body.channels = channels.join(',')
    const hadThreshold = ruleBefore.value.thresholdValue !== null && ruleBefore.value.thresholdValue !== undefined
    if (!hadThreshold || ruleEdit.thresholdValue !== ruleBefore.value.thresholdValue
      || ruleEdit.thresholdUnit !== ruleBefore.value.thresholdUnit) {
      body.thresholdValue = ruleEdit.thresholdValue
      body.thresholdUnit = ruleEdit.thresholdUnit
    }
    if (!Object.keys(body).length) {
      ElMessage.info('没有字段被修改')
      return
    }
    await unwrap(saveRule(ruleEdit.code, body))
    ruleVisible.value = false
    ElMessage.success('规则覆盖已保存')
    await loadRules()
  })
}

function runScan() {
  api(async () => {
    scanReport.value = await unwrap(scan())
    scanVisible.value = true
    await refreshAll()
  })
}

function refreshAll() {
  const jobs = [loadItems(), loadSystem()]
  if (mainTab.value === 'alerts') jobs.push(loadAlerts())
  if (mainTab.value === 'rules' && canConfig) jobs.push(loadRules())
  return Promise.all(jobs)
}

function dismissHint() {
  hintDismissed.value = true
  localStorage.setItem(HINT_KEY, '1')
}

// 台账与规则首次切到该 tab 时才拉取，之后再切回不重复请求。
watch(mainTab, (name) => {
  if (name === 'alerts' && !alerts.value.length) loadAlerts()
  if (name === 'rules' && !rules.value.length) loadRules()
})

onMounted(() => {
  // 甘特角标等外部入口带筛选跳转进来：预填后加载。
  if (typeof route.query.orderCode === 'string' && route.query.orderCode) filters.orderCode = route.query.orderCode
  if (typeof route.query.materialCode === 'string' && route.query.materialCode) filters.materialCode = route.query.materialCode
  loadItems()
  loadSystem()
})
</script>

<style scoped>
.workbench { padding: 16px 20px; overflow: auto; height: 100%; box-sizing: border-box; }
header { display: flex; align-items: center; gap: 14px; margin-bottom: 10px; }
header h2 { margin: 0 auto 0 0; font-size: 18px; }
.header-actions { display: flex; gap: 8px; }
.lines-hint { margin-bottom: 10px; }
.summary-bar { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; margin-bottom: 10px; }
.line-chips { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.chip { cursor: pointer; }
.toolbar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 6px 0 10px; }
.toolbar-total { color: #909399; font-size: 12px; }
.error-bar { margin-bottom: 8px; }
.group-title { font-weight: 600; margin-right: 10px; }
.group-count { color: #909399; font-size: 12px; }
.item-table { cursor: pointer; }
.material-cell { display: flex; flex-direction: column; line-height: 1.3; }
.muted { color: #909399; font-size: 12px; }
.supply-eta { margin-left: 6px; }
.item-table ::v-deep(.el-table .cell) { word-break: break-word; }
.eta-hint { color: #E6A23C; font-style: normal; font-size: 12px; margin-left: 4px; }
.observed { color: #909399; font-size: 12px; cursor: help; }
.overdue { color: #F56C6C; }
.drawer-body { padding: 0 4px; }
.section { margin-bottom: 18px; }
.section-title { font-weight: 600; margin-bottom: 8px; }
.section-title.inline { margin: 0; }
.conclusion-head { display: flex; align-items: center; gap: 10px; }
.rule-name { font-weight: 600; }
.conclusion-title { margin: 8px 0; font-size: 14px; line-height: 1.5; }
.hint-bar { margin-bottom: 10px; }
.drawer-actions { display: flex; gap: 8px; }
.waterfall { display: flex; gap: 6px; flex-wrap: wrap; }
.waterfall-step { background: #F5F7FA; border-radius: 4px; padding: 8px 12px; min-width: 110px; }
.waterfall-step.final { background: #FDE2E2; }
.waterfall-step.final .value { font-weight: 700; color: #F56C6C; font-size: 15px; }
.waterfall-step .value { font-size: 14px; margin-top: 2px; }
.cutoff { margin-top: 6px; }
.table-caption { margin: 8px 0 4px; }
.supply-desc { margin-top: 10px; }
.tasks-table { margin-top: 8px; }
.inner-collapse { border: none; }
.attribution-note { margin-top: 8px; line-height: 1.5; }
.payload { background: #F5F7FA; border-radius: 4px; padding: 12px; max-height: 320px; overflow: auto; font-size: 12px; white-space: pre-wrap; word-break: break-all; margin: 0; }
.quality-notes { display: flex; flex-direction: column; gap: 4px; }
.quality-note { display: flex; gap: 12px; font-size: 13px; }
.quality-note .mono { color: #909399; }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; }
.form-note { margin-top: 6px; line-height: 1.4; }
.main-tabs ::v-deep(.el-tabs__content) { overflow: visible; }
</style>
