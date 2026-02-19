# Gradle Publish Plugin - Development Commands
# Usage: make <target>

.PHONY: help build check test functional integration coverage format docs clean publish-local publish-portal publish-central bump-pre tag-and-publish-pre-release tag-and-publish-release release-doctor

RELEASE_CONVENTION=./gradlew

# Default target
help:
	@echo "Available targets:"
	@echo ""
	@echo "  Build:"
	@echo "    build      - Build the plugin"
	@echo "    check      - Run all checks (tests, detekt, kover)"
	@echo "    clean      - Clean build outputs"
	@echo ""
	@echo "  Test & Coverage:"
	@echo "    test       - Run unit tests"
	@echo "    functional - Run functional tests (GradleRunner)"
	@echo "    integration- Run integration tests (GradleRunner)"
	@echo "    coverage   - Run ALL tests + 3 coverage reports (unit, agent, merged)"
	@echo ""
	@echo "  Code Quality:"
	@echo "    format     - Format + lint (ktfmt + detekt)"
	@echo "    docs       - Generate Dokka documentation"
	@echo ""
	@echo "  Publish:"
	@echo "    publish-local   - Publish to ~/.m2/repository (mavenLocal)"
	@echo "    publish-portal  - Publish to Gradle Plugin Portal"
	@echo "    publish-central - Publish to Maven Central (Sonatype)"
	@echo "    release-doctor  - Validate release scripts + 1Password access"
	@echo ""
	@echo "  Versioning:"
	@echo "    bump-pre        - Bump to prerelease (e.g., 0.1.0 → 0.1.0-alpha.1)"
	@echo "    tag-and-publish-pre-release - Bump alpha, tag, publish, push"
	@echo "    tag-and-publish-release     - Release stable version (VERSION=x.y.z)"

# ============================================================================
# Build
# ============================================================================

build:
	./gradlew :plugin:build

check:
	./gradlew :plugin:check

clean:
	./gradlew clean

# ============================================================================
# Test & Coverage
# ============================================================================

test:
	./gradlew :plugin:test

functional:
	./gradlew :plugin:functionalTest

integration:
	./gradlew :plugin:integrationTest

coverage: clean
	# Single task generates all 3 coverage reports (unit, agent, merged)
	./gradlew :plugin:koverMergedHtmlReport --rerun-tasks

# ============================================================================
# Code Quality
# ============================================================================

format:
	./gradlew :plugin:ktfmtFormat :plugin:detekt

docs:
	./gradlew :plugin:dokkaHtml
	@echo ""
	@echo "Dokka: file://$$(pwd)/plugin/build/dokka/html/index.html"


# ============================================================================
# Publish

tag-and-publish-pre-release:
	$(RELEASE_CONVENTION) releaseTagAndPublishPreRelease

tag-and-publish-release:
	@if [ -z "$(VERSION)" ]; then \
		echo "Usage: make $@ VERSION=x.y.z"; \
		exit 1; \
	fi
	$(RELEASE_CONVENTION) releaseTagAndPublishRelease -Prelease.version="$(VERSION)"

# ============================================================================
# Versioning
# ============================================================================

bump-pre:
	$(RELEASE_CONVENTION) releaseBumpPre

# ============================================================================

publish-local:
	$(RELEASE_CONVENTION) releasePublishLocal

publish-portal:
	$(RELEASE_CONVENTION) releasePublishPortal

publish-central:
	$(RELEASE_CONVENTION) releasePublishCentral

release-doctor:
	$(RELEASE_CONVENTION) releaseDoctor
