<template>
  <div :ref="el => dynamicRefMap(el, item.prop)" :class="{'jvs-form-item': true, 'jvs-form-item-disabled': item.disabled}" style="display:flex;align-items:center;">
    <!-- 普通文本框 -->
    <el-input
      v-model="forms[item.prop]"
      v-if='(item.type==="input" || !item.type) && !item.searchable && getDisableexpress("show")'
      :show-word-limit="item.showwordlimit"
      :minlength="item.minlength"
      :maxlength="item.maxlength"
      :placeholder="item.placeholder || item.label"
      :clearable="item.clearable"
      :show-password="item.showpassword"
      :disabled="item.disabled"
      :prefix-icon="item.prefixicon"
      :suffix-icon="item.suffixicon"
      :size="commonStore.theme.form.size || item.size || 'small'"
      :class="{'has-pend': (item.prepend || item.append)}"
      @focus="beforeValue = forms[item.prop]"
      @blur="formChange"
    >
      <template v-if='item.prepend' #prepend><span>{{item.prepend}}</span></template>
      <template v-if='item.append' #append><span>{{item.append}}</span></template>
    </el-input>
    <span
      v-if='(item.type==="input" || !item.type) && !item.searchable && !getDisableexpress("show")'
      :style="'padding: 0px 10px;border-radius: 5px;'+
      (getDisableexpress('color') ? ('color:' + getDisableexpress('color') + ';') : '') +
      (getDisableexpress('bgcolor') ? ('background-color:' + getDisableexpress('bgcolor') + ';') : '') +';'">{{forms[item.prop]}}</span>
    <span class="el-form-item__error" v-if='errorShow'>{{item.regularMessage}}</span>
    <el-input
      v-model="forms[item.prop]"
      v-if='item.type==="InputReadOnly" || item.type==="inputReadOnly"'
      :disabled="item.disabled || true"
      :placeholder="item.placeholder || item.label"
      :size="commonStore.theme.form.size || item.size || 'small'"
    ></el-input>
    <el-input
      type="textarea"
      v-if='item.type==="textarea"'
      v-model="forms[item.prop]"
      :rows="item.rows"
      :show-word-limit="item.showwordlimit"
      :minlength="item.minlength"
      :maxlength="item.maxlength"
      :placeholder="item.placeholder || item.label"
      :clearable="item.clearable"
      :disabled="item.disabled"
      :size="commonStore.theme.form.size || item.size || 'small'"
      :autosize="item.autoSize || false"
      @focus="beforeValue = forms[item.prop]"
      @blur="formChange"
    ></el-input>
    <el-input
      type="textarea"
      v-if='item.type==="textareaReadOnly"'
      v-model="forms[item.prop]"
      rows="2"
      :placeholder="item.placeholder || item.label"
      :disabled="item.disabled || true"
      :size="commonStore.theme.form.size || item.size || 'small'"
    ></el-input>
    <el-input-number
      v-if='item.type==="inputNumber"'
      v-model="forms[item.prop]"
      :min="item.min"
      :max="item.max"
      :step="item.step"
      :step-strictly="item.stepstrictly"
      :precision="item.precision"
      :disabled="item.disabled"
      :controls-position="item.controlsposition"
      :placeholder="item.placeholder || item.label"
      :size="commonStore.theme.form.size || item.size || 'small'"
      :class="{'input-number-hide': [undefined, null].indexOf(forms[item.prop]) > -1, 'show-thoudsandth-number': (item.thoudsandthable && showThoudsandth), 'input-number-unit': item.unit}"
      @change="formChange"
      @focus="showThoudsandthHandle(false); beforeValue = forms[item.prop];"
      @blur="showThoudsandthHandle(true);blurValidate();"
    >
      <template #suffix>
        <span>{{item.append}}</span>
      </template>
    </el-input-number>
    <span v-if='item.type==="inputNumber" && item.thoudsandthable && showThoudsandth' :class="{'input-number-Thousandth': true, 'input-number-Thousandth-disabled': item.disabled, 'unit': item.unit}" @click="showThoudsandthHandle(false)">{{getThousandthNumber(forms[item.prop])}}</span>
    <span v-if='item.type==="inputNumber" && item.disabled && !item.thoudsandthable' class="input-number-textcon">{{( (forms[item.prop] != null && forms[item.prop] != undefined && forms[item.prop] != '') || forms[item.prop] == 0 ) ? forms[item.prop] : (item.unit ? '' : '-')}}</span>

    <el-select
      v-if='item.type==="select"'
      v-model="forms[item.prop]"
      :placeholder="item.placeholder || item.label"
      :multiple="item.multiple"
      :collapse-tags="!item.collapsetags"
      :disabled="item.disabled"
      :clearable="item.clearable === false ? false : true"
      :filterable="item.filterable"
      :allow-create="item.allowcreate"
      :size="commonStore.theme.form.size || item.size || 'small'"
      :class="{'jvs-select-multiple': item.multiple}"
      @change="formChange"
    >
      <el-option
        v-for="(sitem, sitx) in selectOption"
        :key="sitem[(item.props && item.props.value) || 'value']+ item.prop + '-' + sitx"
        :label="sitem[(item.props && item.props.label) || 'label']"
        :value="sitem[(item.props && item.props.value) || 'value']"
        :disabled="sitem.disabled"
      >
        <span style="float: left">{{ sitem[(item.props && item.props.label) || 'label'] }}</span>
        <span v-if="item.props && item.props.secTitle && sitem[item.props.secTitle]" style="float: right; color: #8492a6; font-size: 13px">{{ sitem[item.props.secTitle] }}</span>
        <span v-if="sitem.tip" style="float: right; color: #8492a6; font-size: 13px">{{ sitem.tip }}</span>
      </el-option>
    </el-select>

    <el-switch
      v-if='item.type==="switch"'
      v-model="forms[item.prop]"
      :disabled="item.disabled"
      :active-text="item.activetext"
      :inactive-text="item.inactivetext"
      :active-color="item.activecolor"
      :inactive-color="item.inactivecolor"
      :size="commonStore.theme.form.size || item.size || 'small'"
      @change="formChange"
    ></el-switch>

    <el-slider
      v-if='item.type==="slider"'
      v-model="forms[item.prop]"
      :min="item.min"
      :max="item.max"
      :disabled="item.disabled"
      :step="item.step"
      :show-stops="item.showstops"
      :show-input="item.showinput"
      :input-size="commonStore.theme.form.size || item.size || 'small'"
      :range="item.range"
      :size="commonStore.theme.form.size || item.size || 'small'"
      @change="formChange"
    ></el-slider>

    <el-time-select
      v-if='item.type==="timeSelect"'
      v-model="forms[item.prop]"
      :disabled="item.disabled"
      :clearable="item.clearable"
      :picker-options="item.pickeroptions"
      :placeholder="item.placeholder || item.label"
      :prefix-icon="item.prefixicon"
      format="HH:mm:ss"
      value-format="HH:mm:ss"
      :default-value="item.defaultValue"
      :size="commonStore.theme.form.size || item.size || 'small'"
      @change="formChange"
    ></el-time-select>

    <el-time-picker
      v-if='item.type==="timePicker"'
      v-model="forms[item.prop]"
      :disabled="item.disabled"
      :clearable="item.clearable"
      :placeholder="item.placeholder || item.label"
      :prefix-icon="item.prefixicon"
      :is-range="item.isrange"
      :start-placeholder="item.startplaceholder"
      :end-placeholder="item.endplaceholder"
      :range-separator="item.rangeseparator"
      format="HH:mm:ss"
      value-format="HH:mm:ss"
      :default-value="item.defaultValue"
      :size="commonStore.theme.form.size || item.size || 'small'"
      @change="formChange"
    ></el-time-picker>

    <el-date-picker
      v-if='(item.type==="datePicker") && ( item.datetype=="date" || item.datetype=="dates" || item.datetype=="daterange")'
      v-model="forms[item.prop]"
      :type="item.datetype"
      :placeholder="item.placeholder || item.label"
      :clearable="item.clearable"
      :disabled="item.disabled"
      :prefix-icon="item.prefixicon"
      format="YYYY-MM-DD"
      value-format="YYYY-MM-DD"
      :start-placeholder="item.startplaceholder"
      :end-placeholder="item.endplaceholder"
      :range-separator="item.rangeseparator"
      :picker-options="startEndLimitHandle"
      :default-value="item.defaultValue"
      :size="commonStore.theme.form.size || item.size || 'small'"
      align="center"
      @change="formChange"
    ></el-date-picker>
    <el-date-picker
      v-if='(item.type==="datePicker") && item.datetype=="week"'
      v-model="forms[item.prop]"
      type="week"
      format="YYYY 第 WW 周"
      value-format="YYYY-MM-DD"
      :placeholder="item.placeholder || item.label"
      :clearable="item.clearable"
      :disabled="item.disabled"
      :prefix-icon="item.prefixicon"
      :start-placeholder="item.startplaceholder"
      :end-placeholder="item.endplaceholder"
      :range-separator="item.rangeseparator"
      :picker-options="startEndLimitHandle"
      :default-value="item.defaultValue"
      :size="commonStore.theme.form.size || item.size || 'small'"
      align="center"
      @change="formChange"
    ></el-date-picker>
    <el-date-picker
      v-if='(item.type==="datePicker")&& ( item.datetype=="month"|| item.datetype=="monthrange" )'
      v-model="forms[item.prop]"
      :type="item.datetype"
      :placeholder="item.placeholder || item.label"
      :clearable="item.clearable"
      :disabled="item.disabled"
      :prefix-icon="item.prefixicon"
      format="YYYY-MM"
      value-format="YYYY-MM"
      :start-placeholder="item.startplaceholder"
      :end-placeholder="item.endplaceholder"
      :range-separator="item.rangeseparator"
      :picker-options="startEndLimitHandle"
      :default-value="item.defaultValue"
      :size="commonStore.theme.form.size || item.size || 'small'"
      align="center"
      @change="formChange"
    ></el-date-picker>
    <el-date-picker
      v-if='(item.type==="datePicker") && item.datetype=="year"'
      v-model="forms[item.prop]"
      type="year"
      :placeholder="item.placeholder || item.label"
      :clearable="item.clearable"
      :disabled="item.disabled"
      :prefix-icon="item.prefixicon"
      format="YYYY"
      value-format="YYYY"
      :start-placeholder="item.startplaceholder"
      :end-placeholder="item.endplaceholder"
      :range-separator="item.rangeseparator"
      :picker-options="startEndLimitHandle"
      :default-value="item.defaultValue"
      :size="commonStore.theme.form.size || item.size || 'small'"
      align="center"
      @change="formChange"
    ></el-date-picker>
    <el-date-picker
      v-if='(item.type==="datePicker") && (item.datetype=="datetime" || item.datetype=="datetimerange")'
      v-model="forms[item.prop]"
      :type="item.datetype"
      :placeholder="item.placeholder || item.label"
      :clearable="item.clearable"
      :disabled="item.disabled"
      format="YYYY-MM-DD HH:mm:ss"
      value-format="YYYY-MM-DD HH:mm:ss"
      :start-placeholder="item.startplaceholder"
      :end-placeholder="item.endplaceholder"
      :range-separator="item.rangeseparator"
      :picker-options="startEndLimitHandle"
      :default-value="item.defaultValue"
      :size="commonStore.theme.form.size || item.size || 'small'"
      :default-time="item.defaultTime"
      date-format="YYYY-MM-DD"
      time-format="HH:mm:ss"
      align="center"
      @change="formChange"
    ></el-date-picker>

    <el-radio-group
      v-if='(item.type==="radio")'
      v-model="forms[item.prop]"
      :disabled="item.disabled"
      :size="commonStore.theme.form.size || item.size || 'small'"
      @change="formChange"
    >
      <div v-if='item.radiotype==="yuan" || !item.radiotype'>
        <el-radio
          v-for="(item2) in selectOption"
          :key="item2[(item.props && item.props.value) || 'value']+item2[(item.props && item.props.label) || 'label']+'yuan'"
          :value="item2[(item.props && item.props.value) || 'value']"
          :disabled="item2.disabled"
        >{{item2[(item.props && item.props.label) || 'label']}}</el-radio>
      </div>
      <div v-if='item.radiotype==="button"'>
        <el-radio-button
          v-for="(item2) in selectOption"
          :key="item2[(item.props && item.props.value) || 'value'] + item2[(item.props && item.props.label) || 'label'] +'but'"
          :value="item2[(item.props && item.props.value) || 'value']"
          :disabled="item2.disabled"
        >{{item2[(item.props && item.props.label) || 'label']}}</el-radio-button>
      </div>
    </el-radio-group>

    <el-checkbox-group
      v-if='(item.type==="checkbox") && forms[item.prop]'
      v-model="forms[item.prop]"
      :disabled="item.disabled"
      :border="item.border"
      :min="item.min"
      :max="item.max"
      :size="commonStore.theme.form.size || item.size || 'small'"
      @change="formChange"
    >
      <div v-if='(item.checkboxtype=== "fang" || !item.checkboxtype) && selectOption && selectOption.length > 0'>
        <el-checkbox
          v-for="(item2) in selectOption"
          :key="item2[(item.props && item.props.value) || 'value']+item.label"
          :value="item2[(item.props && item.props.value) || 'value']"
          :disabled="item2.disabled"
        >{{item2[(item.props && item.props.label) || 'label']}}</el-checkbox>
      </div>
      <div v-if='item.checkboxtype=== "button" && selectOption && selectOption.length > 0'>
        <el-checkbox-button
          v-for="(item2) in selectOption"
          :key="'checkbut'+item2[(item.props && item.props.value) || 'value']+item.label"
          :value="item2[(item.props && item.props.value) || 'value']"
          :disabled="item2.disabled"
        >{{item2[(item.props && item.props.label) || 'label']}}</el-checkbox-button>
      </div>
    </el-checkbox-group>

    <!-- 颜色选择器 -->
    <div v-if="(item.type == 'colorSelect')" class="color-select">
      <el-color-picker v-model="forms[item.prop]" :placeholder="item.placeholder || item.label" :predefine="predefineColors" @change="formChange"></el-color-picker>
    </div>
    <jvs-colorpicker v-if="item.type == 'colorPicker'" :modelValue="forms[item.prop] ? forms[item.prop] : undefined" :resetColor="item.resetColor || item.defaultValue" :prop="item.prop" :form="forms" :allowEmpty="item.clearable" @update:modelValue="colorChangeHandle"></jvs-colorpicker>

    <!-- 图片 -->
    <ul
      class="el-upload-list el-upload-list--picture-card"
      v-if='item.type==="image" && forms[item.prop] && forms[item.prop].length > 0'
    >
      <li
        tabindex="0"
        class="el-upload-list__item is-success"
        v-for="mi in forms[item.prop]"
        :key="'image'+mi.url"
        @click="handlePictureCardPreview(mi.url)"
      >
        <el-image style="width: 100%; height: 100%;" :src="mi.url" :fit="item.fit || 'contain'">
          <template #error>
            <div class="image-slot loading-back" style="position:absolute;">
              <i class="el-icon-loading" style="font-size: 24px;color:#999;"></i>
            </div>
          </template>
        </el-image>
      </li>
      <li
        tabindex="0"
        class="el-upload-list__item"
        v-if='!forms[item.prop] || forms[item.prop].length==0'
      >
        <el-image style="width: 100%; height: 100%;" src :fit="item.fit || 'contain'">
          <template #error>
            <div class="image-slot loading-back" style="position:absolute;">
              <i class="el-icon-loading" style="font-size: 24px;color:#999;"></i>
            </div>
          </template>
        </el-image>
      </li>
    </ul>
    <!-- 没有图片 -->
    <span v-if='item.type==="image" && (!forms[item.prop] || forms[item.prop].length==0)'>无</span>

    <!-- 上传图片 -->
    <el-upload
      v-if='(item.type === "imageUpload")'
      :class="((item.parentKey && item.parentType == 'tableForm') ? (tableFileList[tableRowAIndex] && tableFileList[tableRowAIndex].length || 0) : item.fileList.length) < (item.limit ? item.limit : 5) ? 'form-list-upload-img' : 'form-list-upload-img-none'"
      :ref="el => uploadRefMap(el, 'uploadImageBtn'+'_'+item.prop)"
      :action="item.action || (item.uploadHttp && item.uploadHttp.url) || ''"
      :multiple="item.multipleUpload"
      :limit="item.limit || 5"
      :headers="item.headers || {}"
      :file-list="(item.parentKey && item.parentType == 'tableForm') ? tableFileList[tableRowAIndex] : item.fileList"
      :size="commonStore.theme.form.size || item.size || 'small'"
      list-type="picture-card"
      :data="formatUploadData(item)"
      accept=".jpg,.jpeg,.png,.gif,.bmp,.JPG,.JPEG,.PBG,.GIF,.BMP"
      :auto-upload="true"
      :disabled="item.disabled"
      :on-success="handleSuccess"
      :on-error="item.handleError"
      :on-preview="handlePictureCardPreviewUpload"
      :on-remove="handleRemove"
      :on-change="uploadChangeHandle"
      :before-upload="beforeUpload"
    >
      <i class="el-icon-plus"></i>
      <template #tip>
        <div v-if="imageValidate" class="el-upload__tip" style="color: #F56C6C;font-size: 12px;">只能上传图片，且不超过20M</div>
      </template>
      
    </el-upload>

    <!-- 预览图片 -->
    <el-dialog v-if='item.type === "imageUpload" || item.type === "image"' v-model="dialogVisible" class="preViewDialog" append-to-body>
      <img width="100%" :src="dialogImageUrl" alt />
    </el-dialog>

    <!-- 上传文件 -->
    <el-upload
      v-if='item.type === "fileUpload"'
      :accept="item.fileType ? item.fileType : '*'"
      :class="((item.parentKey && item.parentType == 'tableForm') ? (tableFileList[tableRowAIndex] && tableFileList[tableRowAIndex].length || 0) : item.fileList.length) < (item.limit ? item.limit : 5) ? 'form-list-upload-file' : 'form-list-upload-file-none'"
      :ref="el => uploadRefMap(el, 'uploadFileBtn'+'_'+item.prop)"
      action=""
      :multiple="item.multipleUpload"
      :limit="item.limit"
      :headers="item.headers"
      :file-list="(item.parentKey && item.parentType == 'tableForm') ? ((tableFileList[tableRowAIndex] && tableFileList[tableRowAIndex].length > 0) ? tableFileList[tableRowAIndex] : item.fileList) : item.fileList"
      :size="commonStore.theme.form.size || item.size || 'small'"
      :data="formatUploadData(item)"
      :disabled="item.disabled"
      :on-remove="handleRemove"
      :on-change="uploadChangeHandle"
      :on-preview="handleFilePreviewUpload"
      :on-exceed="handleExceed"
      :http-request="handleUploadRequest"
    >
      <template #trigger><el-button size="small" type="primary">选取文件</el-button></template>
      <template #tip>
        <div v-if="fileValidate" class="el-upload__tip" style="color: #F56C6C;font-size: 12px;">文件大小不超过{{item.fileSize}}MB</div>
      </template>
    </el-upload>

    <!-- 文件列表 -->
    <ul class="el-upload-list el-upload-list--text" v-if="item.type==='file' && forms[item.prop] && forms[item.prop].length > 0">
      <li v-for="fi in forms[item.prop]" :key="'file'+fi.url" class="el-upload-list__item is-success">
        <a class="el-upload-list__item-name" target="_blank" :href="fi.url?fi.url:'javascript:void(0)'">
          <i class="el-icon-document"></i>
          {{fi.name}}
        </a>
        <label class="el-upload-list__item-status-label">
          <i class="el-icon-upload-success el-icon-circle-check"></i>
        </label>
        <i class="el-icon-close"></i>
      </li>
    </ul>
    <span v-if="item.type==='file' && (!forms[item.prop] || forms[item.prop].length==0)">无</span>

    <!-- 描述框 -->
    <div
      v-if="item.type==='box'"
      :style="'width:100%;padding:16px;border-radius:4px;white-space: normal;text-align:'+item.contentposition+';font-size:'+item.fontsize+'px;color:'+item.textcolor+';font-weight:'+item.fontweight+';background-color:'+(item.boxback?item.boxback:'none')+';'"
    >
      {{forms[item.prop]}}
      <span v-if='!forms[item.prop]' v-html="getHtmlText(item.text)" style="white-space: normal;"></span>
    </div>
    <!-- 链接 -->
    <a v-if="item.type =='link'"
      :href="forms[item.prop]?formatUrl(forms[item.prop]):'javascript:void(0);'"
      :target="item.openType"
      :style="'height: 36px;line-height: 36px;text-align:'+item.contentposition+';font-size:'+item.fontsize+'px;color:'+item.textcolor+';font-weight:'+item.fontweight+';text-decoration:'+item.textdecoration+';'"
    >{{item.text}}</a>
    <!-- 嵌入页面 -->
    <div v-if="item.type==='iframe'" :style="'width:100%;height:'+item.iframeheight+'px;background:#ecf5ff;'">
      <iframe
        :name="item.id"
        :id="item.prop"
        :src="forms[item.prop] || item.iframeurl"
        frameborder="0"
        width="100%"
        height="100%"
        scrolling="scroll"
      ></iframe>
    </div>

    <!-- p文字 -->
    <p v-if="item.type === 'p'" class="form-item-p" :style="`text-align:${item.contentposition};font-size:${item.fontsize}px;color:${item.textcolor};margin:0;`">
      <span :style="item.barHide === false ? '' : 'padding-left: 0;'">
        <i v-if="item.barHide === false"></i>
        <b>{{item.text}}</b>
      </span>
    </p>

    <!-- 分割线 -->
    <el-divider v-if="item.type === 'divider'" :content-position='item.contentposition'>{{item.text}}</el-divider>

    <!-- 流水号 -->
    <el-input v-if="item.type==='serialNumber'" disabled v-model="forms[item.prop]" placeholder="根据流水号规则自动生成" class="show-disable"></el-input>

    <el-input v-if="item.type==='positionMap'" disabled v-model="forms[item.prop]" placeholder="请用移动端打开页面获取位置信息" class="show-disable"></el-input>

    <!-- 可编辑表格 -->
    <div v-if="['tableForm'].indexOf(item.type) > -1" style="flex:1;width:100%;">
      <tableForm :formRef="formRef" :item="item" :option="tableFormOption"
        :data="forms[item.prop]" :originOption="originOption"
        :defalutSet="defalutSet" :rowData="rowData"
        :roleOption="roleOption"
        :userList="userList"
        :departmentList="departmentList"
        :postList="postList"
        :resetRadom="resetRadom"
        :designId="designId"
        :forms="forms"
        :originForm="originForm ? originForm : forms"
        :dataModelId="dataModelId"
        :changeRandom="changeRandom"
        :changeDomItem="changeDomItem"
        :isView="isView"
        :execsList="execsList"
        :jvsAppId="jvsAppId"
        :dataTriggerFresh="dataTriggerFresh"
        :tableFormAddHandleIndex="tableFormAddHandleIndex"
        @formChange="$emit('formChange', forms)"
        @reInitData="reInitData"
        @setTable="setTableHandle"
        @resetAddIndex="resetAddIndex">
        <template #menuBtn="scope">
          <jvs-button v-if="!item.disabled  && !item.iconBtn" text @click="deleteRow(scope.row, scope.index)">删除</jvs-button>
          <span v-if="!item.disabled && item.iconBtn === true" class="delete-icon-button" @click="deleteRow(scope.row, scope.index)">
            <span class="border-line"></span>
          </span>
        </template>
      </tableForm>
      <el-row style="margin-top:10px;" v-if="item.editable && item.addBtn && !item.disabled && !item.iconBtn">
        <jvs-button size="small" @click="addRowHandle">新增</jvs-button>
      </el-row>
      <div v-if="item.editable && item.addBtn && !item.disabled && item.iconBtn === true" class="bottom-add-button">
        <div class="button" @click="addRowHandle">
          <div class="icon">
            <svg aria-hidden="true">
              <use xlink:href="#jvs-ui-icon-xinjian"></use>
            </svg>
          </div>
          <span>{{item.addBtnText || '新增'}}</span>
        </div>
      </div>
    </div>

    <!-- 计数器   滑块   显示单位 -->
    <span v-if="['inputNumber', 'slider'].indexOf(item.type) > -1 && item.unit" class="unit-item-empty-span" style="padding-left: 5px;">{{(item.disabled && (!forms[item.prop] && forms[item.prop] !== 0)) ? '-  ' : ''}}</span>
    <span v-if="['inputNumber', 'slider'].indexOf(item.type) > -1 && item.unit" :class="{'unit-span': item.type == 'inputNumber'}">{{item.unit.trim()}}</span>

    <!-- 地区选择 -->
    <el-cascader
      v-if="item.type==='chinaArea'"
      v-model="forms[item.prop]"
      size="small"
      :options="chinaAreaList"
      clearable
      :show-all-levels="item.showalllevels"
      :collapse-tags="!item.collapsetags"
      :disabled="item.disabled"
      :props="{
        expandTrigger: 'hover',
        multiple: item.multiple === false ? item.multiple : true,
        label: 'name',
        value: item.emitKey ? item.emitKey : 'code',
        emitPath: item.emitPath
      }"
      @change="formChange"
    >
    </el-cascader>

    <!-- 富文本 -->
    <div v-if="item.type === 'htmlEditor'" :id="item.prop+'-editor'" style="z-index: 1000;width:100%;"></div>

    <!-- 按钮 -->
    <jvs-button v-if="item.type === 'button'" :disabled="item.disabled" :type="item.buttonType" :round="item.buttonRound" :size="item.size" :loading="item.loading" @click="btnClick">{{item.text}}</jvs-button>

    <!-- 级联选择 -->
    <el-cascader
      v-if="item.type==='cascader' && item.pickType !== 'tree' "
      v-model="forms[item.prop]"
      size="small"
      :options="cascaderList"
      clearable
      :show-all-levels="item.showalllevels"
      :collapse-tags="!item.collapsetags"
      :disabled="item.disabled"
      :props="{
        expandTrigger: 'hover',
        multiple: item.multiple === false ? item.multiple : true,
        label: item.datatype == 'rule' ? (item.props ? item.props.label : 'name') : 'name',
        value: item.datatype == 'rule' ? (item.props ? item.props.value : 'value') : (item.datatype == 'dataModel' ? 'value' : (item.emitKey ? item.emitKey : 'value')),
        emitPath: item.emitPath,
        checkStrictly: true
      }"
      :class="{'fixed-height': !item.collapsetags}"
      @change="formChange"
    >
    </el-cascader>

    <tree-selector
      v-if="item.type==='cascader' && item.pickType === 'tree'"
      :form="forms"
      :item="item"
      :options="cascaderList"
      :showalllevels="item.showalllevels"
      :collapsetags="!item.collapsetags"
      :disabled="item.disabled"
      :props="{
        multiple: item.multiple === false ? item.multiple : true,
        label: item.datatype == 'rule' ? (item.props ? item.props.label : 'name') : 'name',
        value: item.datatype == 'rule' ? (item.props ? item.props.value : 'value') : (item.datatype == 'dataModel' ? 'value' : (item.emitKey ? item.emitKey : 'value')),
        children: 'children',
        emitPath: item.emitPath,
        checkStrictly: false
      }"
      @change="formChange"
    ></tree-selector>

    <!-- 时间线 -->
    <el-timeline v-if="item.type == 'timeline' && forms[item.prop]" style="max-height: 300px; overflow: hidden; overflow-y: auto;">
      <el-timeline-item
        v-for="(activity, index) in forms[item.prop]"
        :key="item.prop + 'timeline-' + index"
        :timestamp="activity[item.timestamp || 'timestamp']">
        <div v-html="activity[item.content || 'content']"></div>
      </el-timeline-item>
    </el-timeline>

    <!-- 右侧提示 -->
    <el-tooltip v-if="item.tips && item.tips.position == 'right' && item.tips.text" class="form-item-tooltip" effect="dark" :content="item.tips.text" placement="top">
      <i class="el-icon-question"></i>
    </el-tooltip>

    <!-- 后置插槽 -->
    <slot v-if="item.appendSlot" :name="item.prop+'AppendItem'"></slot>
  </div>
