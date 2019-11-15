
/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package me.porcelli.nio.jgit.impl;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import me.porcelli.nio.jgit.cluster.ClusterMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGitFileSystemsEventsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(JGitFileSystemsEventsManager.class);

    private final Map<String, JGitFileSystemWatchServices> fsWatchServices = new ConcurrentHashMap<>();

    private final ClusterMessageService clusterMessageService;

    JGitEventsBroadcast jGitEventsBroadcast;

    public JGitFileSystemsEventsManager() {
        clusterMessageService = getClusterMessageService();

        if (clusterMessageService.isSystemClustered()) {
            setupJGitEventsBroadcast();
        }
    }

    ClusterMessageService getClusterMessageService() {
        return new ClusterMessageService() {
            @Override
            public void connect() {

            }

            @Override
            public <T> void createConsumer(DestinationType type, String channel, Class<T> clazz, Consumer<T> listener) {

            }

            @Override
            public void broadcast(DestinationType type, String channel, Serializable object) {

            }

            @Override
            public boolean isSystemClustered() {
                return false;
            }

            @Override
            public void close() {

            }
        };
    }

    void setupJGitEventsBroadcast() {
        jGitEventsBroadcast = new JGitEventsBroadcast(clusterMessageService,
                                                      w -> publishEvents(w.getFsName(),
                                                                         w.getWatchable(),
                                                                         w.getEvents(),
                                                                         false));
    }

    public WatchService newWatchService(String fsName)
            throws UnsupportedOperationException, IOException {
        fsWatchServices.putIfAbsent(fsName,
                                    createFSWatchServicesManager());

        if (jGitEventsBroadcast != null) {
            jGitEventsBroadcast.createWatchService(fsName);
        }

        return fsWatchServices.get(fsName).newWatchService(fsName);
    }

    JGitFileSystemWatchServices createFSWatchServicesManager() {
        return new JGitFileSystemWatchServices();
    }

    public void publishEvents(String fsName,
                              Path watchable,
                              List<WatchEvent<?>> elist) {

        publishEvents(fsName,
                      watchable,
                      elist,
                      true);
    }

    public void publishEvents(String fsName,
                              Path watchable,
                              List<WatchEvent<?>> elist,
                              boolean broadcastEvents) {

        JGitFileSystemWatchServices watchService = fsWatchServices.get(fsName);

        if (watchService == null) {
            return;
        }

        watchService.publishEvents(watchable,
                                   elist);

        if (shouldIBroadcast(broadcastEvents)) {
            jGitEventsBroadcast.broadcast(fsName,
                                          watchable,
                                          elist);
        }
    }

    private boolean shouldIBroadcast(boolean broadcastEvents) {
        return broadcastEvents && jGitEventsBroadcast != null;
    }

    public void close(String name) {

        JGitFileSystemWatchServices watchService = fsWatchServices.get(name);

        if (watchService != null) {
            try {
                watchService.close();
            } catch (final Exception ex) {
                LOGGER.error("Can't close watch service [" + toString() + "]",
                             ex);
            }
        }
    }

    public void shutdown() {
        fsWatchServices.keySet().forEach(key -> this.close(key));

        if (jGitEventsBroadcast != null) {
            jGitEventsBroadcast.close();
        }
    }

    JGitEventsBroadcast getjGitEventsBroadcast() {
        return jGitEventsBroadcast;
    }

    Map<String, JGitFileSystemWatchServices> getFsWatchServices() {
        return fsWatchServices;
    }
}
