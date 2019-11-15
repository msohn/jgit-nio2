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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import me.porcelli.nio.jgit.fs.attribute.BranchDiff;
import me.porcelli.nio.jgit.impl.op.Git;
import me.porcelli.nio.jgit.impl.op.GitImpl;
import me.porcelli.nio.jgit.impl.op.commands.Commit;
import me.porcelli.nio.jgit.impl.op.commands.CreateBranch;
import me.porcelli.nio.jgit.impl.op.commands.CreateRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class JGitFileSystemImplProviderDiffTest extends AbstractTestInfra {

    private Logger logger = LoggerFactory.getLogger(JGitFileSystemImplProviderDiffTest.class);

    @Test
    public void testDiffsBetweenBranches() throws IOException {

        final File parentFolder = createTempDirectory();

        final File gitSource = new File(parentFolder,
                                        "repo.git");
        final Git origin = new CreateRepository(gitSource).execute().get();
        final Repository gitRepo = origin.getRepository();

        new Commit(origin,
                   "master",
                   "name",
                   "name@example.com",
                   "master-1",
                   null,
                   null,
                   false,
                   new HashMap<String, File>() {{
                       put("file1.txt",
                           tempFile("temp1\ntemp1\ntemp3\nmiddle\nmoremiddle\nmoremiddle\nmoremiddle\nother\n"));
                   }}).execute();

        new CreateBranch((GitImpl) origin,
                         "master",
                         "develop").execute();

        new Commit(origin,
                   "develop",
                   "name",
                   "name@example.com",
                   "develop-1",
                   null,
                   null,
                   false,
                   new HashMap<String, File>() {{
                       put("file1.txt",
                           tempFile("temp1\ntemp2\nmiddle\nmoremiddle\nmoremiddle\nmoremiddle\n"));
                   }}).execute();

        new Commit(origin,
                   "develop",
                   "name",
                   "name@example.com",
                   "develop-2",
                   null,
                   null,
                   false,
                   new HashMap<String, File>() {{
                       put("file3.txt",
                           tempFile("temp3"));
                   }}).execute();

        new Commit(origin,
                   "develop",
                   "name",
                   "name@example.com",
                   "develop-3",
                   null,
                   null,
                   false,
                   new HashMap<String, File>() {{
                       put("file4.txt",
                           tempFile("temp4"));
                   }}).execute();

        new Commit(origin,
                   "develop",
                   "name",
                   "name@example.com",
                   "develop-4",
                   null,
                   null,
                   false,
                   new HashMap<String, File>() {{
                       put("file5.txt",
                           tempFile("temp5"));
                   }}).execute();

        final URI newRepo = URI.create("git://diff-repo");

        final Map<String, Object> env = new HashMap<String, Object>() {{
            put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEFAULT_REMOTE_NAME,
                origin.getRepository().getDirectory().toString());
        }};

        provider.newFileSystem(newRepo,
                               env);

        final Path path = provider.getPath(newRepo);
        final BranchDiff branchDiff = (BranchDiff) provider.readAttributes(path,
                                                                           "diff:master,develop").get("diff");

        branchDiff.diffs().forEach(elem -> logger.info(elem.toString()));

        assertThat(branchDiff.diffs().size()).isEqualTo(5);
    }

    @Test
    public void testBranchesDoNotHaveDifferences() throws IOException {

        final File parentFolder = createTempDirectory();

        final File gitSource = new File(parentFolder,
                                        "repo.git");
        final Git origin = new CreateRepository(gitSource).execute().get();
        final Repository gitRepo = origin.getRepository();

        new Commit(origin,
                   "master",
                   "name",
                   "name@example.com",
                   "master-1",
                   null,
                   null,
                   false,
                   new HashMap<String, File>() {{
                       put("file1.txt",
                           tempFile("temp1\ntemp1\ntemp3\nmiddle\nmoremiddle\nmoremiddle\nmoremiddle\nother\n"));
                   }}).execute();

        new Commit(origin,
                   "master",
                   "name",
                   "name@example.com",
                   "develop-1",
                   null,
                   null,
                   false,
                   new HashMap<String, File>() {{
                       put("file1.txt",
                           tempFile("temp1\ntemp2\nmiddle\nmoremiddle\nmoremiddle\nmoremiddle\n"));
                   }}).execute();

        new CreateBranch((GitImpl) origin,
                         "master",
                         "develop").execute();

        final URI newRepo = URI.create("git://diff-repo");

        final Map<String, Object> env = new HashMap<String, Object>() {{
            put(JGitFileSystemProviderConfiguration.GIT_ENV_KEY_DEFAULT_REMOTE_NAME,
                origin.getRepository().getDirectory().toString());
        }};

        provider.newFileSystem(newRepo,
                               env);

        final Path path = provider.getPath(newRepo);
        final BranchDiff branchDiff = (BranchDiff) provider.readAttributes(path,
                                                                           "diff:master,develop").get("diff");

        branchDiff.diffs().forEach(elem -> logger.info(elem.toString()));

        assertThat(branchDiff.diffs().size()).isEqualTo(0);
    }
}