</template>

<script lang="ts" setup name="formitem">
import {
  ref,
  reactive,
  watch ,
  onMounted,
  computed,
  getCurrentInstance,
  nextTick
} from 'vue'
import { useI18n } from 'vue-i18n'
import { Base64 } from 'js-base64'
import E from 'wangeditor'
import { ElNotification } from 'element-plus'

import useCommonStore from '@/store/common.js'
import useTagsStore from '@/store/tags.js'

import {areaList} from '@/const/chinaArea.js'

import { getSelectData} from '@/components/api'

import tableForm from '@/components/basic-assembly/tableForm.vue'
import treeSelector from '@/components/basic-assembly/treeSelector.vue'

const { $openUrl}  = getCurrentInstance().appContext.config.globalProperties
const emit = defineEmits(['currentValueHandle', 'formChange', 'reInitData', 'validateHandle', 'file', 'uploadChange'])
const { t } = useI18n()

const beforeSlotCompute = computed(prop => {
  return prop + 'Before'
})

const commonStore = useCommonStore()
const tagsStore = useTagsStore()

const props = defineProps({
  // 表单对象
  form: {
    type: Object,
    default: () => {
      return {}
    }
  },
  // 表单内的组件对象
  item: {
    type: Object,
    default: () => {
      return {}
    }
  },
  originOption: {
    type: Object
  },
  defalutSet: {
    type: Object
  },
  formRef: {
    type: String,
    default: 'ruleForm'
  },
  // 用户列表
  userList : {
    type: Array,
    default: () => {
      return []
    }
  },
  // 角色列表
  roleOption: {
    type: Array,
    default: () => {
      return []
    }
  },
  // 部门列表
  departmentList: {
    type: Array,
    default: () => {
      return []
    }
  },
  // 岗位列表
  postList: {
    type: Array,
    default: () => {
      return []
    }
  },
  // 是否需要刷新组件
  freshBoolean: {
    type: Boolean
  },
  // 是否需要重新初始化
  reinitFlag: {
    type: Number
  },
  // 表格行数据
  rowData: {
    type: Object
  },
  resetRadom: {
    type: Number
  },
  tableRowAIndex: {
    type: Number
  },
  designId: {
    type: String
  },
  dataModelId: {
    type: String
  },
  changeRandom: {
    type: Number
  },
  changeDomItem: {
    type: Object
  },
  isView: {
    type: Boolean
  },
  disabledControl: {
    type: Boolean
  },
  execsList: {
    type: Array
  },
  jvsAppId:  {
    type: String
  },
  originForm: {
    type: Object
  },
  parentDomWidth: {
    type: Number,
    default: ()=> {
      return 0
    }
  },
  dataTriggerFresh: {
    type: Number
  },
  delcomRandom: {
    type: Number
  }
})

