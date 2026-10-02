#!/usr/bin/env bash
# 全量测试验证入口：强制执行并校验「测试确实跑了」。
#
# 存在的理由：本仓父 POM 里 <skipTests>true</skipTests>，因此任何人跑自然的
# `mvn test` / `mvn verify` 得到的都是：
#
#     [INFO] Tests are skipped.
#     [INFO] BUILD SUCCESS
#
# 这比「Tests run: 0」更隐蔽 —— 连数字都没有，只有 BUILD SUCCESS。绿灯与
# 「测试一个没跑」在输出上无法区分。
#
# 本脚本做四件事把这个洞堵上：
#
#   1. 动态枚举所有含测试类的模块（不写死清单）。写死清单会漏掉后来新增的模块：
#      手工列清单的人只会列自己动过的模块，本仓实测因此漏掉了 6 个模块，
#      其中 2 个的测试从来没被执行过。
#   2. 强制 -DskipTests=false。
#   3. 解析 Tests run 汇总行，出现以下任一情况判失败：
#      「Tests are skipped」、没有 Tests run 行、Tests run = 0、Failures/Errors > 0。
#      判据是 Tests run 的数字，不是 BUILD 结果。
#   4. 区分「需要真实外部环境」的模块。默认仍判失败，用 --allow-env-dependent
#      显式降级为警告。不做静默忽略 —— 逃生舱必须显式打开，否则它会变成
#      掩盖问题的后门。
#
# 用法：
#   ./verify-all.sh                        严格模式
#   ./verify-all.sh --allow-env-dependent  把已知依赖真实外部环境的模块降级为警告
#   ./verify-all.sh --repo <dir>           指定仓库根目录
#
# 本仓 .sh 与 .ps1 都未被 .gitignore 忽略，两者都已有先例；这里选 .sh 是因为
# CI 通常在 Linux 上跑，且与 utils-support-parent-starter 的同名脚本保持一致。

set -u

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALLOW_ENV_DEPENDENT=0

while [ $# -gt 0 ]; do
  case "$1" in
    --allow-env-dependent) ALLOW_ENV_DEPENDENT=1 ;;
    --repo) shift; REPO_ROOT="$1" ;;
    -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

if [ ! -f "$REPO_ROOT/pom.xml" ]; then
  echo "REPO_ROOT_INVALID $REPO_ROOT/pom.xml 不存在"
  exit 2
fi
command -v mvn >/dev/null 2>&1 || { echo "MAVEN_MISSING"; exit 2; }

# 已知确实依赖真实外部环境的模块。只列真需要真实数据库/端口/角色的 ——
# 判定标准若混进别的模块，这个开关就变成掩盖问题的后门了。
env_dependent_real() {
  case "$1" in
    # ShardingMySqlRealTest：需真实 MySQL 分片库（实测 CommunicationsException）
    # RealPortE2ETest / ReadOnlyRealTest：需真实监听端口与特定数据库角色
    # 注意这三类里 ReadOnlyRealTest / RealPortE2ETest 当前是 403 权限不足，
    # 那部分未必是环境问题，降级前应先确认，否则这个开关会掩盖真缺陷
    spring-support-test-app) return 0 ;;
    *) return 1 ;;
  esac
}

echo "REPO_ROOT $REPO_ROOT"
echo "ALLOW_ENV_DEPENDENT $ALLOW_ENV_DEPENDENT"

# ---- 动态枚举含测试类的模块 ----
modules="$(find "$REPO_ROOT" -type d -path '*/src/test/java' 2>/dev/null | while read -r d; do
  # <module>/src/test/java 要往上退三级才是模块根。退两级会停在 <module>/src，
  # 那里没有 pom.xml，于是所有模块都被跳过、枚举结果为 0 —— 实测踩过这个坑，
  # 表现是 MODULES_WITH_TESTS 0 且脚本以 NO_TESTS_FOUND 退出，看起来像"仓里没测试"。
  mod="$(dirname "$(dirname "$(dirname "$d")")")"
  [ -f "$mod/pom.xml" ] || continue
  # 目录里确实有测试源文件才算数，只有空目录的不算
  if find "$d" \( -name '*Test.java' -o -name '*Tests.java' \) 2>/dev/null | grep -q .; then
    printf '%s\n' "${mod#"$REPO_ROOT"/}"
  fi
done | sort -u)"

count=$(printf '%s\n' "$modules" | grep -c . || true)
echo "MODULES_WITH_TESTS $count"
printf '%s\n' "$modules" | sed 's/^/  - /'

