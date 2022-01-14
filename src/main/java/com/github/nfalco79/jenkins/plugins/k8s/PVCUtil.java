/*
 * Copyright 2021 Nikolas Falco
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

import java.net.URLDecoder;

import javax.annotation.Nonnull;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;

public final class PVCUtil {

    private PVCUtil() {
    }

    public static String normalize(String claimName) {
        @SuppressWarnings("deprecation")
        String normalized = "pvc-" + URLDecoder.decode(claimName).trim().replace(' ', '-').replace('/', '-').toLowerCase();
        // remove any not admitted characters
        return normalized.replaceAll("[^0-9a-z-._]", "");
    }

    public static Quantity getRequestSize(@Nonnull PersistentVolumeClaim pvc) {
        return pvc.getSpec().getResources().getRequests().get("storage");
    }
}
