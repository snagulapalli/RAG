# ADR-001: Java version

Date: 2020-09-25 (amended 2026-09-14)

## Context

The My Project backend is an application that is intended to be run on a cloud server. We will be able to freely choose the Java version that is installed.

The possible options are:

- **Java 8**: This version is still widely used, but not recommended anymore for starting new projects today.
- **Java 11**: It is now in paid Extended Support only through Jan 2032 and should not be used for starting new projects.
- **Java 21**: Recommended. This is the current LTS version, valid through Sept 2028.

## Decision

We will use Java 21 as it is a current, actively supported LTS version.

## Status

ACCEPTED (amended 2026-09-14)

## Consequences

- Developers can use all language features of Java 21, including virtual threads, pattern matching, and record patterns.
- Any library we select in the project must support Java 21.
