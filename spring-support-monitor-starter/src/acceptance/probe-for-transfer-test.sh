#!/bin/bash
# ASCII-only. Harmless probe script used to exercise remote-run.ps1 end to end.
# Prints its own path, size and md5 so the caller can confirm the bytes that
# actually arrived on the remote side.
set +u
echo "  [probe] running from: $0"
echo "  [probe] bytes=$(wc -c < "$0")"
echo "  [probe] md5=$(md5sum "$0" | cut -d' ' -f1)"
echo "  [probe] this line proves the script really executed"
exit 0