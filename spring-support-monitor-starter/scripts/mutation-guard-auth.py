"""
敏感性对照门禁：变异 ProductionAuthGuard 的账号格式校验，证明新增 spec 不是恒绿的。

Sensitivity control for ProductionAuthGuard's account-format validation.

Same discipline as the frontend guard (see AGENTS.md): a guard test that has
never been shown to go red proves nothing. Two mutations:

  1. detectMalformedAccounts always returns empty  -> the format assertions die
  2. detectUnusableConfigs stops calling it        -> only the integration
     assertion dies, proving that test is not just re-testing #1

Files are handled as bytes with an md5-verified restore; Chinese comments are
never routed through PowerShell.
"""

import hashlib
import io
import os
import re
import subprocess
import sys
import tempfile

MODULE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRING = os.path.dirname(os.path.dirname(MODULE))
GUARD = os.path.join(
    SPRING,
    "spring-support-monitor-starter",
    "src",
    "main",
    "java",
    "com",
    "chua",
    "starter",
    "monitor",
    "support",
    "config",
    "ProductionAuthGuard.java",
)

MVN = r"D:\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd"

MUTATIONS = [
    (
        "detectMalformedAccounts 恒返回空（退回只判空）",
        """        List<String> reasons = new ArrayList<>(0);
        if (null == staticUserConfig || staticUserConfig.isBlank()) {
            return reasons;
        }""",
        """        List<String> reasons = new ArrayList<>(0);
        if (true) {
            return reasons;
        }
        if (null == staticUserConfig || staticUserConfig.isBlank()) {
            return reasons;
        }""",
    ),
    (
        "detectUnusableConfigs 不再调用格式校验（整合点形同虚设）",
        """            reasons.addAll(detectMalformedAccounts(staticUserConfig));""",
        """            // 变异：整合点被摘掉""",
    ),
]


def run_tests():
    cmd = [
        MVN,
        "-f",
        os.path.join(SPRING, "pom.xml"),
        "clean",
        "test",
        "-pl",
        "spring-support-monitor-starter",
        "-B",
        "-o",
        "-DskipTests=false",
        "-Dtest=ProductionAuthGuardAccountFormatTest",
        "-DfailIfNoSpecifiedTests=false",
    ]
    proc = subprocess.run(
        cmd,
        cwd=SPRING,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=3600,
    )
    out = (proc.stdout or "") + (proc.stderr or "")
    return proc.returncode == 0, out


def main():
    lines = []
    undetected = 0
    restore_failed = 0

    for label, old, new in MUTATIONS:
        with open(GUARD, "rb") as fh:
            original = fh.read()
        text = original.decode("utf-8")
        if old not in text:
            lines.append("%-58s MUTATION_NOT_APPLIED" % label[:58])
            undetected += 1
            continue
        mutated = text.replace(old, new, 1)
        try:
            with io.open(GUARD, "w", encoding="utf-8", newline="") as fh:
                fh.write(mutated)
            ok, out = run_tests()
            failed = 0
            for m in re.finditer(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+)", out):
                failed += int(m.group(2)) + int(m.group(3))
            if ok:
                undetected += 1
                lines.append("%-58s NOT_DETECTED  (failures=%d)" % (label[:58], failed))
            else:
                lines.append("%-58s DETECTED      (failures=%d)" % (label[:58], failed))
        finally:
            with open(GUARD, "wb") as fh:
                fh.write(original)
            with open(GUARD, "rb") as fh:
                after = fh.read()
            ok_restore = hashlib.md5(after).hexdigest() == hashlib.md5(original).hexdigest()
            if not ok_restore:
                restore_failed += 1
            lines.append("%-58s %s" % ("  restore: " + label[:49], "RESTORED_OK" if ok_restore else "RESTORE_FAILED"))

    outp = os.path.join(MODULE, "target", "auth-mutation-result.txt")
    os.makedirs(os.path.dirname(outp), exist_ok=True)
    with io.open(outp, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("MUTATION SENSITIVITY RESULTS (ProductionAuthGuard)\n")
        fh.write("=" * 78 + "\n")
        for l in lines:
            fh.write("  " + l + "\n")
        fh.write("-" * 78 + "\n")
        fh.write("  mutations total  = %d\n" % len(MUTATIONS))
        fh.write("  NOT detected     = %d\n" % undetected)
        fh.write("  restore failures = %d\n" % restore_failed)
        fh.write("=" * 78 + "\n")
        fh.write("VERDICT: %s\n" % ("SENSITIVITY_CONTROL_FAILED" if (undetected or restore_failed)
                                   else "EVERY_MUTATION_DETECTED"))
    print("undetected=%d restore_failed=%d" % (undetected, restore_failed))
    sys.exit(1 if (undetected or restore_failed) else 0)


if __name__ == "__main__":
    main()