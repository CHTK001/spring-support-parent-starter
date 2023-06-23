import {createRouter, createWebHistory} from "vue-router"
import Home from "@/view/home.vue"


const routes = [{
    path: "/home",
    name: 'home',
    component: Home
}, {
    path: "/oss",
    name: "oss",
    component: () => import('@/view/oss.vue')
}, {
    path: "/test",
    name: "test",
    component: () => import('@/view/test.vue')
}, {
    path: "/test2",
    name: "test2",
    component: () => import('@/page/base/base.vue')
}];


const router = createRouter({
    routes,
    history: createWebHistory(),
});
export default router;