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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.jenkinsci.remoting.util.VersionNumber;
import org.kohsuke.stapler.DataBoundConstructor;

import com.github.nfalco79.jenkins.plugins.k8s.K8sConstants;
import com.github.nfalco79.jenkins.plugins.k8s.Messages;

import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.TarCompression;
import hudson.Functions;
import hudson.ProxyConfiguration;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Download and installs Kubernetes CLI.
 *
 * @author Nikolas Falco
 */
public class KubectlInstaller extends DownloadFromUrlInstaller {

    private static final boolean DISABLE_CACHE = Boolean.getBoolean(KubectlInstaller.class.getName() + ".cache.disable");

    @DataBoundConstructor
    public KubectlInstaller(String id) {
        super(id);
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath expected = preferredLocation(tool, node);
        FilePath kubectl = expected.child(K8sConstants.KUBECTL_CMD);

        Installable installable = getInstallable();
        if (installable == null) {
            log.getLogger().println("Invalid tool ID " + id);
            return expected;
        }

        if (installable instanceof NodeSpecific) {
            installable = ((NodeSpecific<KubectlInstallable>) installable).forNode(node, log);
            kubectl = expected.child(((KubectlInstallable) installable).cmd);
        }

        if (!isUpToDate(expected, installable)) {
            File cache = getLocalCacheFile(installable, node);
            boolean skipInstall = false;
            if (!DISABLE_CACHE && cache.exists()) {
                log.getLogger().println(Messages.Installer_installFromCache(cache, expected, node.getDisplayName()));
                try {
                    restoreCache(expected, cache);
                    skipInstall = true;
                } catch (IOException e) {
                    log.error("Use of caches failed: " + e.getMessage());
                }
            }
            if (!skipInstall) {
                // download the single executable file
                URL url = new URL(installable.url);
                URLConnection con = ProxyConfiguration.open(url);
                con.setIfModifiedSince(expected.child(".timestamp").lastModified());
                try (InputStream is = con.getInputStream()) {
                    kubectl.copyFrom(is);
                }
                // leave a record for the next up-to-date check
                expected.child(".installedFrom").write(installable.url, "UTF-8");
                kubectl.act(new ChmodRecAPlusX());
                if (!DISABLE_CACHE) {
                    buildCache(expected, cache);
                }
            }
        }
        return expected;
    }

    private void buildCache(FilePath expected, File cache) throws IOException, InterruptedException {
        // update the local cache on master
        // download to a temporary file and rename it in to handle concurrency and failure correctly,
        Path tmp = new File(cache.getPath() + ".tmp").toPath();
        try {
            Path tmpParent = tmp.getParent();
            if (tmpParent != null) {
                Files.createDirectories(tmpParent);
            }
            try (OutputStream out = new GzipCompressorOutputStream(Files.newOutputStream(tmp))) {
                // workaround to not store current folder as root folder in the archive
                // this prevent issue when tool name is renamed
                expected.tar(out, "**");
            }
            Files.move(tmp, cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private File getLocalCacheFile(Installable installable, Node node) throws DetectionFailedException {
        Platform platform = Platform.of(node);
        // we store cache as tar.gz to preserve symlink
        return new File(Jenkins.get().getRootPath() //
                .child("caches") //
                .child("dependency-check") //
                .child(platform.toString()) //
                .child(id + ".tar.gz") //
                .getRemote());
    }

    private void restoreCache(FilePath expected, File cache) throws IOException, InterruptedException {
        try (InputStream in = cache.toURI().toURL().openStream()) {
            CountingInputStream cis = new CountingInputStream(in);
            try {
                Objects.requireNonNull(expected).untarFrom(cis, TarCompression.GZIP);
            } catch (IOException e) {
                throw new IOException(Messages.Installer_failedToUnpack(cache.toURI().toURL(), cis.getByteCount()), e);
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<KubectlInstaller> {

        private List<? extends Installable> installables = new ArrayList<>();
        private LocalDateTime expiryDate = LocalDateTime.now();

        @Override
        public String getDisplayName() {
            return Messages.Installer_displayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == KubectlInstallation.class;
        }

        @Override
        public List<? extends Installable> getInstallables() throws IOException {
            // move this to a crawler at https://github.com/jenkins-infra/crawler
            if (expiryDate.isBefore(LocalDateTime.now()) || installables.isEmpty()) {
                expiryDate = LocalDateTime.now().plusHours(1);
                Proxy proxy = Proxy.NO_PROXY;
                ProxyConfiguration proxyCfg = Jenkins.get().getProxy();
                if (proxyCfg != null) {
                    proxy = proxyCfg.createProxy("api.github.com");
                }

                List<KubectlInstallable> ghInstallables = new ArrayList<>();
                URL githubURL = new URL("https://api.github.com/repos/kubernetes/kubernetes/releases?per_page=100");
                try (InputStream is = githubURL.openConnection(proxy).getInputStream()) {
                    JSONArray releases = JSONArray.fromObject(IOUtils.toString(is, StandardCharsets.UTF_8));
                    releases.forEach(rel -> {
                        JSONObject release = (JSONObject) rel;
                        if (!release.getBoolean("prerelease") && !release.getBoolean("draft")) {
                            String id = release.getString("tag_name");
                            String name = release.getString("name");
                            ghInstallables.add(new KubectlInstallable(id, name));
                            new VersionNumber(name);
                            ghInstallables.sort((i1, i2) -> new VersionNumber(i2.id).compareTo(new VersionNumber(i1.id)));
                        }
                    });
                }
                installables = ghInstallables;
            }
            return installables;
        }

    }

    /**
     * Sets execute permission on given file.
     */
    static class ChmodRecAPlusX extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        @Override
        public Void invoke(File d, VirtualChannel channel) throws IOException {
            if (!Functions.isWindows())
                process(d);
            return null;
        }

        private void process(File f) {
            if (f.isFile()) {
                f.setExecutable(true, false); // NOSONAR
            }
        }
    }

    public static class KubectlInstallable extends Installable implements NodeSpecific<KubectlInstallable> {
        public String cmd;

        public KubectlInstallable(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public KubectlInstallable forNode(Node node, TaskListener log) throws IOException, InterruptedException {
            String cmd;
            String platform;
            switch (Platform.of(node)) {
            case LINUX:
                platform = "linux";
                cmd = "kubectl";
                break;
            case MACOS:
                platform = "darwn";
                cmd = "kubectl";
                break;
            case WINDOWS:
                platform = "windows";
                cmd = "kubectl.exe";
                break;
            default:
                throw new IllegalStateException("Unmanaged node platform");
            }

            KubectlInstallable clone = new KubectlInstallable(id, name);
            clone.cmd = cmd;
            clone.url = "https://dl.k8s.io/release/" + id + "/bin/" + platform + "/amd64/" + cmd;
            return clone;
        }

    }
}
