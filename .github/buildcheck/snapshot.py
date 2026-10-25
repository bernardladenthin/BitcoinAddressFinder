# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0
"""Update the rolling snapshot without replacing a newer publish or deleting the release.

The workflow serializes this entire operation with a job concurrency group. The ancestry/run
checks also reject an older build which finishes after a newer build has already published.
An incomplete collection changes nothing: no upload, no tag or notes update, no prune.
"""

import json
import re
import subprocess
from pathlib import Path

from .releaseassets import signature_gaps

# GitHub renames release assets with other characters; a renamed asset would be pruned as obsolete.
ASSET_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*")


class GitHub:
    def command(self, *args):
        subprocess.run(["gh", *map(str, args)], check=True)

    def api(self, method, endpoint, payload=None, optional=False):
        args = ["gh", "api", "--method", method, endpoint]
        if payload is not None:
            args += ["--input", "-"]
        result = subprocess.run(args, input=json.dumps(payload) if payload is not None else None,
                                text=True, capture_output=True)
        if result.returncode:
            if optional and "HTTP 404" in result.stderr:
                return None
            raise RuntimeError(f"GitHub {method} {endpoint}: {result.stderr.strip()}")
        return json.loads(result.stdout) if result.stdout.strip() else None


def publish(repository, sha, run_id, attempt, assets_dir, complete, github=None,
            *, tag="snapshot", title="Snapshot (latest)"):
    """Return False for a superseded run; otherwise upload and update the rolling release."""
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repository) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("invalid repository or published commit")
    if not re.fullmatch(r"[\w][\w.-]*", tag) or ".." in tag or tag.endswith((".", ".lock")):
        raise ValueError("invalid rolling release tag")
    if re.match(r"v\d", tag):
        raise ValueError("a versioned release is never a rolling snapshot: " + tag)
    run_id, attempt = int(run_id), int(attempt)
    if run_id < 1 or attempt < 1:
        raise ValueError("run ID and attempt must be positive")
    files = sorted(Path(assets_dir).iterdir())
    if not files or any(not path.is_file() for path in files):
        raise ValueError("refusing to update snapshot without a nonempty, flat asset directory")
    artifacts = [path for path in files if not path.name.endswith(".asc")]
    if not artifacts:
        raise ValueError("refusing to update snapshot with signatures but no artifacts")
    names = {path.name for path in files}
    renamed = sorted(name for name in names if not ASSET_NAME.fullmatch(name))
    if renamed:
        raise ValueError("GitHub would rename release assets: " + ", ".join(renamed))
    missing, errors = signature_gaps(assets_dir)
    if not complete or missing:
        raise ValueError("refusing to update snapshot from an incomplete collection; existing "
                         "snapshot metadata and assets were retained" + "".join("; " + e for e in errors))
    gh = github or GitHub()
    repo = f"repos/{repository}"
    ref = gh.api("GET", f"{repo}/git/ref/tags/{tag}", optional=True)
    release = gh.api("GET", f"{repo}/releases/tags/{tag}", optional=True)
    published = ref["object"] if ref else None
    # Annotated tags point to tag objects, not directly to commits.
    for _ in range(8):
        if not published or published["type"] != "tag":
            break
        published = gh.api("GET", f"{repo}/git/tags/{published['sha']}")["object"]
    if published and published["type"] != "commit":
        raise ValueError("snapshot tag does not resolve to a commit")
    if published and published["sha"] != sha:
        comparison = gh.api("GET", f"{repo}/compare/{published['sha']}...{sha}")
        if comparison["status"] == "behind":
            print("Snapshot already names a newer commit; skipping this older build.")
            return False
        if comparison["status"] not in ("ahead", "identical"):
            raise ValueError("refusing to replace snapshot with a divergent commit")
    if release:
        marker = re.search(r"<!-- snapshot-publish (\d+) (\d+) ([0-9a-f]{40}) -->",
                           release.get("body") or "")
        if marker and marker[3] == sha and (int(marker[1]), int(marker[2])) > (run_id, attempt):
            print("Snapshot already names a newer run/attempt of this commit; skipping.")
            return False

    body = (f"Snapshot from [{sha[:8]}](https://github.com/{repository}/commit/{sha}).\n\n"
            f"[Publish run {run_id}, attempt {attempt}]"
            f"(https://github.com/{repository}/actions/runs/{run_id}/attempts/{attempt}).\n\n"
            f"<!-- snapshot-publish {run_id} {attempt} {sha} -->")
    if not release:
        if not ref:
            ref = gh.api("POST", f"{repo}/git/refs", {"ref": f"refs/tags/{tag}", "sha": sha})
        release = gh.api("POST", f"{repo}/releases", {
            "tag_name": tag, "target_commitish": sha, "name": title,
            "prerelease": True, "body": body
        })
    gh.command("release", "upload", tag, *files, "--repo", repository, "--clobber")

    if ref:
        gh.api("PATCH", f"{repo}/git/refs/tags/{tag}", {"sha": sha, "force": True})
    else:
        gh.api("POST", f"{repo}/git/refs", {"ref": f"refs/tags/{tag}", "sha": sha})
    gh.api("PATCH", f"{repo}/releases/{release['id']}", {
        "target_commitish": sha, "name": title, "prerelease": True, "body": body
    })

    # Read every page before deleting: deleting while paginating could skip shifted entries.
    assets = []
    page = 1
    while True:
        batch = gh.api("GET", f"{repo}/releases/{release['id']}/assets?per_page=100&page={page}")
        assets.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    failures = []
    for asset in assets:
        if asset["name"] not in names:
            try:
                print(f"Removing obsolete snapshot asset: {asset['name']}")
                gh.api("DELETE", f"{repo}/releases/assets/{asset['id']}", optional=True)
            except RuntimeError as error:
                failures.append(str(error))
    if failures:
        raise RuntimeError("; ".join(failures))
    return True
