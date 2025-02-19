// directives/scrollToLoad.ts
import { DirectiveBinding, Ref } from 'vue';

export default {
  mounted(el: HTMLElement, binding: DirectiveBinding<(() => void) | null>) {
    const callback = binding.value as () => void;

    el.addEventListener('scroll', () => {
      if (
        el.scrollTop + el.clientHeight >= el.scrollHeight
      ) {
        callback();
      }
    });
  },
  unbind(el: HTMLElement, binding: DirectiveBinding<(() => void) | null>) {
    el.removeEventListener('scroll', binding.value as EventListener);
  },
};