const forms = computed(() => {
  return props.form
})
const tableFormOption = computed(() => {
  return {
    addBtn: false,
    viewBtn: false,
    delBtn: false,
    editBtn: false,
    page: false,
    border: props.item.border,
    align: props.item.align || 'left',
    menuAlign: props.item.menuAlign || 'left',
    cancal: false,
    showOverflow: true,
    hideTop: props.item.hideTop || false,
    tableColumn: props.item.tableColumn
  }
})

const selectOption = ref([])
let startEndLimitHandle = reactive({
  disabledDate: time => {
    let bool = false
    if(!props.item.startLimit) {
      let end = new Date(props.item.endLimit).getTime()
      if(time.getTime() <= end) {
        bool = false
      }else{
        bool = true
      }
    }
    if(!props.item.endLimit) {
      let start = new Date(props.item.startLimit).getTime()
      if(time.getTime() >= start-8.64e7) {
        bool = false
      }else{
        bool = true
      }
    }
    if(!props.item.startLimit && !props.item.endLimit) {
      bool = false
    }
    if(props.item.startLimit&&props.item.endLimit) {
      let start = new Date(props.item.startLimit).getTime()
      let end = new Date(props.item.endLimit).getTime()
      if(time.getTime() >= start-8.64e7 && time.getTime() <= end) {
        bool = false
      }else{
        bool = true
      }
    }
    return bool
  }
})
const errorShow = ref(false) // 自定义验证提示错误
const dialogVisible = ref(false) // 预览图片弹框
const dialogImageUrl = ref('') // 预览图片地址
const chinaAreaList = ref(areaList)
const cascaderList = ref([]) // 级联选择数据
const editor = ref(null) // 富文本
const pathArr = ref([]) // 路径结果
const imageValidate = ref(false)
const fileValidate = ref(false)
const eventList = ref(['button', 'input', 'textarea', 'inputNumber', 'select', 'slider', 'switch',
  'datePicker', 'timeSelect', 'timePicker', 'radio', 'checkbox', 'imageUpload', 'fileUpload',
  'htmlEditor', 'cascader', 'chinaArea', 'department', 'role', 'user', 'job'
])
const initHtml = ref('') // 记录富文本初始值
const iconToolWidth = ref(400) // 图标组件工具栏宽
let tableFileList = reactive([[]])
const showThoudsandth = ref(true)
const predefineColors = ref([
  '#ffd700',
  '#ff8c00',
  '#ff4500',
  '#c71585',
  '#FF99CC',
  '#FF6666',
  '#CCCCFF',
  '#CCCCCC',
  '#99CCFF',
  '#99CC99',
  '#90ee90',
  '#66CC66',
  '#669933',
  '#663366',
  '#490954',
  '#3471ff',
  '#333300',
  '#1e90ff',
  '#00ced1',
  '#003399'
])
const beforeValue = ref(null)
const fileList = ref([])
const fileListCopy = ref([])
const fileUploadIndex = ref(0)
const fileUploadFailIndex = ref(0)
const uploadLoading = ref(false)
const channelBroad = ref(null)
const notMatch = ref(false)
const tableFormAddHandleIndex = ref(-1)
const searchResult = ref('')
const bluetoothBeacon = ref('')
const uploadRefs = ref({})
const uploadRefMap = (el, ref) => {
  if(el) {
    uploadRefs.value[ref] = el
  }
}
const dynamicRefs = ref({})
const dynamicRefMap = (el, ref) => {
  if(el) {
    dynamicRefs.value[ref] = el
  }
}

createdHandle()
onMounted(() => {
  if(props.item.type == 'htmlEditor') {
    document.getElementById(props.item.prop + '-editor').innerHTML = ''
    initEditor(props.item.prop)
  }
  if(props.item.type == 'tableForm') {
    if(dynamicRefs.value[props.item.prop]) {
      let tw = 0
      props.item.tableColumn.filter(ti => {
        tw += ti.span * 8
      })
      if(tw < dynamicRefs.value[props.item.prop].offsetWidth - 50) {
        props.item.tableColumn.filter(ti => {
          ti.width = ''
        })
      }
    }
  }
})

function createdHandle () {
  if(props.item.shortcutEnable) {
    if(props.item.datetype == 'daterange') {
      startEndLimitHandle['shortcuts'] = [
        {
          text: '今天',
          onClick(picker) {
            picker.$emit('pick', [new Date(), new Date()]);
          }
        },
        {
          text: '最近三天',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000 * 24 * 2)
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '最近一周',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000 * 24 * 6);
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '本月',
          onClick(picker) {
            const date = new Date(new Date().getFullYear() + '-' + (new Date().getMonth() + 1) + '-01');
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '三个月内',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000 * 24 * (30 * 3 - 1));
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '今年',
          onClick(picker) {
            const date = new Date(new Date().getFullYear() + '-01-01');
            picker.$emit('pick', [date, new Date()]);
          }
        }
      ]
    }
    if(props.item.datetype == 'monthrange') {
      startEndLimitHandle['shortcuts'] = [
        {
          text: '本月',
          onClick(picker) {
            picker.$emit('pick', [new Date(), new Date()]);
          }
        },
        {
          text: '近三月',
          onClick(picker) {
            let y = new Date().getFullYear()
            let m = new Date().getMonth() + 1
            let cou = m - 2
            if(cou < 0) {
              y -= 1
              m += cou
            }else{
              m -= 2
            }
            const date = new Date(y + '-' + (m < 10 ? `0${m}` : m));
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '近半年',
          onClick(picker) {
            let y = new Date().getFullYear()
            let m = new Date().getMonth() + 1
            let cou = m - 5
            if(cou < 0) {
              y -= 1
              m += cou
            }else{
              m -= 5
            }
            const date = new Date(y + '-' + (m < 10 ? `0${m}` : m));
            picker.$emit('pick', [date, new Date()]);
          }
        },
      ]
    }
    if(props.item.datetype == 'datetimerange') {
      startEndLimitHandle['shortcuts'] = [
        {
          text: '最近五分钟',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 60 * 5 * 1000)
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '最近一小时',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000)
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '今天',
          onClick(picker) {
            let date = new Date()
            let y = date.getFullYear()
            let m = date.getMonth() + 1 + ''
            if(Number(m) < 9) {
              m = `0${m}`
            }
            let d = date.getDate() + ''
            if(Number(d) < 9) {
              d = `0${d}`
            }
            picker.$emit('pick', [new Date(`${y}-${m}-${d} 00:00:00`), new Date()]);
          }
        },
        {
          text: '最近三天',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000 * 24)
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '最近一周',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000 * 24 * 6);
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '本月',
          onClick(picker) {
            const date = new Date(new Date().getFullYear() + '-' + (new Date().getMonth() + 1) + '-01');
            picker.$emit('pick', [date, new Date()]);
          }
        },
        {
          text: '三个月内',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000 * 24 * 30 * 3);
            picker.$emit('pick', [date, new Date()]);
          }
        }
      ]
    }
  }
  initItem(false, 'create')
}

function changeHandle (val) {
  props.item.currVal = val
  forms.value[props.item.prop] = val
  formChange()
}

function uploadChangeHandle (file, fileList) {
  let obj = {}
  obj[props.item.prop] = fileList
  emit('currentValueHandle', obj)
}

