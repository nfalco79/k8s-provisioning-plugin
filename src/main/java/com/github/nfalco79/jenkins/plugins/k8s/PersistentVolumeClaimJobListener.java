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

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;

@Extension
public class PersistentVolumeClaimJobListener extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(JobPVCWorkspaceVolume.class.getName());

    @Override
    public void onDeleted(Item item) {
        removePVC(item.getFullName());
    }

    private void removePVC(String jobName) {
        String pvcName = PVCUtil.normalize(jobName);

        // for each cloud remove the pvc
        List<KubernetesCloud> clouds = Jenkins.get().clouds.getAll(KubernetesCloud.class);
        try {
            for (KubernetesCloud cloud : clouds) {
                KubernetesClient client = cloud.connect();
                List<PersistentVolumeClaim> pvcs = client.persistentVolumeClaims().list().getItems();
                PersistentVolumeClaim pvc = pvcs.stream().filter(p -> Objects.equals(p.getMetadata().getName(), pvcName)).findFirst().orElse(null);
                if (pvc != null) {
                    // remove old volume, rename of pvc is not supported
                    client.persistentVolumeClaims().delete(pvc);
                    LOGGER.log(INFO, "Removed PVC: {0}/{1}", new Object[] { pvc.getMetadata().getNamespace(), jobName });
                }
            }
        } catch (KubernetesAuthException | IOException e) {
            LOGGER.log(SEVERE, "Can not remove PVC: " + pvcName, e);
        }
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        removePVC(oldName);
    }

    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        onRenamed(item, oldFullName, newFullName);
    }
}
