<template>
  <div :class="{'jvs-table': true, 'jvs-table-nocolumn': (!option.column || option.column.length == 0)}" :id="tableKey">
    <div :class="{'jvs-table-titleTop': true, 'jvs-table-top': !option.search == false, 'jvs-table-hideTop': option.hideTop}">
      <slot name="headerTop"></slot>
      <jvs-form v-if="!option.search == false && searchOption['column'] && searchOption['column'].length > 0" :formData="searchFormData" :defalutFormData="searchFormData" class="search-form" :option="searchOption" @submit="searchHandle" :isSearch="true" @reset="resetSearch">
      </jvs-form>
      <div class="table-top">
        <div class="table-top-left">
          <el-button type="primary" :size="commonStore.theme.btn.size || 'small'" v-if="!(option.addBtn==false)" @click="addForm" :icon="Plus">{{option.addBtnText || t('table.add')}}</el-button>
          <slot name="menuLeft"></slot>
        </div>
        <div class="table-top-right">
          <slot name="menuRight"></slot>
        </div>
      </div>
    </div>
    <h4 class="table-title">{{option.title}}</h4>
    <div class="table-body-top">
      <slot name="tableTop"></slot>
    </div>
    <div :class="'table-body-box '+tableKey">
      <!-- 卡片 -->
      <div v-if="displayType == 'card'" :class="{'jvs-table-body-slot': true, 'jvs-table-body-slot-loading': loading}">
        <div v-if="!displaySlot" class="table-body-slot-box">
          <div v-for="(row, index) in data" :key="row.id+'-'+index" class="table-body-slot-box-item">
            <div class="card-top-head">
              <!-- 标题 -->
              <div class="table-body-slot-box-item-row table-body-slot-box-item-row-title">
                <div v-if="titleItem" style="display: flex;align-items:center;">
                  <h4 class="title">{{titleItem.prepend ? titleItem.prepend : ''}}{{row[titleItem.prop+'_1'] ? fixedNumber(row[titleItem.prop+'_1'], titleItem) : (row[titleItem.prop] instanceof Array ? (row[titleItem.prop].length > 0 ? row[titleItem.prop].join(',') : '') : fixedNumber(row[titleItem.prop], titleItem))}}{{titleItem.append ? titleItem.append : ''}}{{titleItem.unit ? titleItem.unit: ''}}</h4>
                  <i v-if="titleItem.tools && titleItem.tools.indexOf('copy') > -1" class="el-icon-copy-document" style="margin-left: 5px;cursor: pointer;" @click="copyHandle(row[titleItem.prop+'_1'] ? row[titleItem.prop+'_1'] : (row[titleItem.prop] instanceof Array ? (row[titleItem.prop].length > 0 ? row[titleItem.prop].join(',') : '') : row[titleItem.prop]))"></i>
                </div>
                <img class="img" v-if="subTitleItem && subTitleItem.type == 'image' && (typeof row[subTitleItem.prop] == 'string' || (row[subTitleItem.prop] && row[subTitleItem.prop].length == 1))" :src="typeof row[subTitleItem.prop] == 'string' ? row[subTitleItem.prop] :  ( (row[subTitleItem.prop] && row[subTitleItem.prop].length > 0) ? row[subTitleItem.prop][0].url : '' )" />
                <el-popover
                  v-if="subTitleItem && subTitleItem.type == 'image' && (row[subTitleItem.prop] && row[subTitleItem.prop] instanceof Array && row[subTitleItem.prop].length > 1)"
                  placement="bottom"
                  trigger="click">
                  <div class="img-file-list">
                    <div v-for="(ifit, ifix) in row[subTitleItem.prop]" :key="subTitleItem.type+'-item-'+ifix" class="if-item">
                      <img v-if="subTitleItem.type == 'image'" :src="ifit.url" :alt="ifit.name">
                      <span>{{ifit.name}}</span>
                      <i class="el-icon-download" @click="$openUrl(ifit.url, '_blank')"></i>
                    </div>
                  </div>
                  <template #reference>
                    <el-button text>共{{row[subTitleItem.prop].length + (subTitleItem.type == 'image' ? '张图片' : '个文件')}}</el-button>
                  </template>
                </el-popover>
                <!-- 动态文本 -->
                <span class="span" v-if="subTitleItem && subTitleItem.type != 'image' && ( (subTitleItem.expressControl && subTitleItem.expressControl.length > 0) || (subTitleItem.conditionControl && subTitleItem.conditionControl.length > 0) )"
                  :style="'padding: 2px 10px;border-radius: 5px;'+ (styleRowItem(row, subTitleItem, 'color') ? ('color:' + styleRowItem(row, subTitleItem, 'color') + ';') : '')
                  + (styleRowItem(row, subTitleItem, 'bgcolor') ? ('background-color:' + styleRowItem(row, subTitleItem, 'bgcolor') + ';') : '') +';'">{{subTitleItem.prepend ? subTitleItem.prepend : ''}}{{styleRowItem(row, subTitleItem, 'text')}}{{subTitleItem.append ? subTitleItem.append : ''}}{{subTitleItem.unit ? subTitleItem.unit: ''}}</span>
                <!-- 一般文本 -->
                <span class="span" v-if="subTitleItem && subTitleItem.type != 'image' && (!subTitleItem.expressControl || subTitleItem.expressControl.length == 0) && (!subTitleItem.conditionControl || subTitleItem.conditionControl.length == 0)" :style="subTitleItem.color ? ('color:'+subTitleItem.color+';') : ''">{{subTitleItem.prepend ? subTitleItem.prepend : ''}}{{row[subTitleItem.prop+'_1'] ? fixedNumber(row[subTitleItem.prop+'_1'], subTitleItem) : fixedNumber(row[subTitleItem.prop], subTitleItem)}}{{subTitleItem.append ? subTitleItem.append : ''}}{{subTitleItem.unit ? subTitleItem.unit : ''}}</span>
              </div>
              <!-- 小标题 & 描述 -->
              <div class="table-body-slot-box-item-row-subhead-desc">
                <span v-if="subheadingItem">{{subheadingItem.prepend ? subheadingItem.prepend : ''}}{{row[subheadingItem.prop+'_1'] ? row[subheadingItem.prop+'_1'] : (row[subheadingItem.prop] instanceof Array ? (row[subheadingItem.prop].length > 0 ? row[subheadingItem.prop].join(',') : '') : row[subheadingItem.prop])}}{{subheadingItem.append ? subheadingItem.append : ''}}{{subheadingItem.unit ? subheadingItem.unit: ''}}</span>
                <span v-if="describeItem" class="divider-bar"></span>
                <span v-if="describeItem">{{describeItem.prepend ? describeItem.prepend : ''}}{{row[describeItem.prop+'_1'] ? row[describeItem.prop+'_1'] : row[describeItem.prop]}}{{describeItem.append ? describeItem.append : ''}}{{describeItem.unit ? describeItem.unit: ''}}</span>
              </div>
            </div>
            <!-- 其余项 -->
            <div class="table-body-slot-box-row-others">
              <div v-for="(item, cix) in getOtherCardItems(option.column)" :key="'card-item-'+index+'-'+cix" :class="{'table-body-slot-box-item-row': true}">  
                <div v-if="!item.cardPosition && showByIndex(item)" class="info">
                  <span class="label">{{item.label}}</span>
                  <span class="con">
                    <!-- 自定义 -->
                    <slot v-if="item.slot && !item.expand" :name="item.prop" :row="row" :index="index"></slot>
                    <!-- 动态控制 -->
                    <span v-if="!item.slot && !item.expand && ( (item.expressControl && item.expressControl.length > 0) || (item.conditionControl && item.conditionControl.length > 0) )"
                      :style="'border-radius: 5px;' + (styleRowItem(row, item, 'bgcolor') ? ('background:' + styleRowItem(row, item, 'bgcolor') + ';') : '') +';'">
                      <span :style="(styleRowItem(row, item, 'color') ? ('color:' + styleRowItem(row, item, 'color') + ';') : '')">{{item.prepend ? item.prepend : ''}}{{styleRowItem(row, item, 'text')}}{{item.append ? item.append : ''}}{{item.unit ? item.unit: ''}}</span>
                    </span>
                    <span v-if="!item.slot && !item.expand && (!item.expressControl || item.expressControl.length == 0) && (!item.conditionControl || item.conditionControl.length == 0)">
                      <!-- 一般列 -->
                      <span v-if="(!item.dicData || item.dicData.length == 0 || item.datatype == 'rule') && (['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1)" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{item.prepend ? item.prepend : ''}}{{row[item.prop+'_1'] ? fixedNumber(row[item.prop+'_1'], item) : (row[item.prop] instanceof Array ? (row[item.prop].length > 0 ? row[item.prop].join(',') : '') : fixedNumber(row[item.prop], item))}}{{item.append ? item.append : ''}}{{item.unit ? item.unit: ''}}</span>
                      <!-- 特殊颜色 -->
                      <span v-if="item.color && (!item.dicData || item.dicData.length == 0) && (['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1) && ( (item.expressControl && item.expressControl.length > 0) || (item.conditionControl && item.conditionControl.length > 0) )" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{item.prepend ? item.prepend : ''}}{{row[item.prop+'_1'] ? fixedNumber(row[item.prop+'_1'], item) : (row[item.prop] instanceof Array ? (row[item.prop].length > 0 ? row[item.prop].join(',') : '') : fixedNumber(row[item.prop], item))}}{{item.append ? item.append : ''}}{{item.unit ? item.unit: ''}}</span>
                      <!-- 日期时间 -->
                      <span v-if="item.type == 'datetime'" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{dateFormat(row[item.prop], item.format)}}</span>
                      <!-- 字典 -->
                      <span v-if="(['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1) && (item.datatype != 'rule' && item.dicData && item.dicData.length > 0)" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{dicFormat(row[item.prop], item.dicData, item.props)}}</span>
                      <!-- 链接 -->
                      <a :href="formatUrl(row[item.prop])" :target="item.openType || '_blank'" v-if="item.type == 'link'" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{item.text}}</a>
                      <!-- 图片 -->
                      <img v-if="item.type == 'image' && (typeof row[item.prop] == 'string' || (row[item.prop] && row[item.prop].length == 1))" :src="typeof row[item.prop] == 'string' ? row[item.prop] :  ( (row[item.prop] && row[item.prop].length > 0) ? row[item.prop][0].url : '' )" style="display:block;height: 95px;" />
                      <!-- 文件 -->
                      <a v-if="['file'].indexOf(item.type) > -1  && (typeof row[item.prop] == 'string' || (row[item.prop] && row[item.prop].length == 1))" :href="typeof row[item.prop] == 'string' ? row[item.prop] :  ( (row[item.prop] && row[item.prop].length > 0) ? row[item.prop][0].url : '')" :target="'_blank'">{{typeof row[item.prop] == 'string' ? '文件' :  ( (row[item.prop] && row[item.prop].length > 0) ? row[item.prop][0].name : '')}}</a>
                      <el-popover
                        v-if="['image', 'file'].indexOf(item.type) > -1 && (row[item.prop] && row[item.prop] instanceof Array && row[item.prop].length > 1)"
                        placement="bottom"
                        trigger="click">
                        <div class="img-file-list">
                          <div v-for="(ifit, ifix) in row[item.prop]" :key="item.type+'-item-'+ifix" class="if-item">
                            <img v-if="item.type == 'image'" :src="ifit.url" :alt="ifit.name">
                            <span>{{ifit.name}}</span>
                            <i class="el-icon-download" @click="$openUrl(ifit.url, '_blank')"></i>
                          </div>
                        </div>
                        <template #reference>
                          <el-button text>共{{row[item.prop].length + (item.type == 'image' ? '张图片' : '个文件')}}</el-button>
                        </template>
                      </el-popover>
                    </span>
                    <i v-if="item.tools && item.tools.indexOf('copy') > -1" class="el-icon-copy-document" style="margin-left: 5px;cursor: pointer;" @click="copyHandle(row[item.prop+'_1'] ? row[item.prop+'_1'] : (row[item.prop] instanceof Array ? (row[item.prop].length > 0 ? row[item.prop].join(',') : '') : row[item.prop]))"></i>
                  </span>
                </div>
              </div>
            </div>
            <!-- 操作栏 -->
            <div class="card-bottom">
              <slot name="menu" :row="row" :index="index"></slot>
            </div>
          </div>
        </div>
        <slot name="tableBody"></slot>
      </div>
      <!-- 表格 -->
      <el-table
        v-else
        header-row-class-name='headerclass'
        :stripe="false"
        :ref="el => dynamicRefMap(el, refs)"
        :data="data"
        :tooltip-effect="tooltipEffect"
        :show-header="showHeader"
        :border="option.border"
        :showSummary="option.showsummary"
        :summary-method="getSummaries"
        v-loading="loading"
        :size="size || 'small'"
        :highlight-current-row="option.highlightCurrentRow"
        empty-text=""
        :row-key="rowKey"
        default-expand-all
        :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
        @row-click="rowClick"
        @selection-change="handleSelectionChange"
        @sort-change="handleSort"
        :class="{'jvs-fixed-column-table': hasFixed, 'jvs-table-menu-column': option.menu!==false, 'jvs-title-column-table': (option.tableTitle && option.tableTitle.length > 0), 'jvs-table-no-menu': option.menu===false}"
        style="height: 100%;"
      >
        <el-table-column type="selection" width="55" v-if="selectable" class-name="table-select-column"></el-table-column>
        <el-table-column type="index" :width="option.indexWidth || 50" :label="option.indexLabel" v-if="index" class-name="table-index-column"> </el-table-column>
        <template v-if="option.tableTitle && option.tableTitle.length > 0">
          <el-table-column
            v-for="(item, inx) in outHide(titleColumn)"
            :key="item.prop ? ('table-title-'+inx) : item.prop"
            :label="item.label"
            :prop="item.prop ? item.prop : null"
            :show-overflow-tooltip="item.prop ? (item.type == 'image' ? false : tooltipShow(item, option)) : false"
            :header-align="item.prop ? (option.menuAlign) :'center'"
            :align="item.prop ? (item.align || option.align) :'center'"
            :width="item.prop ? item.width : ''"
            :sortable="item.prop ? (item.sort ? 'custom' : false) : false"
            :type="item.expand"
            :fixed="item.fixed"
            :class-name="`${item.prop ? 'table-notitle-column' : 'table-title-column'} ${(item.headerExplain || (item.tools && item.tools.indexOf('sort') > -1)) ? 'table-icon-column' : ''}`"
          >
            <!-- 表头文字说明 -->
            <template #header>
              <span v-if="editable && item.rules && item.rules.length > 0 && item.rules[0].required" style="color:#f56c6c;">*</span>
              <span>{{item.label}}</span>
              <el-tooltip v-if="item.headerExplain" effect="light" :content="item.explainContent" placement="top">
                <el-icon class="info-icon" :size="14"><InfoFilled /></el-icon>
              </el-tooltip>
              <span v-if="item.tools && item.tools.indexOf('sort') > -1" :class="{'sort-column-item-icon': true, 'desc': getSortStatus(item.prop, 'DESC'), 'asc': getSortStatus(item.prop, 'ASC')}" @click="sortIconClick(item)">
                <el-icon :size="14"><CaretTop /></el-icon>
                <el-icon :size="14"><CaretBottom /></el-icon>
              </span>
            </template>
            <template v-if="!item.column || item.column.length == 0" #default="scope">
              <span v-if="item.prop" :style="(typeof scope.row[item.prop] == 'string' ? (!scope.row[item.prop]) : ((scope.row[item.prop] instanceof Array && scope.row[item.prop].length < 1) || [undefined, null].indexOf(scope.row[item.prop]) > -1 || JSON.stringify(scope.row[item.prop]) == '{}')) ? 'width: 100%;' : ''">
                <!-- 自定义 -->
                <slot v-if="item.slot && !item.expand" :name="item.prop" :row="scope.row" :index="scope.$index"></slot>
                <!-- 动态控制 -->
                <span v-if="!item.slot && !item.expand && ( (item.expressControl && item.expressControl.length > 0) || (item.conditionControl && item.conditionControl.length > 0) )"
                  :style="'padding: 2px 10px;border-radius: 5px;' + (styleRowItem(scope.row, item, 'bgcolor') ? ('background:' + styleRowItem(scope.row, item, 'bgcolor') + ';') : '') +';'">
                  <span :style="(styleRowItem(scope.row, item, 'color') ? ('color:' + styleRowItem(scope.row, item, 'color') + ';') : '')">{{(scope.row[item.prop] && item.prepend) ? item.prepend : ''}}{{styleRowItem(scope.row, item, 'text')}}{{(scope.row[item.prop] && item.append) ? item.append : ''}}{{(scope.row[item.prop] && item.unit) ? item.unit : ''}}</span>
                </span>
                <span v-if="!item.slot && !item.expand && (!item.expressControl || item.expressControl.length == 0) && (!item.conditionControl || item.conditionControl.length == 0)">
                  <!-- 一般列 -->
                  <span v-if="(!item.dicData || item.dicData.length == 0 || item.datatype == 'rule') && (['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1)" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{(scope.row[item.prop] && item.prepend) ? item.prepend : ''}}{{scope.row[item.prop+'_1'] ? fixedNumber(scope.row[item.prop+'_1'], item) : (scope.row[item.prop] instanceof Array ? (scope.row[item.prop].length > 0 ? scope.row[item.prop].join(',') : '') : fixedNumber(scope.row[item.prop], item))}}{{(scope.row[item.prop] && item.append) ? item.append : ''}}{{(scope.row[item.prop] && item.unit) ? item.unit : ''}}</span>
                  <!-- 特殊颜色 -->
                  <span v-if="item.color && (!item.dicData || item.dicData.length == 0) && (['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1) && ( (item.expressControl && item.expressControl.length > 0) || (item.conditionControl && item.conditionControl.length > 0) )" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{(scope.row[item.prop] && item.prepend) ? item.prepend : ''}}{{scope.row[item.prop+'_1'] ? fixedNumber(scope.row[item.prop+'_1'], item) : (scope.row[item.prop] instanceof Array ? (scope.row[item.prop].length > 0 ? scope.row[item.prop].join(',') : '') : fixedNumber(row[item.prop], item))}}{{(scope.row[item.prop] && item.append) ? item.append : ''}}{{(scope.row[item.prop] && item.unit) ? item.unit : ''}}</span>
                  <!-- 日期时间 -->
                  <span v-if="item.type == 'datetime'" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{dateFormat(scope.row[item.prop], item.format)}}</span>
                  <!-- 字典 -->
                  <span v-if="(['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1) && (item.datatype != 'rule' && item.dicData && item.dicData.length > 0)" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{dicFormat(scope.row[item.prop], item.dicData, item.props)}}</span>
                  <!-- 链接 -->
                  <a :href="formatUrl(scope.row[item.prop])" :target="item.openType || '_blank'" v-if="item.type == 'link'" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{item.text}}</a>
                  <!-- 图片 -->
                  <img v-if="item.type == 'image' && (typeof scope.row[item.prop] == 'string' || (scope.row[item.prop] && scope.row[item.prop].length == 1))" :src="typeof scope.row[item.prop] == 'string' ? scope.row[item.prop] :  ( (scope.row[item.prop] && scope.row[item.prop].length > 0) ? scope.row[item.prop][0].url : '' )" :style="(item.imgWidth ? ('width:' + item.imgWidth + 'px;') : '') + (item.imgHeight ? ('height:' + item.imgHeight + 'px;') : '')" />
                  <!-- 文件 -->
                  <a class="file-text-a" v-if="['file'].indexOf(item.type) > -1  && (typeof scope.row[item.prop] == 'string' || (scope.row[item.prop] && scope.row[item.prop].length == 1))" @click="getPreviewUrl(scope.row, item, 0)" :target="'_blank'">{{typeof scope.row[item.prop] == 'string' ? '文件' :  ( (scope.row[item.prop] && scope.row[item.prop].length > 0) ? scope.row[item.prop][0].name : '')}}</a>
                  <i v-if="['file'].indexOf(item.type) > -1  && (typeof scope.row[item.prop] == 'string' || (scope.row[item.prop] && scope.row[item.prop].length == 1))" class="el-icon-download down-file-icon" @click="$openUrl(typeof scope.row[item.prop] == 'string' ? scope.row[item.prop] :  ( (scope.row[item.prop] && scope.row[item.prop].length > 0) ? scope.row[item.prop][0].url : ''), '_blank')"></i>
                  <el-popover
                    v-if="['image', 'file'].indexOf(item.type) > -1 && (scope.row[item.prop] && scope.row[item.prop] instanceof Array && scope.row[item.prop].length > 1)"
                    placement="bottom"
                    trigger="click">
                    <div class="img-file-list">
                      <div v-for="(ifit, ifix) in scope.row[item.prop]" :key="item.type+'-item-'+ifix" class="if-item">
                        <img v-if="item.type == 'image'" :src="ifit.url" :alt="ifit.name">
                        <span style="cursor: pointer;" @click="getPreviewUrl(scope.row, item, ifix)">{{ifit.name}}</span>
                        <i class="el-icon-download" @click="$openUrl(ifit.url, '_blank')"></i>
                      </div>
                    </div>
                    <template #reference>
                      <el-button text>共{{scope.row[item.prop].length + (item.type == 'image' ? '张图片' : '个文件')}}</el-button>
                    </template>
                  </el-popover>
                </span>
                <i v-if="item.tools && item.tools.indexOf('copy') > -1" class="el-icon-copy-document" style="margin-left: 5px;cursor: pointer;" @click="copyHandle(scope.row[item.prop+'_1'] ? scope.row[item.prop+'_1'] : (scope.row[item.prop] instanceof Array ? (scope.row[item.prop].length > 0 ? scope.row[item.prop].join(',') : '') : scope.row[item.prop]))"></i>
              </span>
            </template>
            <template v-if="item.column && item.column.length > 0">
              <el-table-column
                v-for="it in outHide(item.column)"
                :key="it.prop"
                :label="it.label"
                :prop="it.prop"
                :show-overflow-tooltip="it.type == 'image' ? false : tooltipShow(it, option)"
                :header-align="option.menuAlign"
                :align="it.align || option.align"
                :width="it.width"
                :sortable="it.sort ? 'custom' : false"
                :type="it.expand"
                :fixed="it.fixed"
              >
                <!-- 表头文字说明 -->
                <template #header>
                  <span v-if="editable && it.rules && it.rules.length > 0 && it.rules[0].required" style="color:#f56c6c;">*</span>
                  <span>{{it.label}}</span>
                  <el-tooltip v-if="it.headerExplain" effect="light" :content="it.explainContent" placement="top">
                    <el-icon class="info-icon" :size="14"><InfoFilled /></el-icon>
                  </el-tooltip>
                  <span v-if="it.tools && it.tools.indexOf('sort') > -1" :class="{'sort-column-item-icon': true, 'desc': getSortStatus(it.prop, 'DESC'), 'asc': getSortStatus(it.prop, 'ASC')}" @click="sortIconClick(it)">
                    <el-icon :size="14"><CaretTop /></el-icon>
                    <el-icon :size="14"><CaretBottom /></el-icon>
                  </span>
                </template>
                <template v-slot="scope">
                  <span :style="(typeof scope.row[it.prop] == 'string' ? (!scope.row[it.prop]) : ((scope.row[it.prop] instanceof Array && scope.row[it.prop].length < 1) || [undefined, null].indexOf(scope.row[it.prop]) > -1 || JSON.stringify(scope.row[it.prop]) == '{}')) ? 'width:100%;' : ''">
                    <!-- 自定义 -->
                    <slot v-if="it.slot && !it.expand" :name="it.prop" :row="scope.row" :index="scope.$index"></slot>
                    <!-- 动态控制 -->
                    <span v-if="!it.slot && !it.expand && ( (it.expressControl && it.expressControl.length > 0) || (it.conditionControl && it.conditionControl.length > 0) )"
                      :style="'padding: 2px 10px;border-radius: 5px;' + (styleRowItem(scope.row, it, 'bgcolor') ? ('background:' + styleRowItem(scope.row, it, 'bgcolor') + ';') : '') +';'">
                      <span :style="(styleRowItem(scope.row, it, 'color') ? ('color:' + styleRowItem(scope.row, it, 'color') + ';') : '')">{{(scope.row[it.prop] && it.prepend) ? it.prepend : ''}}{{styleRowItem(scope.row, it, 'text')}}{{(scope.row[it.prop] && it.append) ? it.append : ''}}{{(scope.row[it.prop] && it.unit) ? it.unit : ''}}</span>
                    </span>
                    <span v-if="!it.slot && !it.expand && (!it.expressControl || it.expressControl.length == 0) && (!it.conditionControl || it.conditionControl.length == 0)">
                      <!-- 一般列 -->
                      <span v-if="(!it.dicData || it.dicData.length == 0 || it.datatype == 'rule') && (['datetime', 'link', 'image', 'file'].indexOf(it.type) == -1)" :class="{'back-color-text': (it.color && !it.color.startsWith('#'))}" :style="(it.color && (it.color.startsWith('#') ? `color: ${it.color};` : `background:${it.color};`))">{{(scope.row[it.prop] && it.prepend) ? it.prepend : ''}}{{scope.row[it.prop+'_1'] ? fixedNumber(scope.row[it.prop+'_1'], it) : (scope.row[it.prop] instanceof Array ? (scope.row[it.prop].length > 0 ? scope.row[it.prop].join(',') : '') : fixedNumber(scope.row[it.prop], it))}}{{(scope.row[it.prop] && it.append) ? it.append : ''}}{{(scope.row[it.prop] && it.unit) ? it.unit : ''}}</span>
                      <!-- 特殊颜色 -->
                      <span v-if="it.color && (!it.dicData || it.dicData.length == 0) && (['datetime', 'link', 'image', 'file'].indexOf(it.type) == -1) && ( (it.expressControl && it.expressControl.length > 0) || (it.conditionControl && it.conditionControl.length > 0) )" :class="{'back-color-text': (it.color && !it.color.startsWith('#'))}" :style="(it.color && (it.color.startsWith('#') ? `color: ${it.color};` : `background:${it.color};`))">{{(scope.row[it.prop] && it.prepend) ? it.prepend : ''}}{{scope.row[it.prop+'_1'] ? fixedNumber(scope.row[it.prop+'_1'], it) : (scope.row[it.prop] instanceof Array ? (scope.row[it.prop].length > 0 ? scope.row[it.prop].join(',') : '') : fixedNumber(row[it.prop], it))}}{{(scope.row[it.prop] && it.append) ? it.append : ''}}{{(scope.row[it.prop] && it.unit) ? it.unit : ''}}</span>
                      <!-- 日期时间 -->
                      <span v-if="it.type == 'datetime'" :class="{'back-color-text': (it.color && !it.color.startsWith('#'))}" :style="(it.color && (it.color.startsWith('#') ? `color: ${it.color};` : `background:${it.color};`))">{{dateFormat(scope.row[it.prop], it.format)}}</span>
                      <!-- 字典 -->
                      <span v-if="(['datetime', 'link', 'image', 'file'].indexOf(it.type) == -1) && (it.datatype != 'rule' && it.dicData && it.dicData.length > 0)" :class="{'back-color-text': (it.color && !it.color.startsWith('#'))}" :style="(it.color && (it.color.startsWith('#') ? `color: ${it.color};` : `background:${it.color};`))">{{dicFormat(scope.row[it.prop], it.dicData, it.props)}}</span>
                      <!-- 链接 -->
                      <a :href="formatUrl(scope.row[it.prop])" :target="it.openType || '_blank'" v-if="it.type == 'link'" :class="{'back-color-text': (it.color && !it.color.startsWith('#'))}" :style="(it.color && (it.color.startsWith('#') ? `color: ${it.color};` : `background:${it.color};`))">{{it.text}}</a>
                      <!-- 图片 -->
                      <img v-if="it.type == 'image' && (typeof scope.row[it.prop] == 'string' || (scope.row[it.prop] && scope.row[it.prop].length == 1))" :src="typeof scope.row[it.prop] == 'string' ? scope.row[it.prop] :  ( (scope.row[it.prop] && scope.row[it.prop].length > 0) ? scope.row[it.prop][0].url : '' )" :style="(it.imgWidth ? ('width:' + it.imgWidth + 'px;') : '') + (it.imgHeight ? ('height:' + it.imgHeight + 'px;') : '')" />
                      <!-- 文件 -->
                      <a class="file-text-a" v-if="['file'].indexOf(it.type) > -1  && (typeof scope.row[it.prop] == 'string' || (scope.row[it.prop] && scope.row[it.prop].length == 1))" @click="getPreviewUrl(scope.row, it, 0)" :target="'_blank'">{{typeof scope.row[it.prop] == 'string' ? '文件' :  ( (scope.row[it.prop] && scope.row[it.prop].length > 0) ? scope.row[it.prop][0].name : '')}}</a>
                      <i v-if="['file'].indexOf(it.type) > -1  && (typeof scope.row[it.prop] == 'string' || (scope.row[it.prop] && scope.row[it.prop].length == 1))" class="el-icon-download down-file-icon" @click="$openUrl(typeof scope.row[it.prop] == 'string' ? scope.row[it.prop] :  ( (scope.row[it.prop] && scope.row[it.prop].length > 0) ? scope.row[it.prop][0].url : ''), '_blank')"></i>
                      <el-popover
                        v-if="['image', 'file'].indexOf(it.type) > -1 && (scope.row[it.prop] && scope.row[it.prop] instanceof Array && scope.row[it.prop].length > 1)"
                        placement="bottom"
                        trigger="click">
                        <div class="img-file-list">
                          <div v-for="(ifit, ifix) in scope.row[it.prop]" :key="it.type+'-item-'+ifix" class="if-item">
                            <img v-if="it.type == 'image'" :src="ifit.url" :alt="ifit.name">
                            <span style="cursor: pointer;" @click="getPreviewUrl(scope.row, it, ifix)">{{ifit.name}}</span>
                            <i class="el-icon-download" @click="$openUrl(ifit.url, '_blank')"></i>
                          </div>
                        </div>
                        <template #reference>
                          <el-button text>共{{scope.row[it.prop].length + (it.type == 'image' ? '张图片' : '个文件')}}</el-button>
                        </template>
                      </el-popover>
                    </span>
                    <i v-if="it.tools && it.tools.indexOf('copy') > -1" class="el-icon-copy-document" style="margin-left: 5px;cursor: pointer;" @click="copyHandle(scope.row[it.prop+'_1'] ? scope.row[it.prop+'_1'] : (scope.row[it.prop] instanceof Array ? (scope.row[it.prop].length > 0 ? scope.row[it.prop].join(',') : '') : scope.row[it.prop]))"></i>
                  </span>
                </template>
              </el-table-column>
            </template>
          </el-table-column>
        </template>
        <template v-else>
          <el-table-column
            v-for="item in outHide(option.column)"
            :key="item.prop"
            :label="item.label"
            :prop="item.prop"
            :show-overflow-tooltip="item.type == 'image' ? false : tooltipShow(item, option)"
            :header-align="option.menuAlign"
            :align="item.align || option.align"
            :width="item.width"
            :sortable="item.sort ? 'custom' : false"
            :type="item.expand"
            :fixed="item.fixed"
            :class-name="`${(item.headerExplain || (item.tools && item.tools.indexOf('sort') > -1)) ? 'table-icon-column' : ''}`"
          >
            <!-- 表头文字说明 -->
            <template #header>
              <span v-if="editable && item.rules && item.rules.length > 0 && item.rules[0].required" style="color:#f56c6c;">*</span>
              <span>{{item.label}}</span>
              <el-tooltip v-if="item.headerExplain" effect="light" :content="item.explainContent" placement="top">
                <el-icon class="info-icon" :size="14"><InfoFilled /></el-icon>
              </el-tooltip>
              <span v-if="item.tools && item.tools.indexOf('sort') > -1" :class="{'sort-column-item-icon': true, 'desc': getSortStatus(item.prop, 'DESC'), 'asc': getSortStatus(item.prop, 'ASC')}" @click="sortIconClick(item)">
                <el-icon :size="14"><CaretTop /></el-icon>
                <el-icon :size="14"><CaretBottom /></el-icon>
              </span>
            </template>
            <template v-slot="scope">
              <span :style="(typeof scope.row[item.prop] == 'string' ? (!scope.row[item.prop] || item.slot) : ((scope.row[item.prop] instanceof Array && scope.row[item.prop].length < 1) || [undefined, null].indexOf(scope.row[item.prop]) > -1 || JSON.stringify(scope.row[item.prop]) == '{}') || (item.slot && item.showOverflow===false)) ? 'width: 100%;' : ''">
                <!-- 自定义 -->
                <slot v-if="item.slot && !item.expand" :name="item.prop" :row="scope.row" :index="scope.$index"></slot>
                <!-- 动态控制 -->
                <span v-if="!item.slot && !item.expand && ( (item.expressControl && item.expressControl.length > 0) || (item.conditionControl && item.conditionControl.length > 0) )"
                  :style="'padding: 2px 10px;border-radius: 5px;' + (styleRowItem(scope.row, item, 'bgcolor') ? ('background:' + styleRowItem(scope.row, item, 'bgcolor') + ';') : '') +';'">
                  <span :style="(styleRowItem(scope.row, item, 'color') ? ('color:' + styleRowItem(scope.row, item, 'color') + ';') : '')">{{(scope.row[item.prop] && item.prepend) ? item.prepend : ''}}{{styleRowItem(scope.row, item, 'text')}}{{(scope.row[item.prop] && item.append) ? item.append : ''}}{{(scope.row[item.prop] && item.unit) ? item.unit : ''}}</span>
                </span>
                <span v-if="!item.slot && !item.expand && (!item.expressControl || item.expressControl.length == 0) && (!item.conditionControl || item.conditionControl.length == 0)">
                  <!-- 一般列 -->
                  <span v-if="(!item.dicData || item.dicData.length == 0 || item.datatype == 'rule') && (['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1)" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{(scope.row[item.prop] && item.prepend) ? item.prepend : ''}}{{scope.row[item.prop+'_1'] ? fixedNumber(scope.row[item.prop+'_1'], item) : (scope.row[item.prop] instanceof Array ? (scope.row[item.prop].length > 0 ? scope.row[item.prop].join(',') : '') : fixedNumber(scope.row[item.prop], item))}}{{(scope.row[item.prop] && item.append) ? item.append : ''}}{{(scope.row[item.prop] && item.unit) ? item.unit : ''}}</span>
                  <!-- 特殊颜色 -->
                  <span v-if="item.color && (!item.dicData || item.dicData.length == 0) && (['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1) && ( (item.expressControl && item.expressControl.length > 0) || (item.conditionControl && item.conditionControl.length > 0) )" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{(scope.row[item.prop] && item.prepend) ? item.prepend : ''}}{{scope.row[item.prop+'_1'] ? fixedNumber(scope.row[item.prop+'_1'], item) : (scope.row[item.prop] instanceof Array ? (scope.row[item.prop].length > 0 ? scope.row[item.prop].join(',') : '') : fixedNumber(row[item.prop], item))}}{{(scope.row[item.prop] && item.append) ? item.append : ''}}{{(scope.row[item.prop] && item.unit) ? item.unit : ''}}</span>
                  <!-- 日期时间 -->
                  <span v-if="item.type == 'datetime'" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{dateFormat(scope.row[item.prop], item.format)}}</span>
                  <!-- 字典 -->
                  <span v-if="(['datetime', 'link', 'image', 'file'].indexOf(item.type) == -1) && (item.datatype != 'rule' && item.dicData && item.dicData.length > 0)" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{dicFormat(scope.row[item.prop], item.dicData, item.props)}}</span>
                  <!-- 链接 -->
                  <a :href="formatUrl(scope.row[item.prop])" :target="item.openType || '_blank'" v-if="item.type == 'link'" :class="{'back-color-text': (item.color && !item.color.startsWith('#'))}" :style="(item.color && (item.color.startsWith('#') ? `color: ${item.color};` : `background:${item.color};`))">{{item.text}}</a>
                  <!-- 图片 -->
                  <img v-if="item.type == 'image' && (typeof scope.row[item.prop] == 'string' || (scope.row[item.prop] && scope.row[item.prop].length == 1))" :src="typeof scope.row[item.prop] == 'string' ? scope.row[item.prop] :  ( (scope.row[item.prop] && scope.row[item.prop].length > 0) ? scope.row[item.prop][0].url : '' )" :style="(item.imgWidth ? ('width:' + item.imgWidth + 'px;') : '') + (item.imgHeight ? ('height:' + item.imgHeight + 'px;') : '')" />
                  <!-- 文件 -->
                  <a class="file-text-a" v-if="['file'].indexOf(item.type) > -1  && (typeof scope.row[item.prop] == 'string' || (scope.row[item.prop] && scope.row[item.prop].length == 1))" @click="getPreviewUrl(scope.row, item, 0)" :target="'_blank'">{{typeof scope.row[item.prop] == 'string' ? '文件' :  ( (scope.row[item.prop] && scope.row[item.prop].length > 0) ? scope.row[item.prop][0].name : '')}}</a>
                  <i v-if="['file'].indexOf(item.type) > -1  && (typeof scope.row[item.prop] == 'string' || (scope.row[item.prop] && scope.row[item.prop].length == 1))" class="el-icon-download down-file-icon" @click="$openUrl(typeof scope.row[item.prop] == 'string' ? scope.row[item.prop] :  ( (scope.row[item.prop] && scope.row[item.prop].length > 0) ? scope.row[item.prop][0].url : ''), '_blank')"></i>
                  <el-popover
                    v-if="['image', 'file'].indexOf(item.type) > -1 && (scope.row[item.prop] && scope.row[item.prop] instanceof Array && scope.row[item.prop].length > 1)"
                    placement="bottom"
                    trigger="click">
                    <div class="img-file-list">
                      <div v-for="(ifit, ifix) in scope.row[item.prop]" :key="item.type+'-item-'+ifix" class="if-item">
                        <img v-if="item.type == 'image'" :src="ifit.url" :alt="ifit.name">
                        <span style="cursor: pointer;" @click="getPreviewUrl(scope.row, item, ifix)">{{ifit.name}}</span>
                        <i class="el-icon-download" @click="$openUrl(ifit.url, '_blank')"></i>
                      </div>
                    </div>
                    <template #reference>
                      <el-button text>共{{scope.row[item.prop].length + (item.type == 'image' ? '张图片' : '个文件')}}</el-button>
                    </template>
                  </el-popover>
                </span>
                <i v-if="item.tools && item.tools.indexOf('copy') > -1" class="el-icon-copy-document" style="margin-left: 5px;cursor: pointer;" @click="copyHandle(scope.row[item.prop+'_1'] ? scope.row[item.prop+'_1'] : (scope.row[item.prop] instanceof Array ? (scope.row[item.prop].length > 0 ? scope.row[item.prop].join(',') : '') : scope.row[item.prop]))"></i>
              </span>
            </template>
          </el-table-column>
        </template>
        <el-table-column :fixed="option.menuFix" :label="(option.menuText === '') ? '' : t('table.oprate')" :width="option.menuWidth" v-if="option.menu!==false" :align="option.menuAlign">
          <template v-slot="scope">
            <div>
              <el-button text :size="commonStore.theme.btn.size || 'small'" v-if="!(option.viewBtn==false)" @click="viewHandle(scope.row, scope.$index)">{{option.viewBtnText || t('table.view')}}</el-button>
              <el-button text :size="commonStore.theme.btn.size || 'small'" v-if="!(option.editBtn==false)" @click="editHandle(scope.row, scope.$index)">{{option.editBtnText || t('table.edit')}}</el-button>
              <!-- 操作栏自定义 -->
              <slot name="menu" :row="scope.row" :index="scope.$index"></slot>
              <el-popconfirm v-if="!(option.delBtn==false)" :title="t('common.deleteConfirm')" width="300px" :icon="WarningFilled" @confirm="delHandle(scope.row,scope.$index)">
                <template #reference>
                  <el-button text :size="commonStore.theme.btn.size || 'small'"><span style="color: #F56C6C;">{{option.delBtnText || t('table.delete')}}</span></el-button>
                </template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="tablepagination" v-if="option.page">
      <el-pagination
        background
        :layout="page.layout || pagination.layout"
        :total="page.total || pagination.total"
        :current-page="page.currentPage || pagination.currentPage"
        :page-sizes="page.pageSizes || pagination.pageSizes"
        :page-size="page.pageSize || pagination.pageSize"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      ></el-pagination>
      <slot name="menuLeftBottom"></slot>
    </div>

    <!-- 空 -->
    <div v-if="(!option.column || option.column.length == 0)" class="empty-view">
      <img src="/jvs-ui-public/img/contentEmpty.png" alt=""/>
      <div>{{t(`common.rightDrawer.emptyCon`)}}</div>
    </div>

    <el-dialog
      :title="title"
      v-model="dialogVisible"
      :close-on-click-modal='option.dialogClickModal'
      :close-on-press-escape='option.dialogEscape'
      append-to-body
      :width="option.dialogWidth || '75%'"
      :fullscreen="option.dialogWidth == '100%' ? true : false"
      :class="{'form-fullscreen-dialog': option.dialogWidth == '100%'}"
      :before-close="handleClose">
      <jvs-form v-if="dialogVisible" :formData="rowData" :defalutFormData="rowData" :option="formOpton" @submit="submitHandle" :isSearch="false" @cancalClick="handleClose">
      </jvs-form>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup name="jvs-table">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick
} from 'vue'
import { useRouter, useRoute } from "vue-router"
import { useI18n } from 'vue-i18n'
import { Base64 } from 'js-base64'
import { ElNotification } from 'element-plus'
import { Plus, WarningFilled, InfoFilled, CaretTop, CaretBottom } from '@element-plus/icons-vue'

