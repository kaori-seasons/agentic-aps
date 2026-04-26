<template>
  <div class="error403">
    <div class="error403-card">
      <div class="code">403</div>
      <div class="title">无权限访问当前页面</div>
      <div class="desc">请联系管理员为当前账号分配所需权限，或返回可访问页面继续操作。</div>
      <div class="actions">
        <el-button @click="backPage">返回上一页</el-button>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "Error403",
  methods: {
    backPage() {
      this.$router.go(-1);
    },
    goHome() {
      this.$router.push({
        path: "/"
      });
    }
  }
};
</script>

<style lang="scss" scoped>
.error403{
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #f7f9fc 0%, #eef3fb 100%);
  .error403-card{
    width: 520px;
    padding: 40px 44px;
    background: rgba(255,255,255,0.92);
    border: 1px solid rgba(30,111,255,0.08);
    box-shadow: 0 20px 60px rgba(54,59,76,0.08);
    border-radius: 20px;
    .code{
      font-size: 56px;
      font-weight: 700;
      line-height: 1;
      color: #1E6FFF;
    }
    .title{
      margin-top: 16px;
      font-size: 24px;
      font-weight: 700;
      color: #363B4C;
    }
    .desc{
      margin-top: 12px;
      font-size: 14px;
      line-height: 24px;
      color: #6F7588;
    }
    .actions{
      margin-top: 28px;
      display: flex;
      gap: 12px;
    }
  }
}
</style>
