/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package com.tungsten.fclcore.mod.modrinth;

import static com.tungsten.fclcore.util.Lang.mapOf;
import static com.tungsten.fclcore.util.Pair.pair;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.tungsten.fclcore.mod.LocalModFile;
import com.tungsten.fclcore.mod.ModLoaderType;
import com.tungsten.fclcore.mod.RemoteMod;
import com.tungsten.fclcore.mod.RemoteModRepository;
import com.tungsten.fclcore.util.DigestUtils;
import com.tungsten.fclcore.util.Lang;
import com.tungsten.fclcore.util.Pair;
import com.tungsten.fclcore.util.StringUtils;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.HttpRequest;
import com.tungsten.fclcore.util.io.NetworkUtils;
import com.tungsten.fclcore.util.io.ResponseCodeException;
import com.tungsten.fclauncher.utils.FCLPath;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ModrinthRemoteModRepository implements RemoteModRepository {
    public static final ModrinthRemoteModRepository MODS = new ModrinthRemoteModRepository("mod");
    public static final ModrinthRemoteModRepository MODPACKS = new ModrinthRemoteModRepository("modpack");
    public static final ModrinthRemoteModRepository RESOURCE_PACKS = new ModrinthRemoteModRepository("resourcepack");
    public static final ModrinthRemoteModRepository SHADER_PACKS = new ModrinthRemoteModRepository("shader");

    private static final String PREFIX = FCLPath.Prop.getProperty("modrinth-api-url","null://");

    private final String projectType;

    private ModrinthRemoteModRepository(String projectType) {
        this.projectType = projectType;
    }

    @Override
    public Type getType() {
        return Type.MOD;
    }

    private static String convertSortType(SortType sortType) {
        switch (sortType) {
            case DATE_CREATED:
                return "newest";
            case POPULARITY:
            case NAME:
            case AUTHOR:
                return "relevance";
            case LAST_UPDATED:
                return "updated";
            case TOTAL_DOWNLOADS:
                return "downloads";
            default:
                throw new IllegalArgumentException("Unsupported sort type " + sortType);
        }
    }

    @Override
    public SearchResult search(String gameVersion, @Nullable RemoteModRepository.Category category, int pageOffset, int pageSize, String searchFilter, SortType sort, SortOrder sortOrder) throws IOException {
        List<List<String>> facets = new ArrayList<>();
        facets.add(Collections.singletonList("project_type:" + projectType));
        if (StringUtils.isNotBlank(gameVersion)) {
            facets.add(Collections.singletonList("versions:" + gameVersion));
        }
        if (category != null && StringUtils.isNotBlank(category.getId())) {
            facets.add(Collections.singletonList("categories:" + category.getId()));
        }
        Map<String, String> query = mapOf(
                pair("query", searchFilter),
                pair("facets", JsonUtils.UGLY_GSON.toJson(facets)),
                pair("offset", Integer.toString(pageOffset * pageSize)),
                pair("limit", Integer.toString(pageSize)),
                pair("index", convertSortType(sort))
        );
        Response<ProjectSearchResult> response = HttpRequest.GET(NetworkUtils.withQuery(PREFIX + "/v2/search", query))
                .getJson(new TypeToken<Response<ProjectSearchResult>>() {
                }.getType());
        return new SearchResult(response.getHits().stream().map(ProjectSearchResult::toMod), (int)Math.ceil((double)response.totalHits / pageSize));
    }

    @Override
    public Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) throws IOException {
        String sha1 = DigestUtils.digestToString("SHA-1", file);

        try {
            ProjectVersion mod = HttpRequest.GET(PREFIX + "/v2/version_file/" + sha1,
                            pair("algorithm", "sha1"))
                    .getJson(ProjectVersion.class);
            return mod.toVersion();
        } catch (ResponseCodeException e) {
            if (e.getResponseCode() == 404) {
                return Optional.empty();
            } else {
                throw e;
            }
        }
    }

    @Override
    public RemoteMod getModById(String id) throws IOException {
        id = StringUtils.removePrefix(id, "local-");
        Project project = HttpRequest.GET(PREFIX + "/v2/project/" + id).getJson(Project.class);
        return project.toMod();
    }

    @Override
    public RemoteMod.File getModFile(String modId, String fileId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException {
        id = StringUtils.removePrefix(id, "local-");
        List<ProjectVersion> versions = HttpRequest.GET(PREFIX + "/v2/project/" + id + "/version")
                .getJson(new TypeToken<List<ProjectVersion>>() {
                }.getType());
        return versions.stream().map(ProjectVersion::toVersion).flatMap(Lang::toStream);
    }

    public List<Category> getCategoriesImpl() throws IOException {
        List<Category> categories = HttpRequest.GET(PREFIX + "/v2/tag/category").getJson(new TypeToken<List<Category>>() {}.getType());
        return categories.stream().filter(category -> category.getProjectType().equals(projectType)).collect(Collectors.toList());
    }

    @Override
    public Stream<RemoteModRepository.Category> getCategories() throws IOException {
        return getCategoriesImpl().stream().map(Category::toCategory);
    }

    public static class Category {
        private final String icon;

        private final String name;

        @SerializedName("project_type")
        private final String projectType;

        public Category() {
            this("","","");
        }

        public Category(String icon, String name, String projectType) {
            this.icon = icon;
            this.name = name;
            this.projectType = projectType;
        }

        public String getIcon() {
            return icon;
        }

        public String getName() {
            return name;
        }

        public String getProjectType() {
            return projectType;
        }

        public RemoteModRepository.Category toCategory() {
            return new RemoteModRepository.Category(
                    this,
                    name,
                    Collections.emptyList());
        }
    }

    public static class Project implements RemoteMod.IMod {
        private final String slug;

        private final String title;

        private final String description;

        private final List<String> categories;

        /**
         * A long body describing project in detail.
         */
        private final String body;

        @SerializedName("project_type")
        private final String projectType;

        private final int downloads;

        @SerializedName("icon_url")
        private final String iconUrl;

        private final String id;

        private final String team;

        private final Instant published;

        private final Instant updated;

        private final List<String> versions;

        @SerializedName("gallery")
        private final List<Screenshot> screenshots;

        public Project(String slug, String title, String description, List<String> categories, String body, String projectType, int downloads, String iconUrl, String id, String team, Instant published, Instant updated, List<String> versions, List<Screenshot> screenshots) {
            this.slug = slug;
            this.title = title;
            this.description = description;
            this.categories = categories;
            this.body = body;
            this.projectType = projectType;
            this.downloads = downloads;
            this.iconUrl = iconUrl;
            this.id = id;
            this.team = team;
            this.published = published;
            this.updated = updated;
            this.versions = versions;
            this.screenshots = screenshots;
        }

        public String getSlug() {
            return slug;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getCategories() {
            return categories;
        }

        public String getBody() {
            return body;
        }

        public String getProjectType() {
            return projectType;
        }

        public int getDownloads() {
            return downloads;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getId() {
            return id;
        }

        public String getTeam() {
            return team;
        }

        public Instant getPublished() {
            return published;
        }

        public Instant getUpdated() {
            return updated;
        }

        public List<String> getVersions() {
            return versions;
        }

        public List<Screenshot> getScreenshots() {
            return screenshots;
        }

        @Override
        public List<RemoteMod> loadDependencies(RemoteModRepository modRepository) throws IOException {
            Set<RemoteMod.Dependency> dependencies = modRepository.getRemoteVersionsById(getId())
                    .flatMap(version -> version.getDependencies().stream())
                    .collect(Collectors.toSet());
            List<RemoteMod> mods = new ArrayList<>();
            for (RemoteMod.Dependency dependency : dependencies) {
                mods.add(dependency.load());
            }
            return mods;
        }

        @Override
        public Stream<RemoteMod.Version> loadVersions(RemoteModRepository modRepository) throws IOException {
            return modRepository.getRemoteVersionsById(getId());
        }

        @Override
        public List<RemoteMod.Screenshot> loadScreenshots(RemoteModRepository modRepository) {
            List<RemoteMod.Screenshot> screenshotList = new ArrayList<>();
            for (Screenshot screenshot : this.screenshots) {
                screenshotList.add(new RemoteMod.Screenshot(screenshot.url, screenshot.title, screenshot.description));
            }
            return screenshotList;
        }

        public RemoteMod toMod() {
            return new RemoteMod(
                    slug,
                    "",
                    title,
                    description,
                    categories,
                    String.format(FCLPath.Prop.getProperty("modrinth-url","null://%s/%s"), projectType, id),
                    iconUrl,
                    this,
                    id
            );
        }
    }

    public static class Dependency {
        @SerializedName("version_id")
        private final String versionId;

        @SerializedName("project_id")
        private final String projectId;

        @SerializedName("dependency_type")
        private final String dependencyType;

        public Dependency(String versionId, String projectId, String dependencyType) {
            this.versionId = versionId;
            this.projectId = projectId;
            this.dependencyType = dependencyType;
        }

        public String getVersionId() {
            return versionId;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getDependencyType() {
            return dependencyType;
        }
    }

    public static class ProjectVersion implements RemoteMod.IVersion {
        private static final Map<String, RemoteMod.DependencyType> DEPENDENCY_TYPE = Lang.mapOf(
                Pair.pair("required", RemoteMod.DependencyType.REQUIRED),
                Pair.pair("optional", RemoteMod.DependencyType.OPTIONAL),
                Pair.pair("embedded", RemoteMod.DependencyType.EMBEDDED),
                Pair.pair("incompatible", RemoteMod.DependencyType.INCOMPATIBLE)
        );

        private final String name;

        @SerializedName("version_number")
        private final String versionNumber;

        private final String changelog;

        private final List<Dependency> dependencies;

        @SerializedName("game_versions")
        private final List<String> gameVersions;

        @SerializedName("version_type")
        private final String versionType;

        private final List<String> loaders;

        private final boolean featured;

        private final String id;

        @SerializedName("project_id")
        private final String projectId;

        @SerializedName("author_id")
        private final String authorId;

        @SerializedName("date_published")
        private final Instant datePublished;

        private final int downloads;

        @SerializedName("changelog_url")
        private final String changelogUrl;

        private final List<ProjectVersionFile> files;

        public ProjectVersion(String name, String versionNumber, String changelog, List<Dependency> dependencies, List<String> gameVersions, String versionType, List<String> loaders, boolean featured, String id, String projectId, String authorId, Instant datePublished, int downloads, String changelogUrl, List<ProjectVersionFile> files) {
            this.name = name;
            this.versionNumber = versionNumber;
            this.changelog = changelog;
            this.dependencies = dependencies;
            this.gameVersions = gameVersions;
            this.versionType = versionType;
            this.loaders = loaders;
            this.featured = featured;
            this.id = id;
            this.projectId = projectId;
            this.authorId = authorId;
            this.datePublished = datePublished;
            this.downloads = downloads;
            this.changelogUrl = changelogUrl;
            this.files = files;
        }

        public String getName() {
            return name;
        }

        public String getVersionNumber() {
            return versionNumber;
        }

        public String getChangelog() {
            return changelog;
        }

        public List<Dependency> getDependencies() {
            return dependencies;
        }

        public List<String> getGameVersions() {
            return gameVersions;
        }

        public String getVersionType() {
            return versionType;
        }

        public List<String> getLoaders() {
            return loaders;
        }

        public boolean isFeatured() {
            return featured;
        }

        public String getId() {
            return id;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getAuthorId() {
            return authorId;
        }

        public Instant getDatePublished() {
            return datePublished;
        }

        public int getDownloads() {
            return downloads;
        }

        public String getChangelogUrl() {
            return changelogUrl;
        }

        public List<ProjectVersionFile> getFiles() {
            return files;
        }

        @Override
        public RemoteMod.Type getType() {
            return RemoteMod.Type.MODRINTH;
        }

        public Optional<RemoteMod.Version> toVersion() {
            RemoteMod.VersionType type;
            if ("release".equals(versionType)) {
                type = RemoteMod.VersionType.Release;
            } else if ("beta".equals(versionType)) {
                type = RemoteMod.VersionType.Beta;
            } else if ("alpha".equals(versionType)) {
                type = RemoteMod.VersionType.Alpha;
            } else {
                type = RemoteMod.VersionType.Release;
            }

            if (files.size() == 0) {
                return Optional.empty();
            }

            return Optional.of(new RemoteMod.Version(
                    this,
                    projectId,
                    name,
                    versionNumber,
                    changelog,
                    datePublished,
                    type,
                    files.get(0).toFile(),
                    dependencies.stream().map(dependency -> {
                        if (dependency.projectId == null) {
                            return RemoteMod.Dependency.ofBroken();
                        }

                        if (!DEPENDENCY_TYPE.containsKey(dependency.dependencyType)) {
                            throw new IllegalStateException("Broken datas");
                        }

                        return RemoteMod.Dependency.ofGeneral(DEPENDENCY_TYPE.get(dependency.dependencyType), MODS, dependency.projectId);
                    }).filter(Objects::nonNull).collect(Collectors.toList()),
                    gameVersions,
                    loaders.stream().flatMap(loader -> {
                        if ("fabric".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.FABRIC);
                        else if ("forge".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.FORGE);
                        else if ("neoforge".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.NEO_FORGED);
                        else if ("quilt".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.QUILT);
                        else if ("liteloader".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.LITE_LOADER);
                        else return Stream.empty();
                    }).collect(Collectors.toList())
            ));
        }
    }

    public static class ProjectVersionFile {
        private final Map<String, String> hashes;
        private final String url;
        private final String filename;
        private final boolean primary;
        private final int size;

        public ProjectVersionFile(Map<String, String> hashes, String url, String filename, boolean primary, int size) {
            this.hashes = hashes;
            this.url = url;
            this.filename = filename;
            this.primary = primary;
            this.size = size;
        }

        public Map<String, String> getHashes() {
            return hashes;
        }

        public String getUrl() {
            return url;
        }

        public String getFilename() {
            return filename;
        }

        public boolean isPrimary() {
            return primary;
        }

        public int getSize() {
            return size;
        }

        public RemoteMod.File toFile() {
            return new RemoteMod.File(hashes, url, filename);
        }
    }

    public static class ProjectSearchResult implements RemoteMod.IMod {
        private final String slug;

        private final String title;

        private final String description;

        private final List<String> categories;

        @SerializedName("project_type")
        private final String projectType;

        private final int downloads;

        @SerializedName("icon_url")
        private final String iconUrl;

        @SerializedName("project_id")
        private final String projectId;

        private final String author;

        private final List<String> versions;

        @SerializedName("date_created")
        private final Instant dateCreated;

        @SerializedName("date_modified")
        private final Instant dateModified;

        @SerializedName("latest_version")
        private final String latestVersion;

        public ProjectSearchResult(String slug, String title, String description, List<String> categories, String projectType, int downloads, String iconUrl, String projectId, String author, List<String> versions, Instant dateCreated, Instant dateModified, String latestVersion) {
            this.slug = slug;
            this.title = title;
            this.description = description;
            this.categories = categories;
            this.projectType = projectType;
            this.downloads = downloads;
            this.iconUrl = iconUrl;
            this.projectId = projectId;
            this.author = author;
            this.versions = versions;
            this.dateCreated = dateCreated;
            this.dateModified = dateModified;
            this.latestVersion = latestVersion;
        }

        public String getSlug() {
            return slug;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getCategories() {
            return categories;
        }

        public String getProjectType() {
            return projectType;
        }

        public int getDownloads() {
            return downloads;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getAuthor() {
            return author;
        }

        public List<String> getVersions() {
            return versions;
        }

        public Instant getDateCreated() {
            return dateCreated;
        }

        public Instant getDateModified() {
            return dateModified;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        @Override
        public List<RemoteMod> loadDependencies(RemoteModRepository modRepository) throws IOException {
            Set<RemoteMod.Dependency> dependencies = modRepository.getRemoteVersionsById(getProjectId())
                    .flatMap(version -> version.getDependencies().stream())
                    .collect(Collectors.toSet());
            List<RemoteMod> mods = new ArrayList<>();
            for (RemoteMod.Dependency dependency : dependencies) {
                mods.add(dependency.load());
            }
            return mods;
        }

        @Override
        public Stream<RemoteMod.Version> loadVersions(RemoteModRepository modRepository) throws IOException {
            return modRepository.getRemoteVersionsById(getProjectId());
        }

        @Override
        public List<RemoteMod.Screenshot> loadScreenshots(RemoteModRepository modRepository) throws IOException {
            //由于直接搜索得到的截图信息只有链接，没有标题、描述等信息，所以需要直接获取这个Mod的详细信息
            return modRepository.getModById(getProjectId()).getData().loadScreenshots(modRepository);
        }

        public RemoteMod toMod() {
            return new RemoteMod(
                    slug,
                    author,
                    title,
                    description,
                    categories,
                    String.format(FCLPath.Prop.getProperty("modrinth-url","null://%s/%s"), projectType, projectId),
                    iconUrl,
                    this,
                    projectId
            );
        }
    }

    public static class Response<T> {
        private final int offset;

        private final int limit;

        @SerializedName("total_hits")
        private final int totalHits;

        private final List<T> hits;

        public Response() {
            this(0, 0, Collections.emptyList());
        }

        public Response(int offset, int limit, List<T> hits) {
            this.offset = offset;
            this.limit = limit;
            this.totalHits = hits.size();
            this.hits = hits;
        }

        public int getOffset() {
            return offset;
        }

        public int getLimit() {
            return limit;
        }

        public int getTotalHits() {
            return totalHits;
        }

        public List<T> getHits() {
            return hits;
        }
    }

    public static class Screenshot {
        private final String url;
        @SerializedName("raw_url")
        private final String rawUrl;
        private final boolean featured;
        private final String title;
        private final String description;
        private final Instant created;
        private final int ordering;

        public Screenshot(String url, String rawUrl, boolean featured, String title, String description, Instant created, int ordering) {
            this.url = url;
            this.rawUrl = rawUrl;
            this.featured = featured;
            this.title = title;
            this.description = description;
            this.created = created;
            this.ordering = ordering;
        }

        public String getUrl() {
            return url;
        }

        public String getRawUrl() {
            return rawUrl;
        }

        public boolean isFeatured() {
            return featured;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public Instant getCreated() {
            return created;
        }

        public int getOrdering() {
            return ordering;
        }
    }
}