import useCommonStore from '@/store/common.js'
import { getStore } from '@/util/store.js'
import { guid } from '@/util/util'

import { getSelectData } from '@/components/api'

const { $openUrl }  = getCurrentInstance().appContext.config.globalProperties
const emit = defineEmits(['on-load', 'sort-change', 'row-click', 'selection-change', 'search-change', 'search-reset', 'size-change', 'current-change', 'addRow', 'editRow', 'delRow', 'sort'])
const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const commonStore = useCommonStore()
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  dynamicRefs.value[ref] = el
}

const props = defineProps({
  // 绑定表格 refs
  refs: {
    type: String,
    default: 'multipleTable'
  },
  // 是否显示表格头
  showHeader: {
    type: Boolean,
    default: true
  },
  // tip提示背景
  tooltipEffect: {
    type: String,
    default: 'light', // 'dark'
  },
  // 是否可以多选
  selectable: {
    type: Boolean,
    default: false
  },
  // 是否提示 等待加载loading
  loading: {
    type: Boolean,
    default: false
  },
  size: {
    type: String,
    default: ''
  },
  // 是否显示顺序
  index: {
    type: Boolean,
    default: false
  },
  // 分页配置
  page: {
    type: Object,
    default: (data) => {
      return {
        total: 0, // 总页数
        currentPage: 1, // 当前页数
        pageSize: 20, // 每页显示多少条
        pageSizes: [20, 50, 100, 200, 500, 1000], // 分页大小
        layout: "total, sizes, prev, pager, next, jumper", // 分页工具
      }
    }
  },
  // 表格数据
  data: {
    type: Array,
    default: () => {
      return []
    }
  },
  // 搜索表单
  formData: {
    type: Object,
    default: () => {
      return {}
    }
  },
  // 表格配置
  option: {
    type: Object,
    default: () => {
      return {
        border: false, // 表格是否边框
        page: true, // 是否分页
        align: 'left', // body对齐
        menuAlign: 'left', // 表头对齐
        menuFix: 'right', // 操作栏固定位置
        menuWidth: 200, // 操作栏宽度
        search: false, // 是否开启查询
        showOverflow: true, // 超出是否合并移入悬浮tip显示
        menu: true,
        indexLabel:'序号',
        // 搜索表单设置
        formAlign: 'right', //对其方式
        inline: false, // 表单项是否可以同行,当垂直方向空间受限且表单较简单时，可以在一行内放置表单
        labelWidth: 'auto', // label宽
        searchBtn:true,//搜索的查询按钮
        searchBtnText: "查询", // 提交按钮文字，默认 提交
        submitBtn: true, // 提交按钮是否显示，默认显示
        submitBtnText: '保存', // 提交按钮文字，默认 提交
        emptyBtn: true, // 重置按钮，默认显示
        emptyBtnText: '清空', // 重置按钮文字，默认 重置
        addDialogText:'新增',//新增弹窗title,默认 新增
        editDialogText: '编辑', //编辑弹框title，默认 编辑
        submitLoading: false, // 默认表单提交按钮loading，初始值默认false
        searchLoading: false, // 查询表单提交按钮loading，初始值默认false
        column: [
          {
            label: '', // 文字
            prop: '', // 字段
            search: false, // 是否搜索
            slot: false, // 是否自定义
            hide: true, // 当前列在表格是否隐藏
            color: '', // 颜色特殊显示
            align: '', // 默认与table保持一致，可自定义 left right center
            menuAlign: '', // 默认与table保持一致，可自定义 left right center
            type: '', // 文本类型，默认input
            dicData: [], // 字典数据
            showOverflow: true, // 超出是否合并移入悬浮tip显示

            // 搜索表单对应
            span: 24, // 表单项栅格比，默认24
            formSlot: false, // 表单项是否自定义

            // 新增、编辑、查看对应表单设置
            addDisabled: false, // 表单新增时是否禁止
            addDisplay: true, // 表单新增时是否可见
            editDisabled: false, // 表单编辑时是否禁止
            editDisplay: false, // 表单编辑是否可见
            viewDisplay: true, // 表单查看是否可见
          }
        ]
      }
    }
  },
  // 是否清空多选，随机数
  isClearSelect: {
    type: Number
  },
  // 已选数据
  selectedRows: {
    type: Array
  },
  // 是否默认全选
  defaultAllSelect: {
    type: Boolean,
    default: false
  },
  editable: {
    type: Boolean
  },
  jvsAppId: {
    type: String
  },
  sortsList: {
    type: Array
  },
  displayType: {
    type: String
  },
  displaySlot: {
    type: Boolean,
    default: false
  },
  rowKey: {
    type: String,
    default: () => {
      return 'id'
    }
  },
  fromDialog: {
    type: Boolean
  },
  dialogHeight: {
    type: Number
  },
  fromOtherOpen: {
    type: Boolean
  }
})
const searchFormData = computed({
  get () {
    return props.formData
  },
  set (newVal) {
    tempForm.value = newVal
  }
})