function beforeUpload (file) {
  if(file.size > 20971520) {
    if(props.item.type == 'imageUpload') {
      imageValidate.value = true
    }else{
      fileValidate.value = true
    }
    return false
  }else{
    if(props.item.type == 'imageUpload') {
      imageValidate.value = false
    }else{
      fileValidate.value = false
    }
  }
}

async function initItem (stopGetDicUrl?, loadType?) {
  if(!props.item.disabled) {
    if(props.item.disabledControl == true) {
      props.item.disabled = true
    }
  }
  if(!props.item.dicUrl && !props.item.url) {
    if(props.item.datatype == 'rule') {
      // 优化👌 下拉框(来自模型或逻辑)
      if(loadType == 'create' && props.item.type == 'select' && ['dataModel', 'rule'].indexOf(props.item.datatype) > -1 && !(selectOption.value && selectOption.value.length > 0)) {
        if(forms.value[props.item.prop] && forms.value[props.item.prop+'_1']) {
          if(props.item.multiple){
            let fitv = forms.value[props.item.prop+'_1'].split(',')
            if(forms.value[props.item.prop].length > 0 && fitv.length > 0 && forms.value[props.item.prop].length == fitv.length) {
              let tl = []
              for(let fix in forms.value[props.item.prop]) {
                if(fitv[fix]) {
                  let tlb = {}
                  tlb[(props.item.props && props.item.props.value) || 'value'] = forms.value[props.item.prop][fix]
                  tlb[(props.item.props && props.item.props.label) || 'label'] = fitv[fix]
                  tl.push(tlb)
                }
              }
              if(tl.length > 0) {
                selectOption.value = tl
              }
            }
          }else{
            let tlb = {}
            tlb[(props.item.props && props.item.props.value) || 'value'] = forms.value[props.item.prop]
            tlb[(props.item.props && props.item.props.label) || 'label'] = forms.value[props.item.prop+'_1']
            selectOption.value = [tlb]
          }
        }
      }
      if(!props.isView && !stopGetDicUrl) {
        getSelectRuleData(loadType)
      }
    }else{
      if(props.item.dicData) {
        selectOption.value = props.item.dicData
        getDicDataByExpress(selectOption.value)
        if(['cascader', 'tab'].indexOf(props.item.type) == -1 && forms.value[props.item.prop]) {
          if((props.item.multiple || props.item.type == 'checkbox')) {
            if(forms.value[props.item.prop].length > 0) {
              let allIn = true
              for(let sif in forms.value[props.item.prop]) {
                let isIn = false
                selectOption.value.filter(sis => {
                  if(forms.value[props.item.prop][sif] == sis[(props.item.props && props.item.props.value) || 'value']) {
                    isIn = true
                  }
                })
                if(!isIn) {
                  allIn = false
                }
              }
              if(!allIn) {
                forms.value[props.item.prop] = []
                notMatch.value = true
              }
            }
          }else{
            let needClear = true
            selectOption.value.filter(sis => {
              if(sis[(props.item.props && props.item.props.value) || 'value'] == forms.value[props.item.prop]) {
                needClear = false
              }
            })
            if(needClear) {
              forms.value[props.item.prop] = ''
              notMatch.value = true
            }
          }
        }
      }
    }
  } else {
    // 优化👌 下拉框(来自模型或逻辑)  
    if(loadType == 'create' && props.item.type == 'select' && ['dataModel', 'rule'].indexOf(props.item.datatype) > -1 && !(selectOption.value && selectOption.value.length > 0)) {
      if(forms.value[props.item.prop] && forms.value[props.item.prop+'_1']) {
        if(props.item.multiple){
          let fitv = forms.value[props.item.prop+'_1'].split(',')
          if(forms.value[props.item.prop].length > 0 && fitv.length > 0 && forms.value[props.item.prop].length == fitv.length) {
            let tl = []
            for(let fix in forms.value[props.item.prop]) {
              if(fitv[fix]) {
                let tlb = {}
                tlb[(props.item.props && props.item.props.value) || 'value'] = forms.value[props.item.prop][fix]
                tlb[(props.item.props && props.item.props.label) || 'label'] = fitv[fix]
                tl.push(tlb)
              }
            }
            if(tl.length > 0) {
              selectOption.value = tl
            }
          }
        }else{
          let tlb = {}
          tlb[(props.item.props && props.item.props.value) || 'value'] = forms.value[props.item.prop]
          tlb[(props.item.props && props.item.props.label) || 'label'] = forms.value[props.item.prop+'_1']
          selectOption.value = [tlb]
        }
      }
    }
    if(!props.isView && !stopGetDicUrl) {
      if(props.item.datatype == 'rule') {
        getSelectRuleData(loadType)
      }else{
        if(props.item.type != 'cascader') {
          getSelectUrlData(loadType)
        }
      }
    }else{
      getDicDataByExpress(selectOption.value)
    }
  }
  if(['checkbox', 'formbox'].indexOf(props.item.type) > -1) {
    if(!forms.value[props.item.prop]) {
      forms.value[props.item.prop] = []
      if(props.item.type == 'checkbox' && props.item.defaultValue) {
        forms.value[props.item.prop] = props.item.defaultValue.split(',')
      }
    }
  }
  // 下拉切换是否多选时，初始化数据类型
  if(props.item.type == 'select') {
    if(props.item.multiple) {
      if(!forms.value[props.item.prop]) {
        forms.value[props.item.prop] = []
      }
      if(JSON.stringify(forms.value[props.item.prop]) == '[]' && props.item.defaultValue) {
        forms.value[props.item.prop] = props.item.defaultValue.split(',')
      }
    }else{
      if((!forms.value[props.item.prop] && forms.value[props.item.prop] !== 0 && forms.value[props.item.prop] !== false) || forms.value[props.item.prop] instanceof Array) {
        forms.value[props.item.prop] = ''
      }
    }
    if(props.item.datatype == 'option') {
      if(props.item.props && !props.item.props.label) {
        props.item.props.label = 'label'
        props.item.props.value = 'value'
      }
    }
  }
  // 滑块
  if(props.item.type == 'slider') {
    if(props.item.range) {
      if(!forms.value[props.item.prop]) {
        forms.value[props.item.prop] = [0, props.item.max / 2]
      }
    }else{
      if(!forms.value[props.item.prop] || forms.value[props.item.prop] instanceof Array) {
        forms.value[props.item.prop] = 0
      }
    }
  }
  if(props.item.type == 'timeSelect') {
    if(props.item.pickeroptions && props.item.pickeroptions.start && props.item.pickeroptions.start != props.item.defaultValue) {
      props.item.defaultValue = props.item.pickeroptions.start
    }
  }
  // 表单项默认值填充，权重小于表单初始化值
  if((props.item.defaultValue || props.item.defaultValue === false || props.item.defaultValue === "" || props.item.defaultValue === 0) && !notMatch.value) {
    // 时间选择 多选
    if(props.item.type == 'timePicker') {
      if(!props.item.isrange) {
        let date = new Date(props.item.defaultValue)
        props.item.defaultValue = ''+ (date.getHours() > 9 ? date.getHours() : `0${date.getHours()}`) + ':' + (date.getMinutes() > 9 ? date.getMinutes() : `0${date.getMinutes()}`) + ':' + (date.getSeconds() > 9 ? date.getSeconds() : `0${date.getSeconds()}`)
        forms.value[props.item.prop] = props.item.defaultValue
      }
    }else{
      if(props.isView) {
        if(props.item.type == 'checkbox') {
          if(props.item.defaultValue) {
            forms.value[props.item.prop] = props.item.defaultValue.split(',')
          }
        }else{
          forms.value[props.item.prop] = props.item.defaultValue
        }
      }else{
        if(!forms.value[props.item.prop] && forms.value[props.item.prop] !== false && forms.value[props.item.prop] !== 0) {
          if(props.item.type == 'checkbox' && props.item.defaultValue && (!forms.value[props.item.prop] || JSON.stringify(forms.value[props.item.prop]) == '[]')) {
            forms.value[props.item.prop] = props.item.defaultValue.split(',')
          }else{
            forms.value[props.item.prop] = props.item.defaultValue
          }
        }
      }
    }
  }
  // 级联选择类
  if(props.item.type == 'cascader') {
    if(props.item.datatype == 'option' && props.item.dicData) {
      cascaderList.value = props.item.dicData
    }
    if(!stopGetDicUrl && props.item.datatype == 'dataModel' && props.item.formId && props.item.url && props.item.props.label && props.item.props.value && props.item.props.secTitle) {
      getCascaderData()
    }
    if(!props.isView && !stopGetDicUrl && props.item.datatype == 'rule') {
      getSelectRuleData()
    }
  }
  // tab选项卡  step步骤条
  if(['tab', 'step'].indexOf(props.item.type) > -1) {
    // 脱离数据
    if(props.item.detachData) {
      if(props.item.dicData && props.item.dicData.length > 0) {
        props.item.dicData.filter(dct => {
          if(dct.prop) {
            if(props.item.column[dct.name] && props.item.column[dct.name] .length > 0) {
              !forms.value[dct.prop] && (forms.value[dct.prop] = {})
            }
          }
        })
      }
    }else{
      if(props.item.dicData && props.item.dicData.length > 0) {
        !forms.value[props.item.prop] && (forms.value[props.item.prop] = {})
        for(let col in props.item.column) {
          if(props.item.column[col] && props.item.column[col].length > 0) {
            !forms.value[props.item.prop][col] && (forms.value[props.item.prop][col] = {})
          }
        }
      }
    }
  }
  if(['tableForm'].indexOf(props.item.type) > -1) {
    !forms.value[props.item.prop] && (forms.value[props.item.prop] = [])
  }
  if(props.item.type == 'reportTable') {
    if(props.item.dicData && props.item.dicData.length > 0) {
      !forms.value[props.item.prop] && (forms.value[props.item.prop] = {})
      for(let di in props.item.dicData) {
        if(!forms.value[props.item.prop][props.item.dicData[di].value]) {
          forms.value[props.item.prop][props.item.dicData[di].value] = {}
        }
      }
    }
  }
  if(['imageUpload', 'fileUpload'].indexOf(props.item.type) > -1) {
    if(forms.value[props.item.prop]) {
      if(!props.item.parentKey || props.item.parentType != 'tableForm'){
        if(typeof forms.value[props.item.prop] == 'object' && (forms.value[props.item.prop] instanceof Array) == false) {
          forms.value[props.item.prop] = []
        }
        props.item['fileList'] = forms.value[props.item.prop]
      }
      tableFileList[props.tableRowAIndex] = forms.value[props.item.prop]
    }
    if(!props.item.headers) {
      props.item['headers'] = {}
    }
    if(props.jvsAppId) {
      props.item.headers['businessId'] = props.jvsAppId
    }
  }
  if(props.item.type == 'timeline' && !forms.value[props.item.prop]) {
    forms.value[props.item.prop] = []
  }
  if(['childrenForm', 'connectForm'].indexOf(props.item.type) > -1 && !forms.value[props.item.prop]) {
    forms.value[props.item.prop] = {}
  }
  if(props.item.type == 'button') {
    props.item['loading'] = false
  }
  // 时间  日期 组件单位变化兼容历史数据
  if(['timePicker', 'datePicker'].indexOf(props.item.type) > -1) {
    if(props.item.type == 'timePicker') {
      if(props.item.isrange) {
        if(typeof props.form[props.item.prop] == 'string') {
          props.form[props.item.prop] = props.form[props.item.prop] ? [props.form[props.item.prop]] : []
        }
        if(props.item.notRightNow) {
          props.form[props.item.prop] = []
        }
      }else{
        if(typeof props.form[props.item.prop] == 'object' && props.form[props.item.prop] instanceof Array) {
          props.form[props.item.prop] = (props.form[props.item.prop] && props.form[props.item.prop].length > 0) ? props.form[props.item.prop][0] : ''
        }
        if(!props.form[props.item.prop] || props.form[props.item.prop].includes('NaN')) {
          let date = new Date()
          props.form[props.item.prop] = (''+ (date.getHours() > 9 ? date.getHours() : `0${date.getHours()}`) + ':' + (date.getMinutes() > 9 ? date.getMinutes() : `0${date.getMinutes()}`) + ':' + (date.getSeconds() > 9 ? date.getSeconds() : `0${date.getSeconds()}`))
        }
        if(props.item.notRightNow) {
          props.form[props.item.prop] = ''
        }
      }
    }
    if(props.item.type == 'datePicker') {
      if(['date', 'week', 'month', 'year', 'datetime'].indexOf(props.item.datetype) > -1) {
        if(typeof props.form[props.item.prop] == 'object' && props.form[props.item.prop] instanceof Array) {
          props.form[props.item.prop] = (props.form[props.item.prop] && props.form[props.item.prop].length > 0) ? formatDateHandle(props.form[props.item.prop][0]) : ''
        }else{
          if(props.form[props.item.prop]) {
            props.form[props.item.prop] = formatDateHandle(props.form[props.item.prop])
          }
        }
      }else{
        if(typeof props.form[props.item.prop] == 'string') {
          props.form[props.item.prop] = props.form[props.item.prop] ? [formatDateHandle(props.form[props.item.prop])] : []
        }else{
          if(props.form[props.item.prop] && props.form[props.item.prop].length > 0) {
            for(let dix in props.form[props.item.prop]) {
              props.form[props.item.prop][dix] = formatDateHandle(props.form[props.item.prop][dix])
            }
          }
        }
      }
    }
  }
  if(props.item.type == 'timeSelect') {
    if(props.item.notRightNow) {
      props.form[props.item.prop] = ''
    }
  }
}

