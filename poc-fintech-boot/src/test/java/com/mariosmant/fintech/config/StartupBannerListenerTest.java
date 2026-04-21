package com.mariosmant.fintech.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StartupBannerListener#renderBanner()}.
 *
 * <p>Covers both the normal path (with {@link BuildProperties} and
 * {@link GitProperties} injected by Spring Boot's {@code ProjectInfoAutoConfiguration})
 * and the IDE / no-build path where those beans are absent.</p>
 */
class StartupBannerListenerTest {

    private static StartupBannerListener newListener(MockEnvironment env,
                                                     BuildProperties build,
                                                     GitProperties git) {
        return new StartupBannerListener(env, build, git);
    }

    private static BuildProperties sampleBuild() {
        Properties p = new Properties();
        p.setProperty("group", "com.mariosmant");
        p.setProperty("artifact", "poc-fintech-boot");
        p.setProperty("version", "1.0.0-SNAPSHOT");
        p.setProperty("name", "POC Fintech - Boot");
        p.setProperty("time", Instant.parse("2026-04-21T10:15:30Z").toString());
        return new BuildProperties(p);
    }

    private static GitProperties sampleGit(boolean dirty) {
        // GitProperties stores entries WITHOUT the "git." prefix; getBranch() / getShortCommitId()
        // read keys "branch" and "commit.id.abbrev" respectively.
        Properties p = new Properties();
        p.setProperty("branch", "main");
        p.setProperty("commit.id", "abcdef1234567890");
        p.setProperty("commit.id.abbrev", "abcdef1");
        p.setProperty("commit.time", "2026-04-20T18:00:00Z");
        p.setProperty("dirty", Boolean.toString(dirty));
        return new GitProperties(p);
    }

    @Nested
    @DisplayName("renderBanner()")
    class RenderBanner {

        @Test
        @DisplayName("contains app name, version, commit, branch and profile when all info present")
        void rendersFullBanner() {
            var env = new MockEnvironment()
                    .withProperty("spring.application.name", "poc-fintech")
                    .withProperty("server.port", "8080");
            env.setActiveProfiles("prod");

            var banner = newListener(env, sampleBuild(), sampleGit(false)).renderBanner();

            assertThat(banner)
                    .contains("poc-fintech")
                    .contains("1.0.0-SNAPSHOT")
                    .contains("abcdef1")
                    .contains("branch main")
                    .contains("[prod]")
                    .contains(":8080")
                    .doesNotContain("(DIRTY)");
        }

        @Test
        @DisplayName("marks worktree as DIRTY when git.dirty=true")
        void rendersDirtyMarker() {
            var env = new MockEnvironment().withProperty("spring.application.name", "poc-fintech");
            var banner = newListener(env, sampleBuild(), sampleGit(true)).renderBanner();
            assertThat(banner).contains("(DIRTY)");
        }

        @Test
        @DisplayName("falls back to 'unknown' and [default] when BuildProperties/GitProperties absent")
        void rendersFallbacksWhenBuildAndGitMissing() {
            var env = new MockEnvironment().withProperty("spring.application.name", "poc-fintech");
            // No active profiles set → should render [default]

            var banner = newListener(env, null, null).renderBanner();

            assertThat(banner)
                    .contains("poc-fintech")
                    .contains("unknown")
                    .contains("[default]");
        }

        @Test
        @DisplayName("uses fallback application name when property missing")
        void rendersDefaultAppNameWhenPropertyMissing() {
            var env = new MockEnvironment();
            var banner = newListener(env, null, null).renderBanner();
            assertThat(banner).contains("poc-fintech"); // fallback in value()
        }
    }
}


