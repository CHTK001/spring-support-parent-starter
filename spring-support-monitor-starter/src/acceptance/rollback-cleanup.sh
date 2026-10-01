#!/bin/bash
# ASCII-only. Rollback jar retention.
#
# Ordering key is the TIMESTAMP IN THE FILENAME, never mtime. Reason: these copies used to
# be made with `cp -p`, which preserves the SOURCE mtime, so every rollback jar inherited the
# same mtime as the jar it was copied from. Under such ties `ls -1t` order is arbitrary, and
# "keep the newest N" silently becomes "delete a random one" -- it can delete the newest jar
# while keeping an ancient one. Sort by filename and the problem disappears.
#
# Direction is asserted explicitly: `sort -r` is NEW -> OLD, and we delete from
# `tail -n +$((KEEP+1))` onwards, i.e. everything past the KEEP newest. Writing plain
# `sort` there would invert and delete the NEWEST instead, and the file count would stay
# the same -- so a count-only check cannot detect the inversion. That is why this script
# prints what it is about to delete and why the ordering is covered by a fixture test.
#
# Usage: rollback-cleanup.sh <dir> [keep]
set +u
DIR=${1:-/home/kali/ms-accept/jars}
KEEP=${2:-3}

if [ ! -d "$DIR" ]; then
  echo "  dir not found: $DIR"
  exit 2
fi

echo "== rollback retention in $DIR (keep newest $KEEP by FILENAME timestamp) =="

# New -> old, by filename. Filenames are rollback-YYYYmmdd-HHMMSS.jar so lexical sort is
# chronological sort. grep -v keeps the live jar out of this list entirely.
LIST=$(ls -1 "$DIR" 2>/dev/null | grep -E '^rollback-[0-9]{8}-[0-9]{6}\.jar$' | sort -r)

if [ -z "$LIST" ]; then
  echo "  no rollback jars matching the expected name pattern; nothing to do"
  echo "  (refusing to guess: a different naming scheme would need its own ordering key)"
  exit 0
fi

TOTAL=$(echo "$LIST" | wc -l | tr -d ' ')
echo "  found $TOTAL rollback jars"
echo "  --- order (index 1 = newest kept) ---"
echo "$LIST" | head -n "$KEEP" | nl -w2 -s'. ' | sed 's/^/    KEEP /'
DROP=$(echo "$LIST" | tail -n +$((KEEP + 1)))
DN=$(echo "$DROP" | grep -c . || true)
if [ "$DN" -eq 0 ]; then
  echo "  nothing to delete (keep=$KEEP >= total=$TOTAL)"
  echo "RESULT=NOTHING_TO_DELETE"
  exit 0
fi
echo "  --- would DELETE (oldest first) ---"
echo "$DROP" | nl -w2 -s'. ' | sed 's/^/    DEL  /'

# Guard against the inverted-direction mistake: the first entry we are about to delete must be
# OLDER than the first entry we are keeping. If that does not hold, the ordering is wrong and we
# refuse to delete anything.
#
# NOTE the condition. An earlier version had the branches inverted: it aborted when the kept entry
# WAS newer (i.e. on correct input) and waved through the inverted sort. Only the fixture test's
# sensitivity control -- flip the sort direction -- exposed that, because the functional checks
# still showed the right KEEP/DEL lists and the abort merely looked like extra caution.
NEWEST_KEPT=$(echo "$LIST" | head -n 1)
OLDEST_DROPPED=$(echo "$DROP" | head -n 1)
if [[ "$NEWEST_KEPT" > "$OLDEST_DROPPED" ]]; then
  echo "  direction check OK: keeping '$NEWEST_KEPT' which is newer than oldest drop '$OLDEST_DROPPED'"
else
  echo "  ABORT: direction check failed (kept '$NEWEST_KEPT' is not newer than dropped '$OLDEST_DROPPED')"
  echo "RESULT=ABORTED_DIRECTION"
  exit 9
fi

# Dry run unless DRYRUN is unset to 0
if [ "${DRYRUN:-1}" = "1" ]; then
  echo "  DRY RUN (set DRYRUN=0 to actually delete)"
  echo "RESULT=DRYRUN total=$TOTAL keep=$KEEP delete=$DN"
  exit 0
fi

echo "$DROP" | while read -r f; do
  [ -z "$f" ] && continue
  rm -f "$DIR/$f" && echo "    deleted $f"
done
echo "RESULT=DELETED delete=$DN"
exit 0