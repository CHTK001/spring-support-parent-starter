import {createRouter, createWebHistory} from 'vue-router'


const routes = [{
    path: '/home/:data?',
    name: "home",
    component: () => import("@/components/home/home.vue"),
    meta: {title: "dashboard", icon: "homepage", affix: true},

}]

const router = createRouter({
    history: createWebHistory(),
    routes
})

//绑定路由钩子处理路由事件
router.beforeEach((to, from, next) => {
    //非标签页子页面，正常导航
    if(!to.meta.tagsPath) {
        next()
    } else {
        //标签页子页面，导航到标签页
        const query = Object.assign({}, to.query);
        //标签子页面的地址作为参数传入标签页
        query.path = to.path;
        next({
            //导航到标签页
            path: to.meta.tagsPath,
            query
        })
    }
})

export default router;