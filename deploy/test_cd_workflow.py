from pathlib import Path
import unittest
import yaml


class CdTests(unittest.TestCase):
    def test_only_main_publishes_after_tests_and_proposes_pr(self):
        root = Path(__file__).resolve().parents[1]
        workflow = yaml.load((root / ".github/workflows/publish-image.yml").read_text(),
                             Loader=yaml.BaseLoader)
        self.assertEqual(workflow["on"]["push"]["branches"], ["main"])
        publish = workflow["jobs"]["publish"]
        self.assertEqual(publish["needs"], "validate")
        self.assertIn("github.event_name != 'pull_request'", publish["if"])
        self.assertIn("github.ref == 'refs/heads/main'", publish["if"])
        self.assertEqual(publish["permissions"], {"contents": "read", "packages": "write"})
        steps = workflow["jobs"]["update-gitops"]["steps"]
        checkout = next(step for step in steps if "uses" in step)
        self.assertEqual(checkout["with"]["repository"], "Astro-Inter/astro-gitops")
        proposal = next(step for step in steps if step.get("id") == "propose")
        self.assertIn("apps/astro-api/overlays/academy/kustomization.yaml", proposal["run"])
        self.assertIn("HEAD:refs/heads/ci/SCRUM-393-atualizar-api-", proposal["run"])
        self.assertNotIn("HEAD:main", proposal["run"])
        self.assertNotIn("--force", proposal["run"])
        self.assertIn("gh pr create", steps[-1]["run"])
        self.assertNotIn("gh pr merge", steps[-1]["run"])
        self.assertEqual(steps[-1]["env"]["GH_TOKEN"], "${{ secrets.ASTRO_GITOPS_TOKEN }}")