const tableKey = ref('table' + guid())
let searchForm = reactive({})
const tempForm = ref({})
const title = ref('') // 弹框标题
const dialogVisible = ref(false)
const rowData = ref({}) // 行数据
const formOpton = ref({})
const optype = ref('addRow') // 提交方式
const pagination = ref({
  total: 0, // 总页数
  currentPage: 1, // 当前页数
  pageSize: 20, // 每页显示多少条
  pageSizes: [20, 50, 100, 200, 500, 1000], // 分页大小
  layout: "total, sizes, prev, pager, next, jumper", // 分页工具
})
let searchOption = reactive({})// 搜索表单配置
const daActionIndex = ref(0)
const sortList = ref([])
const titleItem = ref(null)
const subTitleItem = ref(null)
const subheadingItem = ref(null)
const describeItem = ref(null)
const cardOtherItems = ref([])
const hasFixed = ref(false)
const titleColumn = ref([])

createHandle()
onMounted(() => {
  // 多选回显
  if(props.data && props.data.length > 0) {
    showSelected()
  }
})

function createHandle () {
  let searchObj = {}
  for(let k in props.option) {
    searchOption[k] = props.option[k]
  }
  searchOption['labelWidth'] = 'auto'
  let temp = []
  for(let i in searchOption['column']) {
    if(props.option.column[i].dicUrl) {
      getSelectData(props.option.column[i].dicUrl).then(res=>{
        props.option.column[i].dicData = res.data.data
      })
    }
    if(searchOption['column'][i] && searchOption['column'][i].search == true) {
      if(searchOption['column'][i].formSlot == true) {
        searchOption['column'][i].formSlot = false
      }
      searchObj[searchOption['column'][i].prop] = null
      // 弹窗表格的栅格
      if(searchOption['column'][i] && !searchOption['column'][i].span) {
        searchOption['column'][i].span = searchOption['span'] || 6
      }
      // 搜索表格的栅格
      if (searchOption['column'][i] && !searchOption['column'][i].searchSpan && searchOption['column'][i].search) {
        searchOption['column'][i].searchSpan = searchOption['searchSpan'] || 6
      }
      // 去除提示tips
      if(searchOption['column'][i] && searchOption['column'][i].tips) {
        searchOption['column'][i].tips = null
      }
      temp.push(JSON.parse(JSON.stringify(searchOption['column'][i])))
    }
    if(searchOption['column'][i].fixed) {
      hasFixed.value = true
    }
  }
  if(props.option.menuFix == 'right') {
    hasFixed.value = true
  }
  // 去除搜索条件的校验
  for(let j in temp) {
    if(temp[j].rules && temp[j].rules.length > 0) {
      for(let k in temp[j].rules) {
        if(temp[j].rules[k].required && temp[j].rules[k].required === true) {
          temp[j].rules[k].required = false
        }
      }
    }
  }
  searchOption['column'] = temp
  searchOption['isSearch'] = true
  if(JSON.stringify(searchFormData.value) == '{}') {
    searchFormData.value = JSON.parse(JSON.stringify(searchObj))
  }
  // 卡片展示
  if(props.displayType == 'card') {
    setCardStyle()
  }
  if(props.option.tableTitle && props.option.tableTitle.length > 0) {
    let tempColumn = []
    for(let c in props.option.column) {
      let isIn = false
      for(let t in props.option.tableTitle) {
        if(props.option.tableTitle[t].fieldKey && props.option.tableTitle[t].fieldKey.length > 0) {
          if(props.option.tableTitle[t].fieldKey.indexOf(props.option.column[c].prop) > -1) {
            let needAdd = true
            tempColumn.filter(tit => {
              if(tit.label == props.option.tableTitle[t].tableTitle) {
                needAdd = false
                tit.column.push(props.option.column[c])
              }
            })
            if(needAdd) {
              tempColumn.push({
                label: props.option.tableTitle[t].tableTitle,
                column: [props.option.column[c]]
              })
            }
            isIn = true
          }
        }
      }
      if(!isIn) {
        tempColumn.push(props.option.column[c])
      }
    }
    titleColumn.value = tempColumn
  }
  emit('on-load', props.page)
}

