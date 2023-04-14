<template>
  <NavBar />
  <router-view></router-view>
</template>

<script>
import NavBar from './components/NavBar.vue'
import "bootstrap/dist/css/bootstrap.min.css"
import "bootstrap/dist/js/bootstrap"
// import $ from 'jquery'
export default{
  components:{
    NavBar
  },
  setup(){
    // $.ajax({
    //   url:"http://127.0.0.1:3000/user/account/token/",
    //   type:"post",
    //   data:{
    //     username:'lzy',
    //     password:'plzy',
    //   },
    //   success(resp){
    //     console.log(resp);
    //   },
    //   error(resp){
    //     console.log(resp);
    //   }
    // });

    // $.ajax({
    //   url: "http://127.0.0.1:3000/user/account/info/",
    //   type: "get",
    //   headers: {
    //     Authorization: "Bearer " + "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIwMzM5MGY1MTkwOWE0MTgwOWMzMmI5NjkwODczYmY5ZCIsInN1YiI6IjEiLCJpc3MiOiJzZyIsImlhdCI6MTY1OTk0ODQyMiwiZXhwIjoxNjYxMTU4MDIyfQ.fCbEE1JTG2Shfhtd21qJ2KsCuM854fAskayywNi3OIo"
    //   },
    //   success(resp){
    //     console.log(resp);
    //   },
    //   error(resp){
    //     console.log(resp);
    //   }
    // })

    // $.ajax({
    //   url: "http://127.0.0.1:3000/user/account/register/",
    //   type: "post",
    //   data:{
    //     username: "lzy2",
    //     password: "123",
    //     confirmedPassword: "123",
    //   },
    //   success(resp){
    //     console.log(resp);
    //   },
    //   error(resp){
    //     console.log(resp);
    //   }
    // })
  }

}
</script>
<style>
body{
  background-image: url("assets/images/background3.jpg");
  background-size: cover;
}
</style>
