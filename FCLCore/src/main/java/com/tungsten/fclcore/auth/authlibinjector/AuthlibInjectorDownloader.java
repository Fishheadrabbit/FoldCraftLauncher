/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tungsten.fclcore.auth.authlibinjector;

import static com.tungsten.fclcore.util.Logging.LOG;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.tungsten.fclcore.download.DownloadProvider;
import com.tungsten.fclcore.task.FileDownloadTask;
import com.tungsten.fclcore.util.io.HttpRequest;
import com.tungsten.fclauncher.utils.FCLPath;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;

public class AuthlibInjectorDownloader implements AuthlibInjectorArtifactProvider {

    private static final String LATEST_BUILD_URL = FCLPath.Prop.getProperty("authlib-injector-url", "null://");

    private final Path artifactLocation;
    private final Supplier<DownloadProvider> downloadProvider;

    public AuthlibInjectorDownloader(Path artifactLocation, Supplier<DownloadProvider> downloadProvider) {
        this.artifactLocation = artifactLocation;
        this.downloadProvider = downloadProvider;
    }

    @Override
    public AuthlibInjectorArtifactInfo getArtifactInfo() throws IOException {
        Optional<AuthlibInjectorArtifactInfo> cached = getArtifactInfoImmediately();
        if (cached.isPresent()) {
            return cached.get();
        }

        synchronized (this) {
            Optional<AuthlibInjectorArtifactInfo> local = getLocalArtifact();
            if (local.isPresent()) {
                return local.get();
            }
            LOG.info("No local authlib-injector found, downloading");
            updateChecked.set(true);
            update();
            local = getLocalArtifact();
            return local.orElseThrow(() -> new IOException("The downloaded authlib-inejector cannot be recognized"));
        }
    }

    @Override
    public Optional<AuthlibInjectorArtifactInfo> getArtifactInfoImmediately() {
        return getLocalArtifact();
    }

    private final AtomicBoolean updateChecked = new AtomicBoolean(false);

    public void checkUpdate() throws IOException {
        // this method runs only once
        if (updateChecked.compareAndSet(false, true)) {
            synchronized (this) {
                LOG.info("Checking update of authlib-injector");
                update();
            }
        }
    }

    private void update() throws IOException {
        AuthlibInjectorVersionInfo latest = getLatestArtifactInfo();

        Optional<AuthlibInjectorArtifactInfo> local = getLocalArtifact();
        if (local.isPresent() && local.get().getBuildNumber() >= latest.buildNumber) {
            return;
        }

        try {
            new FileDownloadTask(downloadProvider.get().injectURLWithCandidates(latest.downloadUrl), artifactLocation.toFile(),
                    Optional.ofNullable(latest.checksums.get("sha256"))
                            .map(checksum -> new FileDownloadTask.IntegrityCheck("SHA-256", checksum))
                            .orElse(null))
                    .run();
        } catch (Exception e) {
            throw new IOException("Failed to download authlib-injector", e);
        }

        LOG.info("Updated authlib-injector to " + latest.version);
    }

    private AuthlibInjectorVersionInfo getLatestArtifactInfo() throws IOException {
        IOException exception = null;
        for (URL url : downloadProvider.get().injectURLWithCandidates(LATEST_BUILD_URL)) {
            try {
                return HttpRequest.GET(url.toExternalForm()).getJson(AuthlibInjectorVersionInfo.class);
            } catch (IOException | JsonParseException e) {
                if (exception == null) {
                    exception = new IOException("Failed to fetch authlib-injector artifact info");
                }
                exception.addSuppressed(e);
            }
        }

        if (exception == null) {
            exception = new IOException("No authlib-injector download providers available");
        }
        throw exception;
    }

    private Optional<AuthlibInjectorArtifactInfo> getLocalArtifact() {
        return parseArtifact(artifactLocation);
    }

    protected static Optional<AuthlibInjectorArtifactInfo> parseArtifact(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(AuthlibInjectorArtifactInfo.from(path));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Bad authlib-injector artifact", e);
            return Optional.empty();
        }
    }

    private static final class AuthlibInjectorVersionInfo {
        @SerializedName("build_number")
        public int buildNumber;

        @SerializedName("version")
        public String version;

        @SerializedName("download_url")
        public String downloadUrl;

        @SerializedName("checksums")
        public Map<String, String> checksums;
    }

}