// 隐藏项
function outHide (arr) {
  return arr.filter(item => !(item.hide))
}

// 卡片其余项
function getOtherCardItems (arr) {
  return arr.filter(item => (!item.cardPosition && showByIndex(item)))
}

function dicFormat (val, options, props) {
  let temp = ''
  let tpArr = []
  temp = val
  // 树形字典
  if(props && props.children) {
    recursionTree(options, val, props, tpArr, props.label)
    temp = tpArr.join('/')
  }else{
    // 普通字典
    for(let i in options) {
      let dicitem = options[i][(props && props.value) || 'value']
      if(['number', 'boolean'].indexOf(typeof val) > -1 && ['number', 'boolean'].indexOf(typeof dicitem) > -1) {
        dicitem = eval(dicitem)
      }
      if(typeof val == 'object' && val instanceof Array) {
        if(val.indexOf(dicitem) > -1) {
          tpArr.push(options[i][(props && props.label) || 'label'])
        }
      }else{
        if(dicitem == val) {
          temp = options[i][(props && props.label) || 'label']
        }
      }
    }
    if(typeof val == 'object' && val instanceof Array) {
      temp = tpArr.join(',')
    }
  }
  return temp
}

function recursionTree (list, val, props, temp, attr) {
  for(let i in list) {
    if(typeof val == 'object' && val instanceof Array) {
      if(val.indexOf(list[i][props.value]) > -1) {
        temp.push(list[i][attr])
      }
    }else{
      if(list[i][props.value] == val) {
        temp.push(list[i][attr])
      }
    }
    if(list[i][props.children] && list[i][props.children].length > 0) {
      recursionTree(list[i][props.children], val, props, temp, attr)
    }
  }
}