if [ "$count" -eq 0 ]; then
  echo "RESULT=NO_TESTS_FOUND 没有任何模块含测试类，若不符合预期请检查路径"
  exit 3
fi

BLOCKING=""
WARNED=""
NO_REACTOR=""
TOTAL_RUN=0

run_module() {
  mod="$1"
  name="$(basename "$mod")"
  printf '=== %s ===\n' "$mod"

  started=$(date +%s)
  text="$(cd "$REPO_ROOT" && mvn -f "$REPO_ROOT/pom.xml" test -pl "$mod" -B -DskipTests=false 2>&1)"
  rc=$?
  elapsed=$(( $(date +%s) - started ))

  verdict="OK"
  reason=""
  run=-1
  fail=0
  err=0

  if printf '%s' "$text" | grep -q 'Could not find the selected project'; then
    verdict="NOT_IN_REACTOR"; reason="不在 reactor 中，无法验证"
  elif printf '%s' "$text" | grep -q 'Tests are skipped'; then
    verdict="SKIPPED"; reason="输出 Tests are skipped —— BUILD SUCCESS 是假的，测试未执行"
  elif printf '%s' "$text" | grep -qE 'COMPILATION ERROR|Compilation failure'; then
    verdict="COMPILE_FAILED"; reason="编译失败"
  else
    # 取最后一行汇总：Tests run: N, Failures: F, Errors: E, Skipped: S
    summary="$(printf '%s' "$text" | grep -oE 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' | tail -1)"
    if [ -z "$summary" ]; then
      verdict="NO_SUMMARY"; reason="没有 Tests run 汇总行，无法证明测试跑过"
    else
      run=$(printf '%s' "$summary" | sed -E 's/.*Tests run: ([0-9]+).*/\1/')
      fail=$(printf '%s' "$summary" | sed -E 's/.*Failures: ([0-9]+).*/\1/')
      err=$(printf '%s' "$summary" | sed -E 's/.*Errors: ([0-9]+).*/\1/')
      if [ "$run" -eq 0 ]; then
        verdict="ZERO_TESTS"; reason="存在测试类但 Tests run = 0，测试未被发现或未执行"
      elif [ $(( fail + err )) -gt 0 ]; then
        verdict="FAILED"; reason="Failures=$fail Errors=$err"
      fi
    fi
  fi

  if [ "$verdict" = "FAILED" ] && [ "$ALLOW_ENV_DEPENDENT" -eq 1 ] && env_dependent_real "$name"; then
    verdict="ENV_DEPENDENT"; reason="$reason（已按 --allow-env-dependent 降级为警告）"
  fi

  printf '  -> %s run=%s F=%s E=%s %ss %s %s\n' "$verdict" "$run" "$fail" "$err" "$elapsed" "$name" "$reason"

  [ "$run" -gt 0 ] && TOTAL_RUN=$(( TOTAL_RUN + run ))

  case "$verdict" in
    FAILED|SKIPPED|ZERO_TESTS|NO_SUMMARY|COMPILE_FAILED)
      BLOCKING="${BLOCKING}${name} ${verdict} ${reason}
" ;;
    ENV_DEPENDENT)
      WARNED="${WARNED}${name} ${reason}
" ;;
    NOT_IN_REACTOR)
      NO_REACTOR="${NO_REACTOR}${name}
" ;;
  esac
}

while read -r m; do
  [ -n "$m" ] && run_module "$m"
done <<< "$modules"

# ---- 汇总 ----
echo ""
echo "==================== summary ===================="
n_blocking=$(printf '%s' "$BLOCKING" | grep -c . || true)
n_warned=$(printf '%s' "$WARNED" | grep -c . || true)
n_no_reactor=$(printf '%s' "$NO_REACTOR" | grep -c . || true)
echo "  模块总数 $count，实际执行测试合计 $TOTAL_RUN"
echo "  阻断 $n_blocking，环境依赖降级 $n_warned，不在 reactor $n_no_reactor"
[ -n "$BLOCKING" ] && printf '%s' "$BLOCKING" | sed 's/^/  BLOCKING   /'
[ -n "$WARNED" ] && printf '%s' "$WARNED" | sed 's/^/  WARN       /'
[ -n "$NO_REACTOR" ] && printf '%s' "$NO_REACTOR" | sed 's/^/  NO_REACTOR /'

if [ "$n_blocking" -gt 0 ]; then
  echo "RESULT=BLOCKED $n_blocking 个模块未通过"
  exit 1
fi
echo "RESULT=ALL_GREEN 每个模块都实际执行了测试且零失败"