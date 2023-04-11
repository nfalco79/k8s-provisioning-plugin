/*
 * Copyright 2023 Nikolas Falco
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
package com.github.nfalco79.jenkins.plugins.k8s.tools;

import java.io.IOException;
import java.util.List;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import com.github.nfalco79.jenkins.plugins.k8s.K8sConstants;
import com.github.nfalco79.jenkins.plugins.k8s.Messages;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;

/**
 * Tool definition to install a Kubernetes CLI.
 *
 * @author Nikolas Falco
 */
public class KubectlInstallation extends ToolInstallation
        implements EnvironmentSpecific<KubectlInstallation>, NodeSpecific<KubectlInstallation> {

    private static final long serialVersionUID = -843758332396300622L;

    @DataBoundConstructor
    public KubectlInstallation(@NonNull String name, @NonNull String home, List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
    }

    @Override
    public KubectlInstallation forEnvironment(EnvVars environment) {
        return new KubectlInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Override
    public KubectlInstallation forNode(@NonNull Node node, TaskListener log) throws IOException, InterruptedException {
        return new KubectlInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Override
    public void buildEnvVars(EnvVars env) {
        String home = getHome();
        if (home == null) {
            return;
        }
        env.put(K8sConstants.ENVVAR_KUBECTL_PATH, getHome());
    }

    @Extension
    @Symbol("kubectl")
    public static class DescriptorImpl extends ToolDescriptor<KubectlInstallation> {

        public DescriptorImpl() {
            load();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Installation_displayName();
        }

        @Override
        public void setInstallations(KubectlInstallation... installations) {
            super.setInstallations(installations);
            /*
             * Invoked when the global configuration page is submitted. If
             * installation are modified programmatically than it's a developer
             * task perform the call to save method on this descriptor.
             */
            save();
        }
    }
}