function dateFormat (date, type) {
  let format = 'yyyy-MM-dd hh:mm:ss'
  if(type) {
    format = type
  }
  if(typeof date == 'string') {
    return date
  }
  if (date != 'Invalid Date') {
    var o = {
      'M+': date.getMonth() + 1, // month
      'd+': date.getDate(), // day
      'h+': date.getHours(), // hour
      'm+': date.getMinutes(), // minute
      's+': date.getSeconds(), // second
      'q+': Math.floor((date.getMonth() + 3) / 3), // quarter
      'S': date.getMilliseconds() // millisecond
    }
    if (/(y+)/.test(format)) {
      format = format.replace(RegExp.$1, (date.getFullYear() + '').substr(4 - RegExp.$1.length))
    }
    for (var k in o) {
      if (new RegExp('(' + k + ')').test(format)) {
        format = format.replace(RegExp.$1, RegExp.$1.length == 1 ? o[k] : ('00' + o[k]).substr(('' + o[k]).length))
      }
    }
    return format
  }
  return ''
}

// 排序
function handleSort (row) {
  emit('sort-change', row)
}

// 行点击
function rowClick (row, column, cell, event) {
  emit('row-click', { row, column, cell, event })
}

// 多选
function handleSelectionChange (selection) {
  emit('selection-change', selection)
}

// 搜索
function searchHandle (form) {
  if(props.option.page) {
    form['current'] = 1
  }
  searchFormData.value = form
  emit('search-change', form)
}

// 重置
function resetSearch () {
  emit('search-reset')
}

// 清空
function emptyHandle () {
  searchForm = {}
}

// 分页大小变化
function handleSizeChange (val) {
  props.page.pageSize = val
  emit('on-load', props.page)
  emit('size-change', props.page)
}

// 当前页改变
function handleCurrentChange (val) {
  props.page.currentPage = val
  if(props.option.page){
    let obj = tempForm.value
    obj['current'] = val
    emit('search-change', obj)
  }else{
    emit('on-load', props.page)
  }
  emit('current-change', props.page)
}

// 判断是否需要超出文字提示
function tooltipShow (item, option) {
  let temp = true
  temp = item.showOverflow ? true : (item.showOverflow === false ? false : (option.showOverflow === false ? false : true))
  return temp
}

// 关闭弹框
function handleClose () {
  formOpton.value['column'].forEach((item, index) => {
    if(['imageUpload','fileUpload'].indexOf(item.type) != -1) {
      item.fileList = []
    }
  })
  rowData.value = {}
  dialogVisible.value = false
}

// 表单提交
function submitHandle (form) {
  if(optype.value == 'addRow') {
    emit('addRow', form)
  }
  if(optype.value == 'editRow') {
    emit('editRow', form, daActionIndex.value)
  }
  handleClose()
}

// 新增
function addForm () {
  formOpton.value = props.option
  formOpton.value['submitBtnText'] = formOpton.value['submitBtnText'] || t('form.submit')
  title.value = formOpton.value['addDialogText'] || t('table.add')
  optype.value = 'addRow'
  formOpton.value['disabled'] = false
  formOpton.value['submitBtn'] = true
  let temp = []
  for(let i in formOpton.value['column']) {
    if(formOpton.value['column'][i].addDisabled == true) {
      formOpton.value['column'][i].disabled = true
    }else{
      formOpton.value['column'][i].disabled = false
    }
    if(formOpton.value['column'][i].addDisplay != false) {
      temp.push(formOpton.value['column'][i])
      formOpton.value['column'][i].display = true
    }else{
      formOpton.value['column'][i].display = false
    }
  }
  dialogVisible.value = true
}

// 查看
function viewHandle (row, index) {
  daActionIndex.value = index
  formOpton.value = JSON.parse(JSON.stringify(props.option))
  formOpton.value['submitBtnText'] = t('form.submit')
  title.value = t('table.view')
  optype.value = 'viewRow'
  formOpton.value['disabled'] = true
  formOpton.value['submitBtn'] = false
  formOpton.value['emptyBtn'] = false
  rowData.value = row
  let temp = []
  for(let i in formOpton.value['column']) {
    if(formOpton.value['column'][i].viewDisplay != false) {
      temp.push(formOpton.value['column'][i])
      formOpton.value['column'][i].display = true
    }else{
      formOpton.value['column'][i].display = false
    }
  }
  formOpton.value['column'] = temp
  dialogVisible.value = true
}

function editHandle (row, index) {
  daActionIndex.value = index
  formOpton.value = props.option // JSON.parse(JSON.stringify(props.option))
  formOpton.value['submitBtnText'] = formOpton.value['submitBtnText'] || t('form.submit')
  title.value = formOpton.value['editDialogText'] || t('table.edit')
  optype.value = 'editRow'
  formOpton.value['disabled'] = false
  formOpton.value['submitBtn'] = true
  rowData.value = JSON.parse(JSON.stringify(row))
  let temp = []
  for(let i in formOpton.value['column']) {
    if(formOpton.value['column'][i].editDisabled == true) {
      formOpton.value['column'][i].disabled = true
    }
    if(formOpton.value['column'][i].editDisplay != false) {
      temp.push(formOpton.value['column'][i])
      formOpton.value['column'][i].display = true
    }else{
      formOpton.value['column'][i].display = false
    }
  }
  dialogVisible.value = true
}

function delHandle (row, index) {
  optype.value = 'delRow'
  emit('delRow', row, index)
}

// 多选回显
function showSelected () {
  if(props.selectable == true) {
    if(props.selectedRows && props.selectedRows.length > 0 && dynamicRefs.value[props.refs]) {
      props.selectedRows.forEach(row => {
        for(let i in props.data) {
          let keys = Object.keys(props.data[i])
          let k2 = 'id'
          if(keys.indexOf(k2) > -1) {
          }else{
            k2 = 'aliasColumnName'
          }
          if(props.data[i][k2] == row[k2]) {
            dynamicRefs.value[props.refs].toggleRowSelection(props.data[i])
          }
        }
      });
    }else {
      if(dynamicRefs.value[props.refs]) {
        if(!props.fromDialog) {
          dynamicRefs.value[props.refs].clearSelection()
        }
        // 默认全选上
        if(props.defaultAllSelect == true) {
          props.data.forEach(row => {
            dynamicRefs.value[props.refs].toggleRowSelection(row)
          })
        }
      }
    }
  }
}

// 清空多选
function clearSelect () {
  dynamicRefs.value[props.refs].clearSelection()
}

function doLayout () {
  nextTick(() => {
    dynamicRefs.value[props.refs].doLayout()
  })
}

// 动态控制行数据显示
function styleRowItem (row, item, type) {
  let val = row[item.prop]
  let color = ""
  let bgcolor = ''
  if(item.expressControl && item.expressControl.length > 0) {
    for(let i in item.expressControl) {
      if(item.expressControl[i].express) {
        let str = item.expressControl[i].express.replace(/\$\{/g,"row.")
        str = str.replace(/}/g, "")
        if(eval(str)){
          if(item.expressControl[i].text.includes('${')) {
            let ts = item.expressControl[i].text
            ts = ts.replace(/\$\{/g,"row.")
            ts = ts.replace(/}/g, "")
            if(eval(ts)) {
              val = eval(ts)
            }
          }else{
            val = item.expressControl[i].text
          }
          color = item.expressControl[i].color
        }
      }
    }
  }
  if(item.conditionControl && item.conditionControl.length > 0) {
    let hadFix = false
    for(let i in item.conditionControl) {
      if(item.conditionControl[i].value) {
        let arr = item.conditionControl[i].value.split(',')
        if(arr.indexOf(row[item.conditionControl[i].key || item.prop]) > -1 || arr.indexOf(row[item.conditionControl[i].key || item.prop]+'') > -1) {
          hadFix = true
          if(item.conditionControl[i].text) {
            val = item.conditionControl[i].text
          }else{
            val = row[item.prop+'_1'] ? row[item.prop+'_1'] : (row[item.prop] instanceof Array ? (row[item.prop].length > 0 ? row[item.prop].join(',') : '') : row[item.prop])
          }
          if(item.conditionControl[i].color) {
            color = item.conditionControl[i].color
          }
          if(item.conditionControl[i].bgcolor) {
            bgcolor = item.conditionControl[i].bgcolor
          }
          break;
        }
      }
    }
    if(!hadFix) {
      if(item.color) {
        color = item.color
      }
      if(item.backColor) {
        bgcolor = item.backColor
      }
    }
  }
  if(type == 'color') {
    return color ? (color.startsWith('#') ? color : `;-webkit-text-fill-color: transparent;background:${color};background-clip: text!important;-webkit-background-clip: text!important;`) : ``
  }else if(type == 'bgcolor') {
    return bgcolor
  }else{
    return fixedNumber(val, item)
  }
}

function formatUrl (url) {
  if(props.jvsAppId && url) {
    if(url.includes('?')) {
      if(!url.includes('jvsAppId')) {
        url += `&jvsAppId=${props.jvsAppId}`
      }
    }else{
      url += `?jvsAppId=${props.jvsAppId}`
    }
  }
  return url
}

function sortIconClick (item) {
  let index = -1
  sortList.value.filter((sit, six) => {
    if(sit.fieldKey == item.prop) {
      index = six
    }
  })
  if(index > -1) {
    let str = ''
    if(!sortList.value[index].direction) {
      str = 'DESC'
    }
    if(sortList.value[index].direction == 'DESC') {
      str = 'ASC'
    }
    if(sortList.value[index].direction == 'ASC') {
      str = ''
    }
    sortList.value[index].direction = str
  }else{
    sortList.value.push({
      fieldKey: item.prop,
      direction: 'DESC'
    })
  }
  emit('sort', sortList.value)
}

function getSortStatus (prop, status) {
  let bool = false
  sortList.value.filter(sit => {
    if(sit.fieldKey == prop) {
      if(sit.direction == status) {
        bool = true
      }
    }
  })
  return bool
}

// 复制
function copyHandle (value) {
  const text = document.createElement('input')
  text.value = value
  document.body.appendChild(text)
  text.select()
  document.execCommand('Copy')
  document.body.removeChild(text)
  ElNotification({
    title: t('common.tip'),
    message: t('common.copySuccess'),
    position: 'bottom-right',
    type: 'success'
  })
}

// 设置卡片样式
function setCardStyle () {
  let cardTitleProp = ''
  let cardSubtitleProp = ''
  let cardSubheadingProp = ''
  let cardDescribeProp = ''
  props.option.column.filter(cit => {
    if(cit.cardPosition) {
      switch(cit.cardPosition) {
        case 'title': cardTitleProp = cit.prop;break;
        case 'subtitle': cardSubtitleProp = cit.prop; subTitleItem.value = JSON.parse(JSON.stringify(cit));break;
        case 'subheading': cardSubheadingProp = cit.prop;break;
        case 'describe': cardDescribeProp = cit.prop; describeItem.value = JSON.parse(JSON.stringify(cit));break;
        default: ;break;
      }
    }
  })
  if(!cardTitleProp) {
    for(let i in props.option.column) {
      if(!props.option.column[i].type || (['image', 'imageUpload', 'file', 'fileUpload'].indexOf(props.option.column[i].type) == -1 && !props.option.column[i].multiple)) {
        props.option.column[i].cardPosition = 'title'
        cardTitleProp = props.option.column[i].prop
        break;
      }
    }
  }
  if(!cardSubtitleProp) {
    for(let i in props.option.column) {
      if([cardTitleProp].indexOf(props.option.column[i].prop) == -1 && (['image', 'imageUpload', 'radio'].indexOf(props.option.column[i].type) > -1 || (props.option.column[i].type == 'select' && !props.option.column[i].multiple))) {
        props.option.column[i].cardPosition = 'subtitle'
        cardSubtitleProp = props.option.column[i].prop
        subTitleItem.value = JSON.parse(JSON.stringify(props.option.column[i]))
        break;
      }
    }
  }
  if(!cardSubheadingProp) {
    if([cardTitleProp, cardSubtitleProp].indexOf('createBy') == -1) {
      cardSubheadingProp = 'createBy'
      let has = false
      props.option.column.filter(cit => {
        if(cit.prop == 'createBy') {
          cit.cardPosition = 'subheading'
          has = true
        }
      })
      if(!has) {
        props.option.column.push({
          label: '创建人',
          prop: 'createBy',
          cardPosition: 'subheading'
        })
      }
    }else{
      for(let i in props.option.column) {
        if([cardTitleProp, cardSubtitleProp].indexOf(props.option.column[i].prop) == -1) {
          props.option.column[i].cardPosition = 'subheading'
          cardSubheadingProp = props.option.column[i].prop
          break;
        }
      }
    }
  }
  if(!cardDescribeProp) {
    if([cardTitleProp, cardSubtitleProp, cardSubheadingProp].indexOf('createTime') == -1) {
      cardDescribeProp = 'createTime'
      let has = false
      props.option.column.filter(cit => {
        if(cit.prop == 'createTime') {
          cit.cardPosition = 'describe'
          describeItem.value = JSON.parse(JSON.stringify(cit))
          has = true
        }
      })
      if(!has) {
        describeItem.value = {
          label: '创建时间',
          prop: 'createTime',
          cardPosition: 'describe'
        }
      }
    }else{
      for(let i in props.option.column) {
        if([cardTitleProp, cardSubtitleProp, cardSubheadingProp].indexOf(props.option.column[i].prop) == -1) {
          props.option.column[i].cardPosition = 'describe'
          cardDescribeProp = props.option.column[i].prop
          describeItem.value = JSON.parse(JSON.stringify(props.option.column[i]))
          break;
        }
      }
    }
  }
  cardOtherItems.value = []
  let hasImg = false
  props.option.column.filter((cit, cix) => {
    if(!cit.cardPosition && !hasImg && cit.hide !== true) {
      cardOtherItems.value.push(cit.prop)
    }
    if(!cit.cardPosition && ['image', 'imageUpload'].indexOf(cit.type) > -1) {
      hasImg = true
    }
    if(cit.cardPosition == 'title') {
      titleItem.value = JSON.parse(JSON.stringify(cit))
    }
    if(cit.cardPosition == 'subheading') {
      subheadingItem.value = JSON.parse(JSON.stringify(cit))
    }
  })
  // console.log(cardOtherItems.value)
  // console.log(props.option.column)
}

function showByIndex (item) {
  let bool = false
  let index = cardOtherItems.value.indexOf(item.prop)
  if(index > -1 && index < 3) {
    bool = true
  }
  return bool
}

function getPreviewUrl (row, item, index) {
  if(row.jvsEnabledButtons) {
    let hasRight = false
    row.jvsEnabledButtons.filter(bp => {
      if(bp.includes('btn_modify')) {
        hasRight = true
      }
    })
    if(hasRight) {
      previewFile(row[item.prop][index])
    }
  }
}

function previewFile (row) {
  let protocolhost = commonStore.kkfileUrl || ''
  if(protocolhost && row.url) {
    let view_url = `${protocolhost}/onlinePreview?url=` + encodeURIComponent(Base64.encode(decodeURIComponent(row.url)))
    $openUrl(view_url, '_blank')
  }
}