function formatDateHandle (val) {
  let date = new Date(val)
  let year = date.getFullYear().toString()
  let month = (date.getMonth() + 1) < 10 ? ('0' + (date.getMonth() + 1)) : ('' + (date.getMonth() + 1))
  let day = date.getDate() < 10 ? ('0' + date.getDate()) : ('' + date.getDate())
  let hour = date.getHours() < 10 ? ('0' + date.getHours()) : ('' + date.getHours())
  let minute = date.getMinutes() < 10 ? ('0' + date.getMinutes()) : ('' + date.getMinutes())
  let seconds = date.getSeconds() < 10 ? ('0' + date.getSeconds()) : ('' + date.getSeconds())
  switch(props.item.datetype) {
    case 'date':
    case 'dates':
    case 'daterange':
    case 'week':
      return year+'-'+month+'-'+day;
    case 'datetime':
    case 'datetimerange':
      return year+'-'+month+'-'+day+' '+ hour+':'+minute+':'+seconds;
    case 'month':
    case 'monthrange':
      return year+'-'+month;
    case 'year':
      return year;
    default: return val;
  }
}

function getSelectUrlData (loadType?) {
  let url = props.item.dicUrl || props.item.url
  if(!url) {
    return false
  }
  if(!props.item.formId || (props.item.formId && props.item.props && props.item.props.label)) {
    let fs = []
    fs = (props.item.props && props.item.props.label) ? [props.item.props.label] : []
    if(props.item.props && props.item.props.secTitle && fs.length > 0) {
      fs.push(props.item.props.secTitle)
    }
    let postData = {
      fieldList: fs,
      conditions: []
    }
    if(props.item.props && props.item.props.sourceFieldId) {
      postData['sourceFieldId'] = props.item.props.sourceFieldId
    }
    let nomptyValue = true
    let filterAble = true
    if(((props.item.showFrom && props.item.showFrom.indexOf("url") !== -1 && props.item.datatype === "dataModel") || props.item.searchable) && !props.item.dataFilterable){
      filterAble = false
    }
    // 搜索文本中的查询条件字段不带条件
    if(!props.item.isSearch) {
      if(props.item.dataFilterGroupList) {
        let gtarr = []
        for(let gi in props.item.dataFilterGroupList) {
          let gtp = []
          for(let df in props.item.dataFilterGroupList[gi]) {
            let dfit = {
              enabledQueryTypes: props.item.dataFilterGroupList[gi][df].enabledQueryTypes,
              fieldKey: props.item.dataFilterGroupList[gi][df].fieldKey,
            }
            if(['cust', 'role', 'department', 'job', 'user'].indexOf(props.item.dataFilterGroupList[gi][df].type) > -1) {
              dfit['value'] = props.item.dataFilterGroupList[gi][df].value
            }else{
              let bindDomNode = {}
              let bindkey = props.item.dataFilterGroupList[gi][df].value
              let formTemp = forms.value
              if(typeof props.item.dataFilterGroupList[gi][df].value == 'object' && props.item.dataFilterGroupList[gi][df].value instanceof Array) {
                formTemp = props.originForm
                bindkey = props.item.dataFilterGroupList[gi][df].value[props.item.dataFilterGroupList[gi][df].value.length-1]
                if(props.item.dataFilterGroupList[gi][df].value.length > 1) {
                  getNodeDom(props.originOption.column, props.item.dataFilterGroupList[gi][df].value, bindDomNode)
                }
                if(bindDomNode && bindDomNode['prop']) {
                  if(bindDomNode['parentDom'] && bindDomNode['parentDom'].length > 0) {
                    if(bindDomNode['parentDom'][0].type == 'tab') {
                      if(bindDomNode['parentDom'][0].detachData) {
                        if(bindDomNode['parentDom'][1].prop) {
                          formTemp = props.originForm[bindDomNode['parentDom'][1].prop]
                        }
                      }else{
                        formTemp = forms.value
                      }
                    }
                  }
                }
              }
              dfit['value'] = formTemp[bindkey]
              if((dfit['value'] == undefined || dfit['value'] == null || dfit['value'] == '') &&  props.item.dataFilterGroupList[gi][df].enabledQueryTypes != 'isNull') {
                nomptyValue = false
              }
            }
            gtp.push(dfit)
          }
          gtarr.push(gtp)
        }
        if(filterAble) {
          postData['groupConditions'] = gtarr
        }
      }else{
        if(props.item.dataFilterList) {
          for(let df in props.item.dataFilterList) {
            let dfit = {
              enabledQueryTypes: props.item.dataFilterList[df].enabledQueryTypes,
              fieldKey: props.item.dataFilterList[df].fieldKey,
            }
            if(props.item.dataFilterList[df].type == 'cust') {
              dfit['value'] = props.item.dataFilterList[df].value
            }else{
              dfit['value'] = forms.value[props.item.dataFilterList[df].value]
              if((dfit['value'] == undefined || dfit['value'] == null || dfit['value'] == '') &&  props.item.dataFilterList[df].enabledQueryTypes != 'isNull') {
                nomptyValue = false
              }
            }
            if(filterAble) {
              postData.conditions.push(dfit)
            }
          }
        }
      }
      if(props.item.dataItemExpressable) {
        if(props.item.dataItemExpressGroupList) {
          let gtarr = []
          for(let gi in props.item.dataItemExpressGroupList) {
            let gtp = []
            for(let df in props.item.dataItemExpressGroupList[gi]) {
              let dfit = {
                enabledQueryTypes: props.item.dataItemExpressGroupList[gi][df].enabledQueryTypes,
                fieldKey: props.item.dataItemExpressGroupList[gi][df].fieldKey,
              }
              postData.fieldList.push(props.item.dataItemExpressGroupList[gi][df].fieldKey)
              if(props.item.dataItemExpressGroupList[gi][df].type == 'cust') {
                dfit['value'] = props.item.dataItemExpressGroupList[gi][df].value
              }else{
                let bindDomNode = {}
                let bindkey = props.item.dataItemExpressGroupList[gi][df].value
                let formTemp = forms.value
                if(typeof props.item.dataItemExpressGroupList[gi][df].value == 'object' && props.item.dataItemExpressGroupList[gi][df].value instanceof Array) {
                  formTemp = props.originForm
                  bindkey = props.item.dataItemExpressGroupList[gi][df].value[props.item.dataItemExpressGroupList[gi][df].value.length-1]
                  if(props.item.dataItemExpressGroupList[gi][df].value.length > 1) {
                    getNodeDom(props.originOption.column, props.item.dataItemExpressGroupList[gi][df].value, bindDomNode)
                  }
                  if(bindDomNode && bindDomNode['prop']) {
                    if(bindDomNode['parentDom'] && bindDomNode['parentDom'].length > 0) {
                      if(bindDomNode['parentDom'][0].type == 'tab') {
                        if(bindDomNode['parentDom'][0].detachData) {
                          if(bindDomNode['parentDom'][1].prop) {
                            formTemp = props.originForm[bindDomNode['parentDom'][1].prop]
                          }
                        }else{
                          formTemp = forms.value
                        }
                      }
                    }
                  }
                }
                dfit['value'] = formTemp[bindkey]
                if((dfit['value'] == undefined || dfit['value'] == null || dfit['value'] == '') &&  props.item.dataItemExpressGroupList[gi][df].enabledQueryTypes != 'isNull') {
                  nomptyValue = false
                }
              }
              gtp.push(dfit)
            }
            gtarr.push(gtp)
          }
          postData['enableConditions'] = gtarr
        }
      }
    }
    if(!filterAble) {
      nomptyValue = true
    }
    if(nomptyValue) {
      getSelectData(url, props.item.method ? props.item.method : 'get', (props.item.method == 'post') ? postData : null, (props.item.method == 'post') ? props.designId : (props.item.formId ? props.item.formId : props.designId)).then(res => {
        if(res.data.code === 0) {
          // 优化👌 下拉框(来自模型或逻辑)
          if(loadType == 'create' && props.item.type == 'select' && ['dataModel', 'rule'].indexOf(props.item.datatype) > -1 && selectOption.value && selectOption.value.length > 0) {
            // 首次初始化存在labelvalue默认选项不清空
          }else{
            selectOption.value = []
          }
          for(let sitem in res.data.data){
            if(typeof res.data.data[sitem] == 'string') {
              let needAdd = true
              if(loadType == 'create' && props.item.type == 'select' && ['dataModel', 'rule'].indexOf(props.item.datatype) > -1 && forms.value[props.item.prop] && forms.value[props.item.prop+'_1']) {
                if(props.item.multiple) {
                  if(forms.value[props.item.prop+'_1'].length > 0 && forms.value[props.item.prop].indexOf(res.data.data[sitem]) > -1) {
                    needAdd = false
                  }
                }else{
                  if(forms.value[props.item.prop] == res.data.data[sitem]) {
                    needAdd = false
                  }
                }
              }
              if(needAdd) {
                selectOption.value.push({
                  label: res.data.data[sitem],
                  value: res.data.data[sitem]
                })
              }
            }else{
              let needAdd = true
              if(loadType == 'create' && props.item.type == 'select' && ['dataModel', 'rule'].indexOf(props.item.datatype) > -1 && forms.value[props.item.prop] && forms.value[props.item.prop+'_1']) {
                if(props.item.multiple) {
                  if(forms.value[props.item.prop+'_1'].length > 0 && forms.value[props.item.prop].indexOf(res.data.data[sitem][props.item.props.value ? props.item.props.value : 'value']) > -1) {
                    needAdd = false
                  }
                }else{
                  if(forms.value[props.item.prop] == res.data.data[sitem][props.item.props.value ? props.item.props.value : 'value']) {
                    needAdd = false
                  }
                }
              }
              if(needAdd) {
                selectOption.value.push({...res.data.data[sitem], disabled: (res.data.data[sitem].disabled || res.data.data[sitem].jvsDisableItem)})
              }
            }
          }
          getDicDataByExpress(selectOption.value)
          // console.log(selectOption.value)
          if(loadType != 'create' && forms.value[props.item.prop]) {
            if((props.item.multiple || props.item.type == 'checkbox')) {
              if(forms.value[props.item.prop].length > 0) {
                let allIn = true
                for(let sif in forms.value[props.item.prop]) {
                  let isIn = false
                  selectOption.value.filter(sis => {
                    if(forms.value[props.item.prop][sif] == sis[(props.item.props && props.item.props.value) || 'value']) {
                      isIn = true
                    }
                  })
                  if(!isIn) {
                    allIn = false
                  }
                }
                if(!allIn) {
                  forms.value[props.item.prop] = []
                  notMatch.value = true
                }
              }
            }else{
              let needClear = true
              selectOption.value.filter(sis => {
                if(sis[(props.item.props && props.item.props.value) || 'value'] == forms.value[props.item.prop]) {
                  needClear = false
                }
              })
              if(needClear) {
                forms.value[props.item.prop] = ''
                notMatch.value = true
              }
            }
          }
        }
      })
    }else{
      selectOption.value = []
    }
  }
}

function getSelectRuleData (loadType?) {
  // 优化👌 下拉框(来自模型或逻辑)
  if(loadType == 'create' && props.item.type == 'select' && selectOption.value && selectOption.value.length > 0) {
    // 首次初始化存在labelvalue默认选项补清空
  }else{
    selectOption.value = []
  }
  cascaderList.value = []
}

