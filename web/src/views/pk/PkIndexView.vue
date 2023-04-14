<template>
   <PlayGround v-if="$store.state.pk.status === 'playing'"/>
   <MatchGround v-if="$store.state.pk.status === 'matching'"/>
   <ResultBoard v-if="$store.state.pk.loser != 'none'"/> 
</template>

<script>
import PlayGround from '../../components/PlayGround.vue'
import MatchGround from '../../components/MatchGround.vue'
import ResultBoard from '../../components/ResultBoard.vue'
import { onMounted, onUnmounted } from 'vue'
import { useStore } from 'vuex'

export default{
    components: {
        PlayGround,
        MatchGround,
        ResultBoard,
    },
    setup(){
        const store=useStore();
        const socketUrl=`ws://localhost:3000/websocket/${store.state.user.token}/`;//user.id改为user.token 这是为了防止用户作弊,将userid放在后台判断,创建了工具类Authentication

        store.commit("updateLoser","none");
        store.commit("updateIsRecord",false);


        let socket=null;
        onMounted(() => {//当前组件被挂载的时候也就是页面被打开的时候
            store.commit("updateOpponent",{
                username: "我的对手",
                photo: "https://cdn.acwing.com/media/article/image/2022/08/09/1_1db2488f17-anonymous.png",

            })

            socket = new WebSocket(socketUrl);

            socket.onopen = () => {//前端自带onopen函数，接收信息后自定义函数内容
                console.log("connected!");
                store.commit("updateSocket",socket);//更新socket写入全局变量里
                //每次点击匹配，占用一个websocket线程，取消匹配后下次匹配需要刷新界面
            }

            socket.onmessage = msg => {//传来一个msg
                const data =JSON.parse(msg.data);
                if(data.event === "start-matching"){//匹配成功
                    store.commit("updateOpponent",{
                        username: data.opponent_username,
                        photo:data.opponent_photo,
                    });
                    setTimeout(() => {
                        store.commit("updateStatus","playing");
                    },200);//匹配成功后2秒进去
                    store.commit("updateGame",data.game);
                }else if (data.event === "move") {
                    console.log(data);
                    const game = store.state.pk.gameObject;
                    const [snake0, snake1] = game.snakes;//
                    snake0.set_direction(data.a_direction);
                    snake1.set_direction(data.b_direction);
                }else if(data.event==="result"){
                    console.log(data);
                    const game = store.state.pk.gameObject;
                    const [snake0,snake1] = game.snakes;
                    if(data.loser==="all" || data.loser==="A"){
                        snake0.status="die";
                    }
                    if(data.loser==="all" || data.loser==="B"){
                        snake1.status="die";
                    }
                    store.commit("updateLoser",data.loser);
                }
            }

            socket.onclose = () => {
                console.log("disconnected!")
            }
        });

        onUnmounted(() => {
            socket.close();
            store.commit("updateStatus","matching");//点别的页面再次回来重新匹配
        })
    }
}
</script>

<style scoped>

</style>