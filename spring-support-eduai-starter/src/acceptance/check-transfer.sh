#!/bin/sh
# 传输校验：check-transfer.sh <file> <expected_md5> <expected_size>
# 成功标记只在比对通过时打印（否则等于没校验）
f=$1; em=$2; es=$3
s=$(stat -c%s "$f" 2>/dev/null)
m=$(md5sum "$f" 2>/dev/null | cut -c1-32)
if [ "$s" = "$es" ] && [ "$m" = "$em" ]; then
  echo "TRANSFER_VERIFIED size=$s md5=$m"
else
  echo "TRANSFER_MISMATCH size=$s(expected=$es) md5=$m(expected=$em)"
  exit 9
fi