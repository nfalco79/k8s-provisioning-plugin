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
import java.util.Locale;
import java.util.Map;

import com.github.nfalco79.jenkins.plugins.k8s.Messages;

import hudson.model.Computer;
import hudson.model.Node;

/**
 * Supported platform.
 *
 * @author Nikolas Falco
 */
public enum Platform {
    LINUX(""), WINDOWS(".bat"), MACOS("");

    /**
     * Choose the folder path suitable bin folder of the bundle.
     */
    public final String cmdExtension;

    Platform(String cmdExtension) {
        this.cmdExtension = cmdExtension;
    }

    public boolean is(String line) {
        return line.contains(name());
    }

    /**
     * Determines the platform of the given node.
     *
     * @param node
     *            the computer node
     * @return a platform value that represent the given node
     * @throws DetectionFailedException
     *             when the current platform node is not supported.
     */
    public static Platform of(Node node) throws DetectionFailedException {
        try {
            Computer computer = node.toComputer();
            if (computer == null) {
                throw new DetectionFailedException(Messages.SystemTools_nodeNotAvailable(node.getDisplayName()));
            }
            return detect(computer.getSystemProperties());
        } catch (IOException | InterruptedException e) {
            throw new DetectionFailedException(Messages.SystemTools_failureOnProperties(), e);
        }
    }

    public static Platform current() throws DetectionFailedException {
        return detect(System.getProperties());
    }

    private static Platform detect(Map<Object, Object> systemProperties) throws DetectionFailedException {
        String arch = ((String) systemProperties.get("os.name")).toLowerCase(Locale.ENGLISH);
        if (arch.contains("linux")) {
            return LINUX;
        }
        if (arch.contains("windows")) {
            return WINDOWS;
        }
        if (arch.contains("mac")) {
            return MACOS;
        }
        throw new DetectionFailedException(Messages.Platform_unknown(arch));
    }

}
