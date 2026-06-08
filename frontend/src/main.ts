/**
 * 前端应用入口文件
 * 负责初始化Vue应用并挂载到DOM元素
 */
// 导入Vue的createApp函数用于创建应用实例
import { createApp } from 'vue'
// 导入全局样式文件
import './style.css'
// 导入根组件App.vue
import App from './App.vue'

// 创建Vue应用实例并挂载到id为app的DOM元素上
createApp(App).mount('#app')