"""Prueba el flujo de despliegue sin conectarse a Docker ni a EC2."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/deploy-ec2.sh"
IMAGE = "transportesgt/fleet-service:" + "a" * 40


class DeployTest(unittest.TestCase):
    def run_deploy(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "compose.yml").write_text("name: test\nservices: {}\n")
            (root / "image.tar.gz").touch()
            docker = root / "docker"
            docker.write_text('''#!/usr/bin/env bash
set -eu
printf '%s\\n' "$*" >> "$TEST_ROOT/commands"
if [[ "$*" == *'ps -aq'* ]]; then
  if [[ "$MODE" != first ]]; then printf 'container-id\\n'; fi
elif [[ "$1" == inspect ]]; then
  printf 'transportesgt/fleet-service:previous\\n'
elif [[ "$1" == load && "$MODE" == load-failure ]]; then
  exit 1
elif [[ "$*" == *'up -d'* && "$MODE" == up-failure ]]; then
  if ! test -f "$TEST_ROOT/failed"; then
    touch "$TEST_ROOT/failed"
    exit 1
  fi
fi
''')
            curl = root / "curl"
            curl.write_text('''#!/usr/bin/env bash
if [[ "$MODE" == health-failure || "$MODE" == first ]]; then
  exit 1
fi
''')
            sleep = root / "sleep"
            sleep.write_text("#!/usr/bin/env bash\nexit 0\n")
            for executable in (docker, curl, sleep):
                executable.chmod(0o755)
            env = dict(os.environ, PATH=f"{root}:" + os.environ["PATH"],
                       TEST_ROOT=str(root), MODE=mode)
            result = subprocess.run(
                ["bash", str(SCRIPT), str(root / "compose.yml"),
                 "fleet-service", IMAGE, "http://localhost/health",
                 str(root / "image.tar.gz")],
                env=env, capture_output=True, text=True, timeout=10,
            )
            override = root / ".deploy/fleet-service.yml"
            return (result.returncode,
                    override.read_text() if override.exists() else "",
                    (root / "commands").read_text())

    def test_success_updates_only_requested_service(self):
        code, override, commands = self.run_deploy("success")
        self.assertEqual(code, 0)
        self.assertIn(IMAGE, override)
        self.assertEqual(commands.count("up -d"), 1)
        self.assertIn("--no-deps --no-build --pull never fleet-service", commands)

    def test_health_failure_rolls_back(self):
        code, override, commands = self.run_deploy("health-failure")
        self.assertNotEqual(code, 0)
        self.assertIn(":previous", override)
        self.assertEqual(commands.count("up -d"), 2)

    def test_compose_failure_rolls_back(self):
        code, override, commands = self.run_deploy("up-failure")
        self.assertNotEqual(code, 0)
        self.assertIn(":previous", override)
        self.assertEqual(commands.count("up -d"), 2)

    def test_first_deploy_failure_has_no_rollback(self):
        code, _, commands = self.run_deploy("first")
        self.assertNotEqual(code, 0)
        self.assertEqual(commands.count("up -d"), 1)

    def test_load_failure_does_not_replace_service(self):
        code, override, commands = self.run_deploy("load-failure")
        self.assertNotEqual(code, 0)
        self.assertEqual(override, "")
        self.assertNotIn("up -d", commands)


if __name__ == "__main__":
    unittest.main()
