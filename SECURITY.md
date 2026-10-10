# Security Policy

## Reporting a vulnerability

Please report security issues **privately** through GitHub's private vulnerability reporting: open the
repository's **Security** tab and choose **Report a vulnerability**. Do not open a public issue or pull request for
a security problem.

Useful details: the affected component (backend API, frontend, transcript proxy, container image), the steps to
reproduce, and the impact you expect. You will get an answer in the advisory thread.

## Supported versions

Only the latest release tag on `main` is maintained; fixes are not backported to older versions.

## Scope

The application code and configuration in this repository. Findings in third-party dependencies are welcome when
they affect Povzetkar; dependencies and container images are also scanned in CI (Trivy).
