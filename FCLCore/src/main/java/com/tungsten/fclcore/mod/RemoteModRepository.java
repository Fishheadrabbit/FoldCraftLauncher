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
package com.tungsten.fclcore.mod;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface RemoteModRepository {

    enum Type {
        MOD,
        MODPACK,
        RESOURCE_PACK,
        SHADER_PACK,
        WORLD,
        CUSTOMIZATION
    }

    Type getType();

    enum SortType {
        POPULARITY,
        NAME,
        DATE_CREATED,
        LAST_UPDATED,
        AUTHOR,
        TOTAL_DOWNLOADS
    }

    enum SortOrder {
        ASC,
        DESC
    }

    class SearchResult {
        private final Stream<RemoteMod> sortedResults;

        private final Stream<RemoteMod> unsortedResults;

        private final int totalPages;

        public SearchResult(Stream<RemoteMod> sortedResults, Stream<RemoteMod> unsortedResults, int totalPages) {
            this.sortedResults = sortedResults;
            this.unsortedResults = unsortedResults;
            this.totalPages = totalPages;
        }

        public SearchResult(Stream<RemoteMod> sortedResults, int pages) {
            this.sortedResults = sortedResults;
            this.unsortedResults = sortedResults;
            this.totalPages = pages;
        }

        public Stream<RemoteMod> getResults() {
            return this.sortedResults;
        }

        public Stream<RemoteMod> getUnsortedResults() {
            return this.unsortedResults;
        }

        public int getTotalPages() {
            return this.totalPages;
        }
    }

    SearchResult search(String gameVersion, @Nullable Category category, int pageOffset, int pageSize, String searchFilter, SortType sortType, SortOrder sortOrder)
            throws IOException;

    Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) throws IOException;

    RemoteMod getModById(String id) throws IOException;

    RemoteMod.File getModFile(String modId, String fileId) throws IOException;

    Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException;

    Stream<Category> getCategories() throws IOException;

    class Category {
        private final Object self;
        private final String id;
        private final List<Category> subcategories;

        public Category(Object self, String id, List<Category> subcategories) {
            this.self = self;
            this.id = id;
            this.subcategories = subcategories;
        }

        public Object getSelf() {
            return self;
        }

        public String getId() {
            return id;
        }

        public List<Category> getSubcategories() {
            return subcategories;
        }
    }

    String[] DEFAULT_GAME_VERSIONS = new String[]{
            "",
            "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
            "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
            "1.15.2", "1.15.1", "1.15",
            "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
            "1.13.2", "1.13.1", "1.13",
            "1.12.2", "1.12.1", "1.12",
            "1.11.2", "1.11.1", "1.11",
            "1.10.2", "1.10.1", "1.10",
            "1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
            "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8",
            "1.7.10", "1.7.9", "1.7.8", "1.7.7", "1.7.6", "1.7.5", "1.7.4", "1.7.3", "1.7.2",
            "1.6.4", "1.6.2", "1.6.1",
            "1.5.2", "1.5.1",
            "1.4.7", "1.4.6", "1.4.5", "1.4.4", "1.4.2",
            "1.3.2", "1.3.1",
            "1.2.5", "1.2.4", "1.2.3", "1.2.2", "1.2.1",
            "1.1",
            "1.0"
    };
}