function getCascaderData () {
  // 获取模型树形结构
  let paramData = {label: props.item.props.label, value: props.item.props.value, secTitle: props.item.props.secTitle, filter: {}}
  let nomptyValue = true
  // 搜索文本查询条件字段不带条件
  if(!props.item.isSearch && props.item.dataFilterable !== false) {
    if(props.item.dataFilterGroupList) {
      let gtarr = []
      for(let gi in props.item.dataFilterGroupList) {
        let gtp = []
        for(let df in props.item.dataFilterGroupList[gi]) {
          let dfit = {
            enabledQueryTypes: props.item.dataFilterGroupList[gi][df].enabledQueryTypes,
            fieldKey: props.item.dataFilterGroupList[gi][df].fieldKey,
          }
          if(['cust', 'role', 'department', 'job', 'user'].indexOf(props.item.dataFilterGroupList[gi][df].type) > -1) {
            dfit['value'] = props.item.dataFilterGroupList[gi][df].value
          }else{
            dfit['value'] = forms.value[props.item.dataFilterGroupList[gi][df].value]
            if((dfit['value'] == undefined || dfit['value'] == null || dfit['value'] == '') &&  props.item.dataFilterGroupList[gi][df].enabledQueryTypes != 'isNull') {
              nomptyValue = false
            }
          }
          gtp.push(dfit)
        }
        gtarr.push(gtp)
      }
      paramData.filter['groupConditions'] = gtarr
    }else{
      if(props.item.dataFilterList) {
        let conditions = []
        for(let df in props.item.dataFilterList) {
          let dfit = {
            enabledQueryTypes: props.item.dataFilterList[df].enabledQueryTypes,
            fieldKey: props.item.dataFilterList[df].fieldKey,
          }
          if(props.item.dataFilterList[df].type == 'cust') {
            dfit['value'] = props.item.dataFilterList[df].value
          }else{
            dfit['value'] = forms.value[props.item.dataFilterList[df].value]
            if((dfit['value'] == undefined || dfit['value'] == null || dfit['value'] == '') &&  props.item.dataFilterList[df].enabledQueryTypes != 'isNull') {
              nomptyValue = false
            }
          }
          conditions.push(dfit)
        }
        paramData.filter['conditions'] = conditions
      }
    }
  }
  getSelectData(props.item.url, 'post', paramData, (props.item.method == 'post') ? props.designId : (props.item.formId ? props.item.formId : props.designId)).then(res => {
    if(res.data.code === 0) {
      let tplist = res.data.data
      if(props.item.filterProp && forms.value[props.item.filterProp]) {
        cascaderDataFilter(tplist)
      }
      cascaderList.value = JSON.parse(JSON.stringify(tplist))
    }
  })
}

function handlePictureCardPreview (url) {
  dialogImageUrl.value = url
  dialogVisible.value = true
}

function handlePictureCardPreviewUpload (file) {
  dialogImageUrl.value = file.url
  dialogVisible.value = true
}

function handleFilePreviewUpload (file) {
  if(file.url) {
    $openUrl(file.url, '_blank')
  }
}

// 字段值改变传出表单
async function formChange (value?) {
  if(props.item.type == 'colorSelect') {
    if(!props.form[props.item.prop]) {
      props.form[props.item.prop] = ''
    }
  }
  if(props.item.type == 'cascader' && props.item.pickType === 'tree') {
    props.form[props.item.prop] = value[props.item.prop]
  }
  if(props.item.type == 'inputNumber') {
    if(props.item.parentType == 'tableForm') {
      if(forms.value[props.item.prop] === null || forms.value[props.item.prop] === undefined) {
        forms.value[props.item.prop] = (props.item.precision ? Number(props.item.min || 0).toFixed(props.item.precision) : (props.item.min || 0))
      }
    }
  }
  emit('formChange', props.form, props.item, beforeValue.value)
  if(eventList.value.indexOf(props.item.type) > -1) {
    eventRequireHandle('reinit')
  }else{
    if(props.isView !== true) {
      emit('reInitData', props.item.prop, props.item.parentKey, props.tableRowAIndex)
    }
  }
  if(['user', 'role', 'department', 'group', 'job'].indexOf(props.item.type) > -1 || (props.item.type == 'cascader' && props.item.pickType === 'tree')) {
    if(props.item.rules && props.item.rules.length > 0) {
      if(props.item.rules[0].required) {
        if(props.item.multiple) {
          if(props.form[props.item.prop].length > 0) {
            emit('validateHandle', {type: 'clear', item: props.item})
          }else{
            emit('validateHandle', {type: 'validate', item: props.item})
          }
        }else{
          if(props.form[props.item.prop]) {
            emit('validateHandle', {type: 'clear', item: props.item})
          }else{
            emit('validateHandle', {type: 'validate', item: props.item})
          }
        }
      }
    }
  }
}

function addRowHandle () {
  if(!forms.value[props.item.prop]) {
    forms.value[props.item.prop] = []
  }
  let adrow = {}
  let keys = []
  let pk = []
  tableFormOption.value.tableColumn.filter(item => {
    keys.push(item.prop)
    pk = item.parentKey
    // 时间范围  日期范围
    if((item.type == 'timePicker' && item.isrange)) {
      let val = new Date()
      let h = val.getHours()
      let m = val.getMinutes()
      let s = val.getSeconds()
      let vastr = (h<10?('0'+h) : h) + ':' + (m<10?('0'+m) : m) + ':' + (s<10?('0'+s) : s)
      adrow[item.prop] = [vastr, vastr]
    }
    if(item.type == 'datePicker' && ['dates', 'daterange', 'monthrange', 'datetimerange'].indexOf(item.datetype) > -1) {
      adrow[item.prop] = []
    }
    if(item.type == 'switch' && item.defaultValue == true) {
      adrow[item.prop] = true
    }
  })
  forms.value[props.item.prop].push(adrow)
  tableFormAddHandleIndex.value = forms.value[props.item.prop].length -1
  setTimeout( ()=> {
    emit('reInitData', keys.join(','), pk, (forms.value[props.item.prop].length -1), 'add')
  }, 0)
  console.log(forms.value[props.item.prop])
}

function resetAddIndex () {
  tableFormAddHandleIndex.value = -1
}

function deleteRow (row, index) {
  if(props.item.formId) {
    if(!props.form[props.item.prop+'_del']) {
      props.form[props.item.prop+'_del'] = []
    }
    let list = JSON.parse(JSON.stringify(props.form[props.item.prop+'_del']))
    list.push(row)
    props.form[props.item.prop+'_del'] = list
  }
  forms.value[props.item.prop].splice(index, 1)
  emit('reInitData', props.item.prop, props.item.parentKey, props.tableRowAIndex, 'del')
}

// 同步表格数据
function setTableHandle (data, bindFlowId, dynamicNode) {
  forms.value[props.item.prop] = data
  if(props.item.type == 'tableForm' && data.length == 1 && forms.value[props.item.prop+'_line']) {
    delete forms.value[props.item.prop+'_line']
  }
  if(bindFlowId) {
    forms.value[props.item.prop+'_flowId'] = bindFlowId
  }
  if(dynamicNode) {
    forms.value[props.item.prop+'_dynamicNode'] = dynamicNode
  }
}

// 获取宽度占比
function getWidth (item) {
  let w = 400
  if(item.prop && document.getElementById('icon-select-item-'+item.prop)) {
    w = document.getElementById('icon-select-item-'+item.prop).clientWidth
  }
  return w
}

// 初始化富文本
function initEditor (prop) {
  nextTick(() => {
    if(editor.value) {
      editor.value.destroy()
    }
    editor.value = null
    document.getElementById('#' + prop + '-editor').innerHTML = ''
    editor.value = new E('#' + prop + '-editor')
    editor.value.config.height = 400
    editor.value.config.menus = [
      'head',
      'bold',
      'fontSize',
      'fontName',
      'italic',
      'underline',
      'strikeThrough',
      'indent',
      'lineHeight',
      'foreColor',
      'backColor',
      'link',
      'list',
      'justify',
      'quote',
      'emoticon',
      'image',
      'table',
      'code',
      'splitLine',
      'undo',
      'redo',
    ]
    editor.value.config.onblur = function (newHtml) {
      let vb = false
      if(!newHtml || JSON.stringify(newHtml) == '" "' || newHtml == '<p></p>' && newHtml == '<p><br></p>') {
        props.form[prop] = ''
        vb = false
      }else{
        props.form[prop] = newHtml
        props.item['defaultValue'] = newHtml
        vb = true
      }
      if(props.item.rules && props.item.rules.length > 0) {
        if(props.item.rules[0].required) {
          if(vb) {
            emit('validateHandle', {type: 'clear', item: props.item})
          }else{
            emit('validateHandle', {type: 'validate', item: props.item})
          }
        }
      }
      eventRequireHandle()
    }
    editor.value.config.onchange = function (newHtml) {
      let vb = false
      if(!newHtml || JSON.stringify(newHtml) == '" "' || newHtml == '<p></p>' && newHtml == '<p><br></p>') {
        props.form[prop] = ''
        vb = false
      }else{
        props.form[prop] = newHtml
        props.item['defaultValue'] = newHtml
        vb = true
      }
      if(props.item.rules && props.item.rules.length > 0) {
        if(props.item.rules[0].required) {
          if(vb) {
            emit('validateHandle', {type: 'clear', item: props.item})
          }else{
            emit('validateHandle', {type: 'validate', item: props.item})
          }
        }
      }
    }
    editor.value.config.uploadFileName = 'file'
    editor.value.config.uploadImgHeaders = upheader
    if(props.item.uploadHttp && props.item.uploadHttp.parameters) {
      editor.value.config.uploadImgParams = JSON.parse(JSON.stringify(props.item.uploadHttp.parameters))
    }else{
      editor.value.config.uploadImgParams = {
        module: '/jvs-ui/form/'
      }
    }
    editor.value.config.uploadImgHooks = {
      // 图片上传并返回了结果，图片插入已成功
      success: function(xhr) {
        console.log('success', xhr)
      },
      // 图片上传并返回了结果，但图片插入时出错了
      fail: function(xhr, editor, resData) {
        console.log('fail', resData)
      },
      // 上传图片出错，一般为 http 请求的错误
      error: function(xhr, editor, resData) {
        console.log('error', xhr, resData)
      },
      // 图片上传并返回了结果，想要自己把图片插入到编辑器中
      // 例如服务器端返回的不是 { errno: 0, data: [...] } 这种格式，可使用 customInsert
      customInsert: function(insertImgFn, result) {
        // insertImgFn 可把图片插入到编辑器，传入图片 src ，执行函数即可
        if(result.code == 0 && result.data && result.data.fileLink) {
          let url = result.data.fileLink.indexOf('?') ? result.data.fileLink.split('?')[0] : result.data.fileLink
          insertImgFn(url)
        }
      }
    }
    editor.value.create()
    if(props.item.defaultValue) {
      editor.value.txt.html(props.item.defaultValue)
      initHtml.value = props.item.defaultValue
    }
    if(props.form[prop]) {
      props.form[prop] = props.form[prop].replace(/&lt;/g, "<")
      props.form[prop] = props.form[prop].replace(/&gt;/g, ">")
      editor.value.txt.html(props.form[prop])
      initHtml.value = props.form[prop]
    }
    if(props.item.disabled) {
      editor.value.disable()
    }else{
      editor.value.enable()
    }
  })
}

// 按钮点击
function btnClick () {
  if(props.item.eventType == 'url') {
    if(props.item.openUrl) {
      $openUrl(formatUrl(props.item.openUrl), props.item.newWindowOpen ? '_blank' : '_self')
    }
  }else{
    eventRequireHandle()
  }
}

// 上传成功回调
function handleSuccess (res, file?, fileList?) {
  if(props.item.multipleUpload && fileList){
    if(fileList.every(it => (it.status == 'success' || it.isFinish == true))) {
      fileList.map(item => {
        if(item.response && item.response.code == 0) {
          let obj = {
            name: item.response.data.originalFileName || item.response.data.name,
            url: item.response.data.fileLink,
            fileName: item.response.data.fileName,
            bucketName: item.response.data.bucketName
          }
          if(props.item.parentKey && props.item.parentType == 'tableForm') {
            if(!tableFileList[props.tableRowAIndex]) {
              tableFileList[props.tableRowAIndex] = []
            }
            tableFileList[props.tableRowAIndex].push(obj)
            forms.value[props.item.prop] = tableFileList[props.tableRowAIndex]
          }else{
            props.item.fileList.push(obj)
            let temp = {
              key: props.item.prop,
              fileList: props.item.fileList
            }
            emit('file', temp)
            forms.value[props.item.prop] = props.item.fileList
          }
        }
      })
    }
    eventRequireHandle()
  }else{
    if(res && res.code == 0 && res.data) {
      let obj = {
        name: res.data.originalFileName,
        url: res.data.fileLink,
        fileName: res.data.fileName,
        bucketName: res.data.bucketName
      }
      if(props.item.parentKey && props.item.parentType == 'tableForm') {
        if(!tableFileList[props.tableRowAIndex]) {
          tableFileList[props.tableRowAIndex] = []
        }
        tableFileList[props.tableRowAIndex].push(obj)
        forms.value[props.item.prop] = tableFileList[props.tableRowAIndex]
      }else{
        props.item.fileList.push(obj)
        let temp = {
          key: props.item.prop,
          fileList: props.item.fileList
        }
        emit('file', temp)
        forms.value[props.item.prop] = props.item.fileList
      }
      eventRequireHandle()
    }
  }
  if(['imageUpload', 'fileUpload'].indexOf(props.item.type) > -1) {
    if(props.item.rules && props.item.rules.length > 0) {
      if(props.item.rules[0].required) {
        if(forms.value[props.item.prop] && forms.value[props.item.prop].length > 0) {
          emit('validateHandle', {type: 'clear', item: props.item})
        }else{
          emit('validateHandle', {type: 'validate', item: props.item})
        }
      }
    }
    emit('uploadChange') // 单独使用formitem组件
  }
}

