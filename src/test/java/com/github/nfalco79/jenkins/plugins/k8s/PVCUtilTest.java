/*
 * Copyright 2021 Nikolas Falco
 *
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.nfalco79.jenkins.plugins.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class PVCUtilTest {

    @Test
    void test_nomalize_with_encoded_characters() {
        String result = PVCUtil.normalize("MY projects/cm.images-docker-image%2fcli");
        assertThat(result).matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*");
    }

    @Test
    void test_nomalize_with_spurious_characters() {
        String result = PVCUtil.normalize("CM projects/cm.images-$docker$-images");
        assertThat(result).matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*");
    }
}
