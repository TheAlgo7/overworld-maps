"""Runs the Desktop Head Unit with a controllable console.

Commands are appended as lines to C:/tmp/dhu-cmd.txt (for example "tap 400 600" or
"screenshot C:/tmp/dhu.png"); this script forwards new lines to the DHU's stdin, so the
DHU can be driven without moving the mouse or stealing window focus. DHU output goes to
C:/tmp/dhu-out.txt.
"""
import os
import subprocess
import sys
import threading
import time

DHU_DIR = os.path.join(os.environ["LOCALAPPDATA"], r"Android\Sdk\extras\google\auto")
CMD = r"C:\tmp\dhu-cmd.txt"
OUT = r"C:\tmp\dhu-out.txt"

open(CMD, "w").close()
out = open(OUT, "w", buffering=1, encoding="utf-8", errors="replace")
proc = subprocess.Popen(
    [os.path.join(DHU_DIR, "desktop-head-unit.exe"), "-c", r"config\tata_curvv.ini"],
    cwd=DHU_DIR,
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    text=True,
    bufsize=1,
)


def pump():
    for line in proc.stdout:
        out.write(line)


threading.Thread(target=pump, daemon=True).start()

seen = 0
while proc.poll() is None:
    with open(CMD, encoding="utf-8") as f:
        lines = f.read().splitlines()
    for line in lines[seen:]:
        out.write(f">>> {line}\n")
        proc.stdin.write(line + "\n")
        proc.stdin.flush()
    seen = len(lines)
    time.sleep(0.2)
out.write(f"DHU exited {proc.returncode}\n")
sys.exit(0)