function eventRequireHandle (op?) {
  if(op == 'reinit' && props.isView !== true) {
    emit('reInitData', props.item.prop, props.item.parentKey, props.tableRowAIndex)
  }
}

// 处理上传参数
function formatUploadData (item) {
  let obj = {}
  if(item.uploadHttp && item.uploadHttp.parameters) {
    obj = item.uploadHttp.parameters
  }else{
    obj = { module: '/jvs-ui/form/' }
  }
  return obj
}

// 删除
function handleRemove (file, fileList) {
  if(props.item.parentKey && props.item.parentType == 'tableForm') {
    for(let i in tableFileList[props.tableRowAIndex]) {
      if(tableFileList[props.tableRowAIndex][i].uid == file.uid) {
        tableFileList[props.tableRowAIndex].splice(Number(i), 1)
        forms.value[props.item.prop] = tableFileList[props.tableRowAIndex]
      }
    }
  }else{
    for(let i in props.item.fileList) {
      if(props.item.fileList[i].uid == file.uid) {
        props.item.fileList.splice(i, 1)
        let temp = {
          key: props.item.prop,
          fileList: props.item.fileList
        }
        emit('file', temp)
        forms.value[props.item.prop] = props.item.fileList
      }
    }
  }
}

function formatCascaderOptions (list) {
  for(let i in list) {
    if(list[i].extend && list[i].extend.uniqueName) {
      list[i].uniqueName = list[i].extend.uniqueName
    }
    if(list[i].children && list[i].children.length > 0) {
      formatCascaderOptions(list[i].children)
    }
  }
}

function getHtmlText (text) {
  if(text) {
    return text.replace(/[\n]/g, "<br/>")
  }else{
    return ''
  }
}

function reInitData (prop, parentKey, index, tableType) {
  emit('reInitData', prop, parentKey, index, tableType)
}

function getThousandthNumber(num) {
  let str = ''
  if(typeof num == 'number' || (typeof num == 'string' && num)) {
    str = num + ''
    str = str.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    if(str.includes('.') == false && props.item.precision > 0) {
      str += '.'
      for(let i=0; i < props.item.precision; i++) {
        str += '0'
      }
    }
  }
  return str
}

function showThoudsandthHandle (bool) {
  if(props.item.thoudsandthable && !props.item.disabled) {
    showThoudsandth.value = bool
  }
}

function blurValidate () {
  if(props.item.rules && props.item.rules.length > 0) {
    if(props.item.rules[0].required) {
      if(forms.value[props.item.prop] !== null && forms.value[props.item.prop] !== undefined && forms.value[props.item.prop] !== '') {
        emit('validateHandle', {type: 'clear', item: props.item})
      }else{
        emit('validateHandle', {type: 'validate', item: props.item})
      }
    }
  }
}

function getDisableexpress (type) {
  let bool = true
  let color = ""
  let bgcolor = ''
  if(props.item.disabled) {
    if(props.item.expressDisplay) {
      if(props.item.expressDisplay.backColor || props.item.expressDisplay.textcolor) {
        bool = false
        if(props.item.expressDisplay.backColor) {
          bgcolor = props.item.expressDisplay.backColor
        }
        color = props.item.expressDisplay.textcolor
      }
      if(props.item.expressDisplay.conditionControl){
        props.item.expressDisplay.conditionControl.filter(cit => {
          if(cit.value == props.form[props.item.prop]) {
            bgcolor = cit.bgcolor
            color = cit.color
            bool = false
          }
        })
      }
    }
  }
  if(type == 'show') {
    if(!props.form[props.item.prop]) {
      bool = true
    }
    return bool
  }
  if(type == 'color') {
    return color
  }
  if(type == 'bgcolor') {
    return bgcolor
  }
}

function formatUrl (url) {
  if(url.includes('?')) {
    if(!url.includes('jvsAppId')) {
      url += `&jvsAppId=${props.jvsAppId}`
    }
  }else{
    url += `?jvsAppId=${props.jvsAppId}`
  }
  return url
}

function handleExceed () {
  ElNotification({
    title: '提示',
    message: `最多可同时选择${props.item.limit} 个文件，请重新选择文件`,
    position: 'bottom-right',
    type: 'warning'
  })
}

function handleUploadRequest (file) {
  if(props.item.fileSize &&  file.file.size > (Number(props.item.fileSize) * 1024 * 1024)) {
    fileValidate.value = true
    if(uploadRefs['uploadFileBtn'+'_'+props.item.prop]) {
      if(props.item.limit < 2) {
        uploadRefs['uploadFileBtn'+'_'+props.item.prop].clearFiles()
      }else{
        for(let i in uploadRefs['uploadFileBtn'+'_'+props.item.prop].uploadFiles) {
          if(uploadRefs['uploadFileBtn'+'_'+props.item.prop].uploadFiles[i].uid == file.file.uid) {
            uploadRefs['uploadFileBtn'+'_'+props.item.prop].uploadFiles.splice(i, 1)
          }
        }
        setTimeout(()=> {
          fileValidate.value = false
        }, 500)
      }
    }
  }else{
    fileValidate.value = false
    fileList.value.push(file.file);
    fileListCopy.value.push(file.file)
  }
}

function dealUpload () {
  let newFileList = []
  fileListCopy.value.sort((a, b) => {
    return a.size - b.size
  })
  fileListCopy.value.forEach( (item) => {
    newFileList.push({
      name: item.name,
      percentage: 0
    })
  })
  if(fileListCopy.value.length > 0) {
    let channelBroad = new BroadcastChannel('upload-tree')
    channelBroad.postMessage({type:'uploadFile', data:{
      fileList: newFileList,
      isFinish: false
    }})
    startUploadList(newFileList, fileListCopy.value.shift())
  }
}

async function startUploadList(newFileList, item) {
  // 休眠
  function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms))
  }
    // 更新文件状态
  function changeFileStatus (fileList, isFinish) {
    let channelBroad = new BroadcastChannel('upload-tree')
    channelBroad.postMessage({type:'uploadFile',data:{
      fileList: fileList,
      isFinish: isFinish
    }})
  }
  // 上传失败
  function uploadError () {
    newFileList[fileUploadFailIndex.value].isFinish = true
    newFileList[fileUploadFailIndex.value].isfail = true
    fileUploadFailIndex.value ++
    startUploadList(newFileList, fileListCopy.value.shift())
    changeFileStatus(newFileList,false)
  }
  // 上传成功
  function uploadSuccess (response) {
    newFileList[fileUploadFailIndex.value].percentage = 100
    newFileList[fileUploadFailIndex.value].isfail = false
    newFileList[fileUploadFailIndex.value].isFinish = true
    if(response) {
      newFileList[fileUploadFailIndex.value].response = response
    }
    fileUploadFailIndex.value ++
    startUploadList(newFileList, fileListCopy.value.shift())
    changeFileStatus(newFileList,false)
  }
  await sleep(500)
  if(!item) {
    fileList.value = []
    fileUploadFailIndex.value = 0
    let channelBroad = new BroadcastChannel('upload-tree')
    channelBroad.postMessage({type:'uploadFile', data:{ newFileList }})
    changeFileStatus(newFileList, true)
    return
  }
  let fetchForm = new FormData()
  fetchForm.append("file", item)
  fetchForm.append("module", '/jvs-ui/form/')
}

function cascaderDataFilter (list, bool?) {
  for(let i in list) {
    if(list[i][props.item.datatype == 'dataModel' ? 'value' : (props.item.emitKey ? props.item.emitKey : 'value')] == forms.value[props.item.filterProp]) {
      list[i].disabled = true
      if(list[i].children && list[i].children.length > 0) {
        cascaderDataFilter(list[i].children, true)
      }
    }else{
      if(bool == true) {
        list[i].disabled = true
        if(list[i].children && list[i].children.length > 0) {
          cascaderDataFilter(list[i].children, true)
        }
      }else{
        if(list[i].children && list[i].children.length > 0) {
          cascaderDataFilter(list[i].children)
        }
      }
    }
  }
}

