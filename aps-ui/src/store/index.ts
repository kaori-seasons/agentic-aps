import { createPinia } from "pinia";
import { shareStorePlugin } from '@/store/shareStore'
const pinia = createPinia();
pinia.use(shareStorePlugin)
export default pinia;