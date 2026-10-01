#!/bin/bash
# ASCII-only. Fixture test for rollback-cleanup.sh.
#
# The point of the fixture: make every rollback jar share ONE mtime. That is exactly the
# state `cp -p` produced in the real directory. If the cleanup sorted by mtime it would be
# untestable here (all ties) and dangerous in production (random deletion). Sorting by the
# timestamp embedded in the filename must give a deterministic, correct answer anyway.
#
# Also includes a sensitivity control for the direction guard: a mutated copy with the sort
# direction flipped must be REFUSED, otherwise the guard is decoration.
set +u
CLEANUP=/home/kali/ms-accept/rollback-cleanup.sh
FIX=/tmp/rbtest
PASS=0
FAIL=0

ok()   { PASS=$((PASS+1)); echo "  [ok]   $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }

rm -rf "$FIX" && mkdir -p "$FIX" || exit 1

# 5 rollback jars, ALL with an identical mtime, plus a non-rollback file that must be ignored
for t in 20260930-122419 20260930-180000 20261001-074811 20261001-120000 20261001-235959; do
  printf 'jar-%s\n' "$t" > "$FIX/rollback-$t.jar"
done
printf 'not a rollback\n' > "$FIX/other-file.jar"
printf 'live\n' > "$FIX/spring-support-monitor-starter-4.0.0.42-exec.jar"
touch -t 202601010000 "$FIX"/rollback-*.jar "$FIX"/other-file.jar "$FIX"/spring-support-monitor-starter-4.0.0.42-exec.jar

echo "== fixture: all rollback jars must share ONE mtime (that is the cp -p state) =="
echo "  distinct mtimes among rollback jars = $(ls -1 --full-time "$FIX"/rollback-*.jar | awk '{print $6, $7}' | sort -u | wc -l | tr -d ' ') (expect 1)"
echo "  files = $(ls -1 "$FIX" | wc -l | tr -d ' ')"
ls -1 --full-time "$FIX"/rollback-*.jar | awk '{print "    " $6, $7, $9}'

echo
echo "== CASE 1: keep 3, expect KEEP = 235959 / 120000 / 074811, DEL = 180000 / 122419 =="
OUT=$(bash "$CLEANUP" "$FIX" 3 2>&1)
echo "$OUT" | sed 's/^/    /'
echo "$OUT" | grep -qE 'KEEP +1\. rollback-20261001-235959\.jar' && ok "newest kept first" || bad "newest kept first"
echo "$OUT" | grep -qE 'KEEP +3\. rollback-20261001-074811\.jar' && ok "third kept is 074811" || bad "third kept is 074811"
echo "$OUT" | grep -qE 'DEL +1\. rollback-20260930-180000\.jar' && ok "first delete is 180000" || bad "first delete is 180000"
echo "$OUT" | grep -qE 'DEL +2\. rollback-20260930-122419\.jar' && ok "second delete is 122419 (oldest)" || bad "second delete is 122419"
echo "$OUT" | grep -q 'direction check OK' && ok "direction check passed" || bad "direction check passed"
echo "$OUT" | grep -q 'RESULT=DRYRUN' && ok "dry run by default" || bad "dry run by default"
echo "$OUT" | grep -q 'other-file.jar' && bad "must ignore non-rollback names" || ok "ignores non-rollback names"
echo "$OUT" | grep -q 'exec.jar' && bad "must not touch the live jar" || ok "does not touch the live jar"
echo "  files still present after dry run = $(ls -1 "$FIX" | wc -l | tr -d ' ') (must be 7)"

echo
echo "== CASE 2: keep 5 (>= total) -> nothing to delete =="
OUT2=$(bash "$CLEANUP" "$FIX" 5 2>&1)
echo "$OUT2" | grep -q 'RESULT=NOTHING_TO_DELETE' && ok "keep>=total reports nothing to delete" || bad "keep>=total"

echo
echo "== CASE 3: keep 1 -> proposes deleting 4, still newest kept =="
OUT3=$(bash "$CLEANUP" "$FIX" 1 2>&1)
echo "$OUT3" | grep -qE 'KEEP +1\. rollback-20261001-235959\.jar' && ok "keeps the newest" || bad "keeps the newest"
echo "$OUT3" | grep -q 'delete=4' && ok "would delete 4" || bad "would delete 4"

echo
echo "== CASE 4: empty dir -> refuses to guess =="
rm -rf "$FIX" && mkdir -p "$FIX"
OUT4=$(bash "$CLEANUP" "$FIX" 3 2>&1)
echo "$OUT4" | grep -q 'refusing to guess' && ok "refuses to guess on unknown naming" || bad "refuses to guess"

echo
echo "== CASE 5 (sensitivity control): flipped sort direction must be REFUSED =="
rm -rf "$FIX" && mkdir -p "$FIX"
for t in 20260930-122419 20261001-074811 20261001-235959; do
  printf 'x\n' > "$FIX/rollback-$t.jar"
done
touch -t 202601010000 "$FIX"/rollback-*.jar
# NOTE the sed delimiter: the pattern itself contains a pipe, so using | as the sed delimiter
# breaks (the first pipe inside the pattern would be taken as the delimiter and the substitution
# silently does nothing). Use # instead. The earlier run showed this as "1 remaining sort -r".
sed 's#| sort -r)#| sort)#' "$CLEANUP" > /tmp/rb-mutated.sh
# Count only the real pipeline occurrence, not the prose in comments (an earlier version
# grepped plain 'sort -r', which also matched the comment mentioning it, so it reported
# "1 remaining" even when the mutation had in fact been applied).
echo "  mutation applied: $(grep -c '| sort -r)' /tmp/rb-mutated.sh) remaining pipeline sort -r (expect 0)"
echo "  comment mentions sort -r: $(grep -c 'sort -r' /tmp/rb-mutated.sh) (informational only)"
OUT5=$(bash /tmp/rb-mutated.sh "$FIX" 1 2>&1)
echo "$OUT5" | sed 's/^/    /'
echo "$OUT5" | grep -q 'RESULT=ABORTED_DIRECTION' && ok "flipped direction is refused" || bad "flipped direction is refused"

rm -rf "$FIX" /tmp/rb-mutated.sh
echo
echo "== summary: pass=$PASS fail=$FAIL =="
[ "$FAIL" -eq 0 ] || exit 1
echo DONE