function getSummaries (param) {
  const { columns, data } = param
  const sums = [];
  columns.forEach((column, index) => {
    if (index === 0) {
      sums[index] = '合计'
      return
    }
    if(column.property) {
      let col = null
      props.option.column.filter(fit => {
        if(fit.prop == column.property) {
          col = fit
        }
      })
      if(col && col.enableStatistics) {
        const values = data.map(item => Number(item[column.property]));
        if(!values.every(value => isNaN(value))) {
          sums[index] = values.reduce((prev, curr) => {
            const value = Number(curr)
            if (!isNaN(value)) {
              return prev + curr
            } else {
              return prev
            }
          }, 0)
          if(col.precision) {
            sums[index] = Number(sums[index]).toFixed(col.precision)
          }
          if(col.thoudsandthable) {
            sums[index] = getThousandthNumber(sums[index], col)
          }
          if(col.unit) {
            sums[index] += ` ${col.unit}`
          }
        } else {
          sums[index] = ''
        }
      }else{
        sums[index] = ''
      }
    }else{
      sums[index] = ''
    }
  })
  return sums
}

function fixedNumber (val, item) {
  let num = isNaN(Number(val)) ? val : (item.precision ? Number(val).toFixed(item.precision || 0) : val)
  return item.thoudsandthable ? getThousandthNumber(num, item) : num
}

function getThousandthNumber(num, item) {
  let str = ''
  if(typeof num == 'number' || (typeof num == 'string' && num)) {
    str = num + ''
    str = str.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    if(str.includes('.') == false && item.precision > 0) {
      str += '.'
      for(let i=0; i < item.precision; i++) {
        str += '0'
      }
    }
  }
  return str
}

watch(() => props.isClearSelect, (newVal, oldVal) => {
  if(newVal != 0) {
    clearSelect()
  }
})

watch(() => props.data, (newVal, oldVal) => {
  if(props.option.menuFix) {
    doLayout()
  }
  nextTick(() => {
    if(newVal && newVal.length > 0) {
      showSelected()
    }
  })
})

watch(() => props.option.searchLoading, (newVal, oldVal) => {
  searchOption['submitLoading'] = (newVal || false)
})

watch(() => props.sortsList, (newVal, oldVal) => {
  sortList.value = newVal
})
</script>
<style lang="scss">
$jvsDarkBlue: #1E6FFF;
.el-form.search-form{
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  .el-form-item{
    display: flex;
    align-items: center;
    margin-left: 0;
    margin-bottom: 10px;
  }
  .form-item-btn{
    width: auto;
  }
}
.tablepagination{
  padding-top: 20px;
  overflow: hidden;
  .el-pagination {
    float: right;
    padding: 0;
    margin-right: 20px;
  }
}
.table-top {
  width: 100%;
  overflow: hidden;
  .table-top-left{
    float: left;
  }
  .table-top-right {
    float: right;
    display: flex;
  }
}
.table-title{
  margin: 0;
  text-align: center;
}
// 去除斑马纹
.el-table--striped .el-table__body tr.el-table__row--striped td{
  background-color: #fff;
}
.el-table--enable-row-hover .el-table__body tr:hover>td{
  background-color: #F5F7FA;
}
.jvs-table{
  height: 100%;
  display: flex;
  flex-direction: column;
  .table-body-box{
    flex: 1;
    padding-top: 10px;
    background-color: #fff;
    box-sizing: border-box;
    overflow: hidden;
    .el-table{
      .el-table__header{
        .el-table__cell:not(.table-index-column){
          box-sizing: border-box;
          border-bottom: 0;
          .cell{
            line-height: 42px;
            @include SourceHanSansCN-Bold;
            padding: 0 24px;
          }
        }
        .table-icon-column{
          .cell{
            display: flex;
            align-items: center;
            .info-icon{
              margin-left: 10px;
              cursor: pointer;
            }
            .sort-column-item-icon{
              cursor: pointer;
              display: flex;
              flex-direction: column;
              justify-content: center;
              align-items: center;
              i:nth-of-type(2) {
                margin-top: -7px;
              }
            }
            .sort-column-item-icon.asc{
              i:nth-of-type(1) {
                color: $jvsDarkBlue;
              }
            }
            .sort-column-item-icon.desc{
              i:nth-of-type(2) {
                color: $jvsDarkBlue;
              }
            }
          }
        }
      }
      .el-table__body-wrapper{
        overflow: auto;
        .el-table__cell:not(.table-index-column){
          .cell{
            position: relative;
            line-height: 42px;
            padding: 0 24px;
            .file-text-a{
              cursor: pointer;
              color: $jvsDarkBlue;
            }
            .down-file-icon{
              position: absolute;
              right: 0;
              top: 5px;
              cursor: pointer;
            }
          }
        }
      }
    }
    .table-body-slot-box{
      width: 100%;
      box-sizing: border-box;
      height: 100%;
      overflow: hidden;
      overflow-y: auto;
      .table-body-slot-box-item{
        float: left;
        border: 1px solid #EEEFF0;
        background-color: #fff;
        width: calc(25% - 12px);
        margin-left: 16px;
        margin-bottom: 16px;
        box-sizing: border-box;
        border-radius: 6px;
        overflow: hidden;
        height: 217px;
        cursor: pointer;
        position: relative;
        .card-top-head{
          padding: 12px 16px;
          box-sizing: border-box;
          border-bottom: 1px solid #EEEFF0;
          border-radius: 6px;
          .table-body-slot-box-item-row-title{
            margin-bottom: 0;
            .title{
              margin: 0;
              padding: 0;
              font-size: 16px;
              @include SourceHanSansCN-Bold;
              font-weight: 700;
              color: #363B4C;
              height: 23px;
              line-height: 23px;
            }
          }
          .table-body-slot-box-item-row-subhead-desc{
            margin-top: 4px;
            font-size: 12px;
            line-height: 12px;
            @include SourceHanSansCN-Regular;
            color: #6F7588;
            display: flex;
            align-items: center;
            box-sizing: border-box;
            .divider-bar{
              display: block;
              width: 1px;
              height: 15px;
              background-color: #6F7588;
              margin: 1px 5px;
            }
          }
        }
        .table-body-slot-box-row-others{
          margin: 16px;
        }
        .table-body-slot-box-item-row{
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 8px;
          font-size: 14px;
          @include SourceHanSansCN-Regular;
          color: #6F7588;
          line-height: 20px;
          .img{
            display: block;
            width: 80px;
            height: 80px;
            border-radius: 8px;
            overflow: hidden;
          }
        }
        .table-body-slot-box-item-row:nth-last-of-type(1){
          margin-bottom: 2px;
        }
        .info{
          display: flex;
          align-content: center;
          flex: 1;
          .label{
            margin-right: 10px;
          }
        }
        .card-bottom{
          position: absolute;
          width: calc(100% - 4px);
          left: 2px;
          bottom: 2px;
          height: 36px;
          background: #F5F6F7;
          border-radius: 0px 0px 6px 6px;
          overflow: hidden;
          div{
            width: 100%;
            display: flex;
            align-items: center;
            align-items: center;
            .el-button--text{
              flex: 1;
              height: 36px;
              box-sizing: border-box;
              margin-left: 2px;
              margin-right: 2px;
              position: relative;
              border-radius: 0;
              span{
                font-size: 14px;
                @include SourceHanSansCN-Regular;
                color: #363B4C;
              }
            }
            .el-button--text:hover{
              background-color: #1E6FFF;
              span{
                color: #fff;
              }
            }
            .el-button--text::before{
              content: "";
              width: 1px;
              height: 20px;
              background: #EEEFF0;
              position: absolute;
              left: -3px;
              top: 8px;
              z-index: 0;
            }
            .el-button--text:nth-of-type(1){
              margin-left: 0;
            }
            .el-button--text:nth-of-type(1)::before{
              display: none;
            }
            .el-button--text:nth-last-of-type(1){
              margin-right: 0;
            }
          }
        }
      }
      .table-body-slot-box-item:nth-of-type(4n+1){
        margin-left: 0;
      }
    }
    .jvs-table-body-slot-loading{
      background-image: url('/jvs-ui-public/img/loading.gif');
      background-repeat: no-repeat;
      background-position: center;
    }
  }
  .jvs-table-hideTop{
    .table-top{
      display: none;
    }
  }
  .back-color-text{
    background-clip: text!important;
    -webkit-background-clip :text!important;
    -webkit-text-fill-color: transparent;
  }
}
// 文字提示
.el-tooltip__popper{
  max-width: 70%;
}
.jvs-table-nocolumn{
  position: relative;
  .jvs-table-top, .table-title, .table-body-box, .tablepagination{
    display: none;
  }
}
.img-file-list{
  padding: 30px 35px;
  .if-item{
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 15px;
    margin-top: 15px;
    box-sizing: border-box;
    width: 300px;
    flex-wrap: nowrap;
    min-height: 40px;
    background: #ebeef5;
    border-radius: 5px;
    border: 1px solid #ebeef5;
    overflow: hidden;
    img{
      display: block;
      width: 50px;
      height: 50px;
      margin-right: 10px;
    }
    i{
      cursor: pointer;
      font-size: 20px;
    }
    span{
      flex: 1;
      margin-right: 10px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: pre;
      font-size: 16px;
    }
  }
  .if-item:nth-of-type(1){
    margin-top: 0;
  }
  .if-item:hover{
    border-color: #409EFF;
    span{
      color: #409EFF;
    }
  }
}
.card-item-more-tool-list{
  width: 100px;
  div{
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: 0 10px;
    .el-button{
      width: 100%;
      cursor: pointer;
    }
    .el-button:hover{
      transition: 0.3s;
      background-color: #eff2f7;
    }
    .el-button+.el-button{
      margin-left: 0;
    }
  }
}
</style>
