/*
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.influxdata.platform;

import java.time.Instant;

import org.influxdata.platform.domain.Health;
import org.influxdata.platform.domain.Onboarding;
import org.influxdata.platform.domain.OnboardingResponse;
import org.influxdata.platform.domain.Ready;
import org.influxdata.platform.domain.User;
import org.influxdata.platform.error.rest.UnprocessableEntityException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

/**
 * @author Jakub Bednar (bednar@github) (20/11/2018 07:37)
 */
@RunWith(JUnitPlatform.class)
class ITPlatformClient extends AbstractITClientTest {

    @Test
    void health() {

        Health health = platformClient.health();

        Assertions.assertThat(health).isNotNull();
        Assertions.assertThat(health.isHealthy()).isTrue();
        Assertions.assertThat(health.getMessage()).isEqualTo("ready for queries and writes");
    }

    @Test
    void healthNotRunningInstance() throws Exception {

        PlatformClient clientNotRunning = PlatformClientFactory.create("http://localhost:8099");
        Health health = clientNotRunning.health();

        Assertions.assertThat(health).isNotNull();
        Assertions.assertThat(health.isHealthy()).isFalse();
        Assertions.assertThat(health.getMessage()).startsWith("Failed to connect to");

        clientNotRunning.close();
    }

    @Test
    void ready() {

        Ready ready = platformClient.ready();

        Assertions.assertThat(ready).isNotNull();
        Assertions.assertThat(ready.getStatus()).isEqualTo("ready");
        Assertions.assertThat(ready.getStarted()).isNotNull();
        Assertions.assertThat(ready.getStarted()).isBefore(Instant.now());
        Assertions.assertThat(ready.getUp()).isNotBlank();
    }

    @Test
    void readyNotRunningInstance() throws Exception {

        PlatformClient clientNotRunning = PlatformClientFactory.create("http://localhost:8099");

        Ready ready = clientNotRunning.ready();
        Assertions.assertThat(ready).isNull();

        clientNotRunning.close();
    }

    @Test
    void isOnboardingNotAllowed() {

        Boolean onboardingAllowed = platformClient.isOnboardingAllowed();

        Assertions.assertThat(onboardingAllowed).isFalse();
    }

    @Test
    void onboarding() throws Exception {

        String url = "http://" + platformIP + ":9990";

        PlatformClient platformClient = PlatformClientFactory.create(url);

        Boolean onboardingAllowed = platformClient.isOnboardingAllowed();
        Assertions.assertThat(onboardingAllowed).isTrue();

        platformClient.close();

        OnboardingResponse onboarding = PlatformClientFactory.onBoarding(url, "admin", "111111", "Testing", "test");

        Assertions.assertThat(onboarding).isNotNull();
        Assertions.assertThat(onboarding.getUser()).isNotNull();
        Assertions.assertThat(onboarding.getUser().getId()).isNotEmpty();
        Assertions.assertThat(onboarding.getUser().getName()).isEqualTo("admin");

        Assertions.assertThat(onboarding.getBucket()).isNotNull();
        Assertions.assertThat(onboarding.getBucket().getId()).isNotEmpty();
        Assertions.assertThat(onboarding.getBucket().getName()).isEqualTo("test");

        Assertions.assertThat(onboarding.getOrganization()).isNotNull();
        Assertions.assertThat(onboarding.getOrganization().getId()).isNotEmpty();
        Assertions.assertThat(onboarding.getOrganization().getName()).isEqualTo("Testing");

        Assertions.assertThat(onboarding.getAuthorization()).isNotNull();
        Assertions.assertThat(onboarding.getAuthorization().getId()).isNotEmpty();
        Assertions.assertThat(onboarding.getAuthorization().getToken()).isNotEmpty();

        platformClient.close();

        platformClient = PlatformClientFactory.create(url, onboarding.getAuthorization().getToken().toCharArray());

        User me = platformClient.createUserClient().me();
        Assertions.assertThat(me).isNotNull();
        Assertions.assertThat(me.getName()).isEqualTo("admin");

        platformClient.close();
    }

    @Test
    void onboardingAlreadyDone() {

        Onboarding onboarding = new Onboarding();
        onboarding.setUsername("admin");
        onboarding.setPassword("111111");
        onboarding.setOrg("Testing");
        onboarding.setBucket("test");

        Assertions.assertThatThrownBy(() -> platformClient.onBoarding(onboarding))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage("onboarding has already been completed");
    }
}