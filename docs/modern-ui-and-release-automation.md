# eBlocker Modern UI and Release Automation Plan

This branch starts the full eBlocker UI modernization as a safe, testable migration instead of a risky one-shot rewrite.

## Goals

1. Build a completely new UI on a current frontend stack.
2. Keep the existing AngularJS UI available until each feature has a verified replacement.
3. Generate Debian packages automatically in GitHub Actions.
4. Create/update GitHub releases with the generated `.deb` artifacts and SHA256 checksums.

## Current implementation on this branch

- Adds `eblocker-ui-next/` as a parallel modern UI package.
- Uses React, TypeScript and Vite.
- Packages the modern UI preview into `eblocker-ui-next_..._all.deb` under `/opt/eblocker-icap/htdocs-next`.
- Adds `.github/workflows/release-debs.yml`.
- Pins all newly introduced GitHub Actions by full commit SHA.

## Why a parallel UI first?

The existing UI contains several independent AngularJS applications (`settings`, `dashboard`, `controlbar`, `setup`, `advice`, `sample`) and the backend currently exposes 366 manually wired routes. Replacing all screens in one commit would very likely break appliance behavior. The safe approach is:

1. Build the new shell.
2. Add typed `/api/v1/...` contracts feature by feature.
3. Replace one user-facing area at a time.
4. Keep explicit legacy fallbacks until each area is verified.
5. Only then switch the production package root from AngularJS to the modern UI.

## Migration order

1. System status, diagnostics, update and backup.
2. Devices, pause state and profile assignment.
3. DNS, resolvers, local DNS and filtering.
4. SSL, trusted apps and trusted domains.
5. Parental control.
6. VPN/Mobile with OpenVPN compatibility and WireGuard-first future UI.
7. Advanced legacy screens.

## Release automation behavior

The new workflow runs on:

- tag pushes matching `v*`
- manual `workflow_dispatch`

It performs:

1. checkout
2. Java 11 setup
3. Maven package build with `-DskipTests=true`
4. collection of every `.deb` under Maven `target` directories
5. SHA256 generation
6. artifact upload
7. GitHub Release create/update when a tag is present or a manual `release_tag` input is supplied

## Verification commands

From repository root:

```bash
mvn -q -DskipTests validate
cd eblocker-ui-next
npm ci
npm test
npm run build
cd ..
mvn -pl eblocker-ui-next package
```

The full release-equivalent package build is:

```bash
mvn -B -s settings.xml package -DskipTests=true
```

This builds the existing legacy Debian packages and the new `eblocker-ui-next` preview package.

## Next engineering step

The next real migration slice should add a typed backend API for system status, e.g. `/api/v1/system/status`, with OpenAPI output and a React system-status screen that no longer falls back to AngularJS.
