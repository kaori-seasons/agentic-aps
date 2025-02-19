// 在你的Vue3项目中创建一个 directives 文件夹，并在其中创建 lazy.js
import { onMounted, onUnmounted, ref } from 'vue'

export default {
  mounted(el:HTMLElement, binding:any) {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          const src = binding.value;
          el.src = src;
          observer.unobserve(el);
        }
      },
      { threshold: 0.1 } // 可调整阈值以控制何时开始加载
    )

    observer.observe(el)
  },
  unmounted(el:HTMLElement) {
    // 清理观察者
    const observer = el.__vueLazyIntersectionObserver__
    if (observer) {
      observer.unobserve(el);
    }
  },
}