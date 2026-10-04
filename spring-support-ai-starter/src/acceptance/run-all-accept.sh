#!/bin/bash
# 服务端总运行器：语法自检 → 后端两套 → 行为复核两套 → 收尾状态
# 放在服务端执行（避免在 Windows 侧拼接命令时被 zsh/pwsh 的引号层破坏）。
cd /tmp || exit 1
echo "########## 0. 语法自检 ##########"
for f in pg fg vsf ve cfs; do
  if bash -n "/tmp/$f.sh" 2>/tmp/_syn.err; then
    echo "  $f.sh 语法 OK"
  else
    echo "  $f.sh 语法 FAIL: $(cat /tmp/_syn.err | head -2)"
  fi
done

echo
echo "########## 1. 可达性（不允许在不可达状态下给结论）##########"
C=$(curl -s -f -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8787/v1/captcha)
echo "  /v1/captcha = $C"
echo "  md5=$(md5sum /opt/ai-starter/app.jar | cut -c1-8)  is-active=$(systemctl is-active ai-starter)"
if [ "$C" != "200" ]; then echo "  [ABORT] 服务不可达，直接停止（不产出任何绿/红结论）"; exit 3; fi

echo
echo "########## 2. prodgrade（以图搜图）##########"
AI_ACC=admin bash /tmp/pg.sh; echo "  prodgrade exit=$?"

echo
echo "########## 3. facegrade（人脸，11 组）##########"
bash /tmp/fg.sh; echo "  facegrade exit=$?"

echo
echo "########## 4. verify 修复的正/反例对照 ##########"
bash /tmp/vvc.sh; echo "  contrast exit=$?"

echo
echo "########## 5. verify-secfix（越权敏感性对照）##########"
bash /tmp/vsf.sh; echo "  secfix exit=$?"

echo
echo "########## 6. verify-effective（改完立即生效）##########"
bash /tmp/ve.sh; echo "  effective exit=$?"

echo
echo "########## 7. 收尾状态 ##########"
bash /tmp/cfs.sh

echo
echo "########## 8. 关键结论汇总 ##########"
echo "  被测 md5 = $(md5sum /opt/ai-starter/app.jar | cut -c1-8)"
systemctl is-active ai-starter | sed 's/^/  is-active=/'
echo RUN_ALL_DONE
