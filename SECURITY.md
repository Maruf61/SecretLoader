# Security Policy

SecretLoader handles credentials, so security reports are taken seriously.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Report it privately through either:

- GitHub's [private vulnerability reporting](https://github.com/Maruf61/SecretLoader/security/advisories/new)
  (the repository's **Security → Report a vulnerability**), or
- email **maruf.hakyemez@kivierp.com.tr**.

Please include the edition (IntelliJ / Visual Studio / VS Code), the version, the
IDE and vault/CLI involved, and steps to reproduce. **Redact any real secret
values** from logs, screenshots, and command output.

We aim to acknowledge a report within a few business days and will keep you
updated on the fix and the disclosure timeline.

## Supported versions

| Version | Supported |
| ------- | --------- |
| 1.0.x   | ✅         |

## Scope

SecretLoader is designed to keep secrets off disk and out of logs: values are
injected into the launch in memory, never written back to run configurations,
and never logged (console masking is best-effort defense-in-depth, as documented
in each edition's README). Reports that a secret value reaches disk, a log, or
another project's launch — or that the CLI command can be made to execute
unintended input — are in scope and treated as high priority.
