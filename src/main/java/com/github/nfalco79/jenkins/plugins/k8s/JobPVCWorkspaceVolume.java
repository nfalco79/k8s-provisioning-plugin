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
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud.DEFAULT_POD_LABELS;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.csanchez.jenkins.plugins.kubernetes.volumes.DynamicPVC;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.client.KubernetesClient;

@SuppressWarnings("serial")
public class JobPVCWorkspaceVolume extends WorkspaceVolume implements DynamicPVC {
    private static final Logger LOGGER = Logger.getLogger(JobPVCWorkspaceVolume.class.getName());

    private final String claimName;
    private final String storageClassName;
    private final String requestsSize;
    private final String accessModes;

    @DataBoundConstructor
    public JobPVCWorkspaceVolume(@NonNull String claimName,
                                 String storageClassName,
                                 @NonNull String requestsSize,
                                 String accessModes) {
        this.claimName = claimName;
        this.storageClassName = storageClassName;
        this.requestsSize = requestsSize;
        this.accessModes = accessModes;
    }

    public String getClaimName() {
        return claimName;
    }

    @Override
    @CheckForNull
    public String getAccessModes() {
        return accessModes;
    }

    @Override
    @CheckForNull
    public String getRequestsSize() {
        return requestsSize;
    }

    @Override
    @CheckForNull
    public String getStorageClassName() {
        return storageClassName;
    }

    @Override
    public Volume buildVolume(String volumeName, String podName) {
        return buildPVC(volumeName, podName);
    }

    @Override
    public String getPvcName(String podName) {
        return PVCUtil.normalize(getClaimName());
    }

    @Override
    public PersistentVolumeClaim createVolume(KubernetesClient client, ObjectMeta podMetaData) {
        String namespace = podMetaData.getNamespace();
        String podId = podMetaData.getName();
        String pvcName = getPvcName(podId);
        LOGGER.log(Level.FINE, "Adding workspace volume {0} from pod: {1}/{2}", new Object[] { pvcName, namespace, podId });

        List<PersistentVolumeClaim> pvcs = client.persistentVolumeClaims().list().getItems();
        PersistentVolumeClaim pvc = pvcs.stream().filter(p -> Objects.equals(p.getMetadata().getName(), pvcName)).findFirst().orElse(null);

        if (pvc != null) {
            // check if size has been changed
            Quantity actualStorage = PVCUtil.getRequestSize(pvc);
            Quantity requestStorage = Quantity.parse(getRequestsSizeOrDefault());
            if (!actualStorage.equals(requestStorage)) {
                LOGGER.log(INFO, "PVC request is different than actual storage, from {0} to {1}. Request new one", new Object[] { actualStorage, requestStorage });

                client.persistentVolumeClaims().resource(pvc).delete();
                LOGGER.log(INFO, "Removed PVC: {0}/{1}", new Object[] { pvc.getMetadata().getNamespace(), pvc.getFullResourceName() });
                pvc = null;
            }
        }

        if (pvc == null) {
            pvc = new PersistentVolumeClaimBuilder() //
                    .withNewMetadata() //
                    .withName(pvcName) //
                    .withLabels(DEFAULT_POD_LABELS) //
                    .endMetadata() //
                    .withNewSpec() //
                    .withAccessModes(getAccessModesOrDefault()) //
                    .withNewResources() //
                    .withRequests(getResourceMap()) //
                    .endResources() //
                    .withStorageClassName(getStorageClassNameOrDefault()) //
                    .endSpec() //
                    .build();
            pvc = client.persistentVolumeClaims() //
                    .inNamespace(podMetaData.getNamespace()) //
                    .resource(pvc).create();
            LOGGER.log(INFO, "Created PVC: {0}/{1}", new Object[] { namespace, pvcName });
        }
        return pvc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JobPVCWorkspaceVolume that = (JobPVCWorkspaceVolume) o;
        return Objects.equals(storageClassName, that.storageClassName) &&
                Objects.equals(requestsSize, that.requestsSize) &&
                Objects.equals(accessModes, that.accessModes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageClassName, requestsSize, accessModes);
    }

    @Extension
    @Symbol("jobPVC")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {

        private static final ListBoxModel ACCESS_MODES_BOX = new ListBoxModel() //
                .add("ReadWriteOnce") //
                .add("ReadOnlyMany") //
                .add("ReadWriteMany");

        @Override
        public String getDisplayName() {
            return "Per Job Persistent Volume Claim Workspace Volume";
        }

        @RequirePOST
        @Restricted(DoNotUse.class) // stapler only
        public ListBoxModel doFillAccessModesItems() {
            return ACCESS_MODES_BOX;
        }
    }

}