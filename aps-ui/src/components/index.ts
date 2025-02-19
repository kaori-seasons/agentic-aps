import jvsForm from './basic-assembly/form.vue'
import jvsTable from './basic-assembly/table.vue'
import jvsButton from './basic-assembly/button.vue'
import jvsColorPicker from './colorPicker/index.vue'
// 注册全局容器


function installComponent (app:any) {
  app.component('jvs-form', jvsForm)
  app.component('jvs-table', jvsTable)
  app.component('jvs-button', jvsButton)
  app.component('jvs-colorpicker', jvsColorPicker)
}

export default installComponent