// 下载文件
function downloadFile (filename, content, attr?) {
  var elink = document.createElement('a')
  if(filename) {
    elink.download = filename
  }
  elink.style.display = 'none'
  var blob = new Blob([content], attr == 'type' ? {type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8'} : {})
  elink.href = URL.createObjectURL(blob)
  document.body.appendChild(elink)
  elink.click()
  document.body.removeChild(elink)
}

function previewFile (row) {
  let protocolhost = commonStore.kkfileUrl || ''
  if(protocolhost && row.url) {
    let view_url = `${protocolhost}/onlinePreview?url=` + encodeURIComponent(Base64.encode(decodeURIComponent(row.url)))
    $openUrl(view_url, '_blank')
  }
}

function getDicDataByExpress (list) {
  if(props.item.dataItemExpressable) {
    if(props.item.dataItmeBindProp) {
      let temp = []
      let formTemp = forms.value
      let bindDomNode = {}
      let bindkey = props.item.dataItmeBindProp
      if(typeof props.item.dataItmeBindProp == 'object' && props.item.dataItmeBindProp instanceof Array) {
        formTemp = props.originForm
        bindkey = props.item.dataItmeBindProp[props.item.dataItmeBindProp.length-1]
        if(props.item.dataItmeBindProp.length > 1) {
          getNodeDom(props.originOption.column, props.item.dataItmeBindProp, bindDomNode)
        }
      }
      if(bindDomNode && bindDomNode['prop']) {
        if(bindDomNode['parentDom'] && bindDomNode['parentDom'].length > 0) {
          if(bindDomNode['parentDom'][0].type == 'tab') {
            if(bindDomNode['parentDom'][0].detachData) {
              if(bindDomNode['parentDom'][1].prop) {
                formTemp = props.originForm[bindDomNode['parentDom'][1].prop]
              }
            }else{
              formTemp = forms.value
            }
          }
        }
      }
      list.filter(dit => {
        if(formTemp && formTemp[bindkey]) {
          if(typeof formTemp[bindkey] == 'object' && formTemp[bindkey] instanceof Array) {
            if(formTemp[bindkey].indexOf(dit[props.item.props ? props.item.props.value : 'value']) > -1) {
              temp.push(dit)
            }
          }else{
            if(dit[props.item.props ? props.item.props.value : 'value'] == formTemp[bindkey]) {
              temp.push(dit)
            }
          }
        }
      })
      selectOption.value = temp
    }
  }
}

function getNodeDom (list, keys, currentDomNode) {
  for(let i in list) {
    if(list[i].parentKey) {
      if((list[i].parentKey+'.'+list[i].prop) == keys.join('.')) {
        for(let k in list[i]) {
          currentDomNode[k] = list[i][k]
        }
      }
    }else{
      if(list[i].prop == keys.join('')) {
        for(let k in list[i]) {
          currentDomNode[k] = list[i][k]
        }
      }
    }
    if(list[i].type == 'tab' && list[i].column) {
      for(let j in list[i].column) {
        if(list[i].column[j] && list[i].column[j].length > 0) {
          getNodeDom(list[i].column[j], keys, currentDomNode)
        }
      }
    }
    if(list[i].type == 'tableForm' && list[i].tableColumn && list[i].tableColumn.length > 0) {
      getNodeDom(list[i].tableColumn, keys, currentDomNode)
    }
  }
}

function comTypeChange (row, index) {
  if(dynamicRefs.value['dynamicForm_'+props.item.prop]) {
    let tob = dynamicRefs.value['dynamicForm_'+props.item.prop].getobj(row)
    if(tob) {
      forms.value[props.item.prop][index] = tob
    }
  }
}

function colorChangeHandle (value, prop, form) {
  if(form) {
    form[prop] = value
  }
  formChange(value)
}

watch(() => props.item, (newVal, oldVal) => {
  if(props.delcomRandom && props.delcomRandom > -1) {
    if(newVal.datatype == 'option') {
      selectOption.value = newVal.dicData
    }
    return false
  }
  let bool = false
  if(newVal.dicUrl && oldVal.dicUrl && newVal.dicUrl == oldVal.dicUrl) {
    bool = true
  }
  if(newVal.url && oldVal.url && newVal.url == oldVal.url) {
    bool = true
  }
  if(newVal.datatype == 'rule' && newVal.optionHttp == oldVal.optionHttp) {
    bool = true
  }
  // 优化级联重复请求设置选项数据
  if(newVal.type == 'cascader') {
    if(newVal.datatype == 'system') {
      if(newVal.dictName == oldVal.dictName) {
        bool = true
      }
    }else{
      if(newVal.formId == oldVal.formId) {
        bool = true
      }
    }
  }
  let reInitBool = false
  if(['tab', 'tableForm'].indexOf(newVal.type) > -1) {
    reInitBool = true
  }else{
    if(newVal.parentKey && newVal.parentDom) {
      let newOb = Object.assign({}, newVal)
      let oldOb = Object.assign({}, oldVal)
      delete newOb.parentDom
      delete oldOb.parentDom
      reInitBool = (JSON.stringify(newOb) !== JSON.stringify(oldOb))
    }else{
      reInitBool = (JSON.stringify(newVal) !== JSON.stringify(oldVal))
    }
  }
  if(reInitBool) {
    initItem(bool, 'create')
  }
  // 下拉框 单选 自定义选择
  if(props.item.currVal) {
    if(props.item.multiple) {
      if(props.item.currVal instanceof Array !== true) {
        forms.value[props.item.prop] = []
      }else{
        forms.value[props.item.prop] = props.item.currVal
      }
    }else{
      if(props.item.currVal instanceof Array !== true) {
        forms.value[props.item.prop] = props.item.currVal
      }else{
        forms.value[props.item.prop] = ''

      }
    }
  }
  if(props.item.type == 'htmlEditor'){
    if(newVal.prop != oldVal.prop) {
      if(document.getElementById(props.item.prop + '-editor')) {
        document.getElementById(props.item.prop + '-editor').innerHTML = ''
      }
      if(editor.value) {
        editor.value.destroy()
      }
      initEditor(props.item.prop)
    }else{
      if(editor.value) {
        if(props.item.disabled) {
          editor.value.disable()
        }else{
          editor.value.enable()
        }
      }
    }
  }
})

watch(() => props.freshBoolean, (newVal, oldVal) => {
  if(props.item.type == 'htmlEditor') {
    document.getElementById(props.item.prop + '-editor').innerHTML = ''
    initEditor(props.item.prop)
  }else{
    document.getElementById(props.item.prop + '-editor').innerHTML = ''
    editor.value.destroy()
  }
})

watch(() => props.reinitFlag, (newVal, oldVal) => {
  if(newVal != -1) {
    initItem()
  }
})

watch(() => props.resetRadom, (newVal, oldVal) => {
  if(props.item.type == 'htmlEditor') {
    editor.value.txt.html(initHtml.value)
  }
  if(props.item.type == 'checkbox') {
    if(!forms.value[props.item.prop]) {
      forms.value[props.item.prop]= []
    }
  }
  if(props.item.type == 'reportTable') {
    initItem()
  }
})

watch(() => props.changeRandom, (newVal, oldVal) => {
  if(newVal > -1) {
    if((props.item.dataFilterable || props.item.dataItemExpressable) && !props.isView && props.item.type != 'cascader' && props.item.datatype != 'rule') {
      if(props.item.parentType == 'tableForm') {
        if(props.item.hasRelationPropList && props.item.hasRelationPropList.length > 0) {
          if(props.item.hasRelationPropList.indexOf(props.changeDomItem.parentKey ? `${props.changeDomItem.parentKey}.${props.changeDomItem.prop}` : props.changeDomItem.prop) > -1) {
            getSelectUrlData()
          }
        }
      }else{
        getSelectUrlData()
      }
    }
    if(props.item.datatype == 'rule' && !props.isView) {
      if(props.item.ruleOptionDom && props.item.ruleOptionDom.indexOf(props.changeDomItem.prop) > -1) {
        if(props.item.parentType == 'tableForm') {
          if(props.item.hasRelationPropList && props.item.hasRelationPropList.length > 0) {
            if(props.item.hasRelationPropList.indexOf(props.changeDomItem.parentKey ? `${props.changeDomItem.parentKey}.${props.changeDomItem.prop}` : props.changeDomItem.prop) > -1) {
              getSelectRuleData()
            }
          }
        }else{
          getSelectRuleData()
        }
      }
    }
    if(props.item.type == 'cascader') {
      let needRequire = false
      if(props.item.dataFilterGroupList && props.item.dataFilterGroupList.length > 0) {
        for(let i in props.item.dataFilterGroupList) {
          props.item.dataFilterGroupList[i].filter(dit => {
            if(dit.fieldKey == props.changeDomItem.prop) {
              needRequire = true
            }
          })
        }
      }
      if(props.item.dataFilterList && props.item.dataFilterList.length > 0) {
        props.item.dataFilterList.filter(dit => {
          if(dit.fieldKey == props.changeDomItem.prop) {
            needRequire = true
          }
        })
      }
      if(needRequire) {
        getCascaderData()
      }
    }
    if(props.item.datatype == 'option' && props.item.dataItmeBindProp == props.changeDomItem.prop) {
      getDicDataByExpress(props.item.dicData)
    }
  }
})

// 被嵌套表格自适应宽判断
watch(() => props.parentDomWidth, (newVal, oldVal) => {
  if(newVal && newVal > 0) {
    if(props.item.type == 'tableForm') {
      if(dynamicRefs.value[props.item.prop]) {
        let tw = 0
        props.item.tableColumn.filter(ti => {
          tw += ti.span * 8
        })
        let w = dynamicRefs.value[props.item.prop].offsetWidth
        if(w <= 0) {
          w = (newVal / 24) * (props.item.span)
        }
        if(tw < w - 50) {
          props.item.tableColumn.filter(ti => {
            ti.width = ''
          })
        }
      }
    }
  }
})

watch(() => fileList.value, (fileList, oldFileList) => {
  if(fileList.length) {
    nextTick(() => {
      dealUpload()
    })
  }
})

watch(() => props.dataTriggerFresh, (newVal, oldVal) => {
  if(newVal > -1) {
    if(props.item.type == 'htmlEditor') {
      if(forms.value[props.item.prop] && forms.value[props.item.prop] != '<p></p>') {
        editor.value.txt.html(forms.value[props.item.prop])
      }else{
        editor.value.txt.html('')
      }
    }
  }
})
</script>

<style lang="scss" scoped>
.bottom-add-button{
  margin-top: 8px;
  .button{
    width: 80px;
    display: flex;
    align-items: center;
    cursor: pointer;
    .icon{
      width: 16px;
      height: 16px;
      background: #1E6FFF;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 4px;
      svg{
        width: 12px;
        height: 12px;
        fill: #fff;
      }
    }
    span{
      font-family: Microsoft YaHei-Regular, Microsoft YaHei;
      font-weight: 400;
      font-size: 14px;
      color: #1E6FFF;
      line-height: 18px;
    }
  } 
}
.delete-icon-button{
  width: 16px;
  height: 16px!important;
  background: #36B452;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  cursor: pointer;
  .border-line{
    width: 10px;
    height: 2px;
    background: #fff;
    border-radius: 2px;
  }
}
</style>
<style lang="scss">
$jvsFormItemHeight: 36px;
.loading-back {
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  display: flex;
  justify-content: center;
  align-items: center;
}
.form-list-upload-img {
  .el-upload--picture-card {
    display: inline-block;
  }
}
.form-list-upload-img-none {
  .el-upload--picture-card {
    display: none;
  }
  .el-upload-list {
    height: 148px!important;
  }
}
.form-list-upload-file {
  width: 100%;
  .el-upload--text {
    display: inline-block;
  }
}
.form-list-upload-file-none {
  width: 100%;
  .el-upload--text {
    display: none;
  }
}
.icon-select-tool{
  display: flex;
  flex-wrap: wrap;
  height: 200px;
  scrollbar-width: none; /* firefox */
  -ms-overflow-style: none; /* IE 10+ */
  overflow-x: hidden;
  overflow-y: auto;
  padding-left: 15px;
  i{
    margin: 10px;
    display: block;
    width: 20px;
    height: 20px;
    line-height: 20px;
    cursor: pointer;
  }
  i:hover{
    color: #409EFF;
  }
  .icon {
    fill: currentColor;
    overflow: hidden;
    cursor: pointer;
    width: 30px;
    height: 30px;
    margin-bottom: 10px;
    margin-left: 10px;
  }
}
.icon-select-tool::-webkit-scrollbar {
  display: none; /* Chrome Safari */
}
.icon-select-tool-position{
  position: absolute;
  height: 158px;
  top: 45px;
  margin: 0;
  z-index: 9;
}
.jvs-form-item{
  min-height: $jvsFormItemHeight;
  position: relative;
  .el-slider, p, .el-input-number, .el-select, .el-date-editor, .el-tabs, .el-cascader, .user-info-list{
    flex: 1;
  }
  .el-input-number{
    .el-input__inner{
      text-align: left;
    }
  }
  .show-thoudsandth-number{
    .el-input__inner{
      color: transparent!important;
    }
  }
  .input-number-Thousandth{
    position: absolute;
    left: 16px;
    font-size: 12px;
  }
  .input-number-Thousandth-disabled{
    left: 0;
    font-size: 14px;
  }
  // 隐藏文本框前置图标
  .el-input--prefix{
    .el-input__prefix{
      display: none;
    }
  }
  .color-select{
    .el-color-picker{
      width: 40px;
      height: 24px;
      .el-color-picker__trigger{
        width: 100%;
        padding: 0;
        border-radius: 4px;
        overflow: hidden;
        .el-color-picker__color{
          border-width: 0;
          border-radius: 4px;
        }
      }
    }
  }
  // 隐藏禁用状态下placeholder
  .el-input.is-disabled{
    font-size: 12px;
    .el-input__inner::-webkit-input-placeholder{
      color: transparent;
    }
    .el-input__inner::-moz-input-placeholder{
      color: transparent;
    }
    .el-input__inner::-ms-input-placeholder{
      color: transparent;
    }
  }
  // p文字
  .form-item-p{
    span{
      display: inline-block;
      box-sizing: border-box;
      overflow: hidden;
      position: relative;
      padding-left: 10px;
      height: $jvsFormItemHeight;
      i,b{
        font-weight: normal;
        font-style: normal;
      }
      i{
        width: 4px;
        border-radius: 2px;
        height: 20px;
        background-color: rgb(52, 113, 255);
        display: inline-block;
        line-height: $jvsFormItemHeight;
        position: absolute;
        top: 4px;
        left: 0;
      }
    }
  }
  .table-form{
    overflow: hidden;
    .el-card {
      border-width: 0;
    }
    .jvs-table{
      .el-table__header-wrapper {
        margin-top: 0;
      }
      .el-table__body-wrapper {
        .el-table__empty-block {
          border-top: 0;
        }
      }
      .cell {
        > div {
          width: 100%;
        }
        .el-radio-group,
        .el-checkbox-group {
          width: 100%;
          div {
            display: flex;
            flex-wrap: wrap;
            .el-radio,
            .el-checkbox {
              min-width: 50%;
              margin-right: 0;
              text-align: left;
            }
          }
        }
        .demo-dynamic {
          .el-form-item {
            padding: 0;
            .el-input.is-disabled {
              .el-input__inner {
                padding-right: 0;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: pre;
              }
            }
          }
        }
      }
      .el-table__fixed,
      .el-table__fixed-right {
        margin-top: 0;
      }
      .el-table.el-table--border {
        .el-table__body-wrapper {
          .el-table__empty-block {
            border-top: 0;
            border-left: 0;
          }
        }
      }
    }
    .table-body-box{
      padding: 0;
      .el-table__body-wrapper {
        min-height: 300px;
        height: auto !important;
        tr td{
        .cell.el-tooltip{
            >span{
              display: inline-block;
              width: 100%;
            }
          }
        }
      }
      .el-table__body-wrapper::-webkit-scrollbar {
        height: 8px;
      }
      .el-table__body-wrapper::-webkit-scrollbar-thumb {
        border-radius: 20px;
      }
      .el-table__fixed-right {
        padding-bottom: 4px;
      }
    }
    .el-table__row {
      .el-upload-list {
        .el-upload-list__item {
          .el-icon-close-tip {
            display: none !important;
          }
        }
      }
    }
  }
  .table-form-noteditable {
    padding: 10px;
    .jvs-table {
      .jvs-table-titleTop {
        box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
      }
      .table-body-box {
        padding: 0;
      }
    }
  }
  .show-form {
    .table-form {
      .table-body-box {
        .el-table__body-wrapper {
          .el-table__empty-block {
            display: none !important;
          }
        }
      }
    }
  }
  .user-info-list{
    .user-info-input-div{
      height: $jvsFormItemHeight;
    }
  }
  .w-e-text-container{
    table{
      td{
        *{
          margin: 0;
          padding: 0;
        }
      }
    }
  }
}
.jvs-form-item-disabled{
  .el-input-number.input-number-hide{
    display: none;
  }
  .el-upload-list{
    .el-upload-list__item{
      .el-upload-list__item-status-label{
        display: none!important;
      }
    }
    .el-upload-list__item:hover{
      .el-icon-close{
        display: none!important;
      }
    }
  }
  .w-e-toolbar{
    display: none;
  }
  .w-e-text-container{
    width: 100%;
    border: 0!important;
    height: auto!important;
  }
}
</style>
