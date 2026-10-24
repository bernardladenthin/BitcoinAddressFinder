# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Publication gate and transport invariants; no signing key or remote writes."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

from buildcheck.publication import prepare, verify, snapshot_pom
from buildcheck import workflow
from buildcheck.tests.helpers import REPO


class PublicationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / '.github').mkdir()
        (self.root / '.github/release-assets.json').write_text('{"schema":1}')
        self.pom = '<project><modelVersion>4.0.0</modelVersion><groupId>local.test</groupId><artifactId>demo</artifactId><version>1-SNAPSHOT</version></project>'
        (self.root / 'pom.xml').write_text(self.pom)
        self.assets = self.root / 'assets'
        self.assets.mkdir()
        self.stage = self.root / 'target/central-deferred/local/test/demo/1-SNAPSHOT'
        self.stage.mkdir(parents=True)
        for suffix in ['.pom', '.jar', '-sources.jar', '-javadoc.jar']:
            name = 'demo-1-SNAPSHOT' + suffix
            for extra in ['', '.asc']:
                data = self.pom if suffix == '.pom' and not extra else name + extra
                (self.assets / (name + extra)).write_text(data)
                (self.stage / (name + extra)).write_text(data)
        self.package = self.root / 'package'
        self.signatures = patch('buildcheck.publication.subprocess.run')
        self.run = self.signatures.start()
        self.addCleanup(self.signatures.stop)
        quiet = patch('builtins.print')
        quiet.start()
        self.addCleanup(quiet.stop)

    def freeze(self):
        prepare(self.root, self.assets, self.package)

    def test_freezes_once_and_verifies_every_signature(self):
        self.freeze()
        self.assertEqual(self.run.call_count, 4)
        self.assertEqual(verify(self.package)['channel'], 'snapshot')
        (self.assets / 'demo-1-SNAPSHOT.jar').write_text('later rebuild')
        self.assertNotEqual((self.package / 'github/demo-1-SNAPSHOT.jar').read_text(), 'later rebuild')
        verify(self.package)

    def test_missing_signature_or_wrong_central_bytes_block_both_publishers(self):
        sig = self.assets / 'demo-1-SNAPSHOT.jar.asc'
        sig.unlink()
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            self.freeze()
        self.assertFalse(self.package.exists())
        sig.write_text('signature')
        with self.assertRaisesRegex(ValueError, 'bytes differ'):
            self.freeze()
        self.assertFalse(self.package.exists())

    def test_invalid_signature_blocks_package_creation(self):
        import subprocess
        self.run.side_effect = subprocess.CalledProcessError(1, 'gpg')
        with self.assertRaises(subprocess.CalledProcessError):
            self.freeze()
        self.assertFalse(self.package.exists())

    def test_transport_tampering_and_extra_files_are_rejected(self):
        self.freeze()
        unexpected = self.package / 'unexpected.txt'
        unexpected.write_text('extra')
        with self.assertRaisesRegex(ValueError, 'membership'):
            verify(self.package)
        unexpected.unlink()
        (self.package / 'github/demo-1-SNAPSHOT.jar').write_text('tampered')
        with self.assertRaisesRegex(ValueError, 'bytes changed'):
            verify(self.package)

    def test_snapshot_transfer_retains_signature_extensions(self):
        self.freeze()
        target = self.root / 'transfer.xml'
        snapshot_pom(self.package, target, 'file:///unused-local-diagnostic')
        xml = ET.parse(target).getroot()
        config = xml.find('.//{*}configuration')
        self.assertIn('jar.asc', config.findtext('{*}types'))
        self.assertIn('pom.asc', config.findtext('{*}types'))
        self.assertEqual(xml.findtext('.//{*}phase'), 'validate')
        self.assertEqual(config.findtext('{*}generatePom'), 'false')

    def test_pom_only_component_transfers_signature_and_pom_together(self):
        pom = self.pom.replace('</project>', '<packaging>pom</packaging></project>')
        (self.root / 'pom.xml').write_text(pom)
        for directory in (self.assets, self.stage):
            for path in directory.iterdir():
                if '.jar' in path.name:
                    path.unlink()
            (directory / 'demo-1-SNAPSHOT.pom').write_text(pom)
        self.freeze()
        target = self.root / 'pom-transfer.xml'
        snapshot_pom(self.package, target)
        config = ET.parse(target).getroot().find('.//{*}configuration')
        self.assertEqual(config.findtext('{*}packaging'), 'pom.asc')
        self.assertTrue(config.findtext('{*}file').endswith('.pom.asc'))
        self.assertTrue(config.findtext('{*}pomFile').endswith('.pom'))
        self.assertIsNone(config.find('{*}files'))

    def test_publishers_depend_only_on_the_same_checked_handoff(self):
        jobs = workflow.parse((Path(REPO) / '.github/workflows/publish.yml').read_text())
        action = (Path(REPO) / '.github/actions/finalize-publication/action.yml').read_text()
        self.assertIn('steps.ready.outputs.complete', action)
        order = ['id: collect', 'id: preflight', 'id: handoff', 'id: ready']
        self.assertEqual([action.index(item) for item in order], sorted(action.index(item) for item in order))
        self.assertIn('if-no-files-found: error', action)
        self.assertNotIn('always()', action)
        self.assertNotIn('continue-on-error', action)
        for channel in ['snapshot', 'release']:
            prep = jobs['publish-' + channel]
            self.assertIn('steps.finalize.outputs.complete', prep.text)
            self.assertIn('uses: ./.github/actions/finalize-publication', prep.text)
            self.assertIn('channel: ' + channel, prep.text)
            self.assertNotIn('publication.py prepare', prep.text)
            self.assertNotIn('attestations: write', prep.text)
            self.assertNotIn('id-token: write', prep.text)
            self.assertIn('-DskipUpload=true deploy', prep.text)
            self.assertNotIn('publishAllPublicationsToCentralSnapshotsRepository', prep.text)
            for consumer in ['central', 'github', 'attest']:
                job = jobs[consumer + '-' + channel]
                self.assertEqual(job.needs, ['publish-' + channel])
                self.assertIn("needs.publish-" + channel + ".outputs.assets_complete == 'true'", job.text)
                self.assertIn('digest-mismatch: error', job.text)
            self.assertIn('publication.py upload publication-package', jobs['central-' + channel].text)
            self.assertIn('publication.py verify publication-package', jobs['github-' + channel].text)
            attest = jobs['attest-' + channel].text
            self.assertLess(attest.index('publication.py verify'), attest.index('uses: actions/attest-build-provenance@'))
            self.assertIn('subject-path: publication-package/github/*', attest)
            self.assertIn('id-token: write', attest)
            self.assertIn('attestations: write', attest)
