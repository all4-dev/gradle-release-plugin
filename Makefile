# Gradle Publish Plugin - Development Commands
# Usage: make <target>

.PHONY: help build check test coverage format docs clean publish-local publish-portal publish-central bump-pre bumpversion-and-remote-pblish-andtag

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
	@echo ""
	@echo "  Versioning:"
	@echo "    bump-pre        - Bump to prerelease (e.g., 0.1.0 → 0.1.0-alpha.1)"
	@echo "    bumpversion-and-remote-pblish-andtag - Bump version, tag, push, publish Portal + Central (VERSION=x.y.z)"

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
	@current=$$(grep 'version = ' plugin/build.gradle.kts | sed 's/.*"\(.*\)"/\1/'); \
	if echo "$$current" | grep -q '\-alpha\.'; then \
		num=$$(echo "$$current" | sed 's/.*-alpha\.\([0-9]*\)/\1/'); \
		base=$$(echo "$$current" | sed 's/-alpha\.[0-9]*//'); \
		new="$$base-alpha.$$((num + 1))"; \
	else \
		new="$$current-alpha.1"; \
	fi; \
	sed -i '' "s/version = \"$$current\"/version = \"$$new\"/" plugin/build.gradle.kts; \
	echo "🆙 Bumping version: $$current → $$new"; \
	git add plugin/build.gradle.kts && git commit -m "chore: bump version to $$new"; \
	git tag -a "v$$new" -m "Release $$new"; \
	git push origin HEAD; \
	git push origin "v$$new"; \
	$(MAKE) publish-portal; \
	$(MAKE) publish-central

tag-and-publish-release:
	@if [ -z "$(VERSION)" ]; then \
		echo "Usage: make $@ VERSION=x.y.z"; \
		exit 1; \
	fi
	@set -e; \
	current=$$(grep 'version = ' plugin/build.gradle.kts | sed 's/.*"\(.*\)"/\1/'); \
	new="$(VERSION)"; \
	if [ "$$current" = "$$new" ]; then \
		echo "Version already $$new"; \
		exit 1; \
	fi; \
	if [ -n "$$(git status --porcelain)" ]; then \
		echo "Working tree is dirty. Commit or stash changes before releasing."; \
		exit 1; \
	fi; \
	sed -i '' "s/version = \"$$current\"/version = \"$$new\"/" plugin/build.gradle.kts; \
	echo "🆙 Bumping version: $$current → $$new"; \
	git add plugin/build.gradle.kts; \
	git commit -m "chore: bump version to $$new"; \
	git tag -a "v$$new" -m "Release $$new"; \
	git push origin HEAD; \
	git push origin "v$$new"; \
	$(MAKE) publish-portal; \
	$(MAKE) publish-central

# ============================================================================
# Versioning
# ============================================================================

bump-pre:
	@current=$$(grep 'version = ' plugin/build.gradle.kts | sed 's/.*"\(.*\)"/\1/'); \
	if echo "$$current" | grep -q '\-alpha\.'; then \
		num=$$(echo "$$current" | sed 's/.*-alpha\.\([0-9]*\)/\1/'); \
		base=$$(echo "$$current" | sed 's/-alpha\.[0-9]*//'); \
		new="$$base-alpha.$$((num + 1))"; \
	else \
		new="$$current-alpha.1"; \
	fi; \
	sed -i '' "s/version = \"$$current\"/version = \"$$new\"/" plugin/build.gradle.kts; \
	echo "🆙 Bumping version: $$current → $$new"; \
	git add plugin/build.gradle.kts && git commit -m "chore: bump version to $$new"

# ============================================================================

publish-local:
	./gradlew :plugin:publishToMavenLocal

publish-portal:
	build-logic/scripts/publish-portal.main.kts

publish-central:
	build-logic/scripts/publish-central.main.kts