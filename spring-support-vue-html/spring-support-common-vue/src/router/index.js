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
    path: "/arrange",
    name: "arrange",
    component: () => import('@/view/arrange.vue')
},  {
    path: "/arrange-dag",
    name: "arrange-dag",
    component: () => import('@/view/arrange-dag.vue')
},{
    path: "/test2",
    name: "test2",
    component: () => import('@/page/base/base.vue')
},{
    path: "/drag",
    name: "drag",
    component: () => import('@/page/drag/drag.vue')
}];


const router = createRouter({
    routes,
    history: createWebHistory(),
});
export default router;