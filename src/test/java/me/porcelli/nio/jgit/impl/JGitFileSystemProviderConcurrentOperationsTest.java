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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class JGitFileSystemProviderConcurrentOperationsTest extends AbstractTestInfra {

    private Logger logger = LoggerFactory.getLogger(JGitFileSystemProviderConcurrentOperationsTest.class);

    @Test
    public void testConcurrentGitCreation() {

        int threadCount = 2;
        final CountDownLatch finished = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int name = i;
            Runnable r = () -> {
                this.provider.createNewGitRepo(EMPTY_ENV,
                                               "git://parent/concurrent-test" + name);
                finished.countDown();
                logger.info("Countdown" + Thread.currentThread().getName());
            };
            Thread t = new Thread(r);
            threads.add(t);
            t.start();
        }

        wait(threads);
        assertEquals(0,
                     finished.getCount());
    }

    @Test
    public void testConcurrentGitDeletion() throws IOException {

        String gitRepo = "git://parent/delete-test-repo";
        final URI newRepo = URI.create(gitRepo);
        JGitFileSystemProxy fs = (JGitFileSystemProxy) provider.newFileSystem(newRepo,
                                                                              EMPTY_ENV);

        int threadCount = 2;
        final CountDownLatch finished = new CountDownLatch(threadCount);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int name = i;
            Runnable r = () -> {
                try {
                    this.provider.deleteFS(fs.getRealJGitFileSystem());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                finished.countDown();
                logger.info("Countdown" + Thread.currentThread().getName());
            };
            Thread t = new Thread(r);
            threads.add(t);
            t.start();
        }

        wait(threads);
        assertEquals(0,
                     finished.getCount());
    }

    private void wait(List<Thread> threads) {
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                logger.error("Error waiting for threads",
                             e);
            }
        });
    }
}
