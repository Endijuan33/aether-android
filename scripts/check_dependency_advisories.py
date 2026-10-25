#!/usr/bin/env python3
"""Query OSV for the resolved release graph; network failures fail the check.

Only public Maven coordinates leave the development/CI machine. This script
does not run in the APK. No result is a guarantee that a dependency is secure.
"""
import json
import pathlib
import sys
import urllib.error
import urllib.request


def main():
    source = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else
                          "app/build/reports/release-dependencies.txt")
    coordinates = sorted(set(source.read_text().splitlines()))
    if not coordinates:
        raise ValueError("Resolved dependency inventory is empty")
    queries = []
    for coordinate in coordinates:
        group, artifact, version = coordinate.split(":")
        queries.append({"package": {"ecosystem": "Maven", "name": f"{group}:{artifact}"},
                        "version": version})
    findings = []
    for start in range(0, len(queries), 100):
        batch = queries[start:start + 100]
        request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
                                         data=json.dumps({"queries": batch}).encode(),
                                         headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=45) as response:
            results = json.load(response)["results"]
        if len(results) != len(batch):
            raise ValueError("Incomplete OSV response")
        for coordinate, result in zip(coordinates[start:start + 100], results):
            findings.append({"coordinate": coordinate,
                             "advisories": [v["id"] for v in result.get("vulns", [])]})
    report = source.parent / "dependency-advisories.json"
    report.write_text(json.dumps({"source": "OSV", "dependencies": findings}, indent=2) + "\n")
    affected = [f for f in findings if f["advisories"]]
    print(f"OSV checked {len(coordinates)} resolved Maven coordinates; {len(affected)} have matching advisories.")
    for finding in affected:
        print(f"{finding['coordinate']}: {', '.join(finding['advisories'])}")
    return 1 if affected else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, urllib.error.URLError) as error:
        print(f"Advisory check failed: {error}", file=sys.stderr)
        sys.exit(2)
