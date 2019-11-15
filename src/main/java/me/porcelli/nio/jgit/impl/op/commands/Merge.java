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

package me.porcelli.nio.jgit.impl.op.commands;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import me.porcelli.nio.jgit.impl.op.Git;
import me.porcelli.nio.jgit.impl.op.exceptions.GitException;
import me.porcelli.nio.jgit.impl.op.model.MergeCommitContent;
import me.porcelli.nio.jgit.impl.op.model.MessageCommitInfo;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static me.porcelli.nio.jgit.impl.util.Preconditions.checkNotEmpty;
import static me.porcelli.nio.jgit.impl.util.Preconditions.checkNotNull;

/**
 * Implements Git Merge command between branches in a bare repository.
 * Branches needs to be part of the same repository, you cannot merge
 * branches from different repositories (or forks).
 * This command is based on Git Cherry Pick command for a simple fast forward merge,
 * otherwise it will create a merge commit.
 * It returns the list of commits involved in the merge operation.
 */
public class Merge {

    private Logger logger = LoggerFactory.getLogger(Merge.class);

    private final Git git;
    private final String sourceBranch;
    private final String targetBranch;
    private final boolean noFastForward;

    public Merge(final Git git,
                 final String sourceBranch,
                 final String targetBranch) {
        this(git,
             sourceBranch,
             targetBranch,
             false);
    }

    public Merge(final Git git,
                 final String sourceBranch,
                 final String targetBranch,
                 final boolean noFastForward) {

        this.git = checkNotNull("git",
                                git);
        this.sourceBranch = checkNotEmpty("sourceBranch",
                                          sourceBranch);
        this.targetBranch = checkNotEmpty("targetBranch",
                                          targetBranch);

        this.noFastForward = noFastForward;
    }

    public List<String> execute() throws IOException {
        BranchUtil.existsBranch(git,
                                sourceBranch);
        BranchUtil.existsBranch(git,
                                targetBranch);

        final RevCommit lastSourceCommit = git.getLastCommit(sourceBranch);
        final RevCommit lastTargetCommit = git.getLastCommit(targetBranch);

        final RevCommit commonAncestor = git.getCommonAncestorCommit(sourceBranch,
                                                                     targetBranch);

        canMerge(git.getRepository(),
                 commonAncestor,
                 lastSourceCommit,
                 lastTargetCommit,
                 sourceBranch,
                 targetBranch);

        return proceedMerge(commonAncestor,
                            lastSourceCommit,
                            lastTargetCommit);
    }

    private List<String> proceedMerge(final RevCommit commonAncestor,
                                      final RevCommit lastSourceCommit,
                                      final RevCommit lastTargetCommit) throws IOException {
        final List<DiffEntry> diffBetweenCommits = git.listDiffs(commonAncestor.getName(),
                                                                 lastSourceCommit.getName());

        final List<DiffEntry> diffBetweenBranches = diffBetweenCommits.isEmpty() ?
                Collections.emptyList() : git.listDiffs(git.getTreeFromRef(targetBranch),
                                                        git.getTreeFromRef(sourceBranch));

        if (diffBetweenBranches.isEmpty()) {
            logger.info("There is nothing to merge from branch {} to {}",
                        sourceBranch,
                        targetBranch);
            return Collections.emptyList();
        }

        final List<RevCommit> targetCommits = git.listCommits(commonAncestor,
                                                              lastTargetCommit);

        return targetCommits.isEmpty() && !noFastForward ?
                doFastForward(commonAncestor,
                              lastSourceCommit) : doMerge(commonAncestor,
                                                          lastSourceCommit,
                                                          lastTargetCommit);
    }

    private void canMerge(final Repository repo,
                          final RevCommit commonAncestor,
                          final RevCommit sourceCommitTree,
                          final RevCommit targetCommitTree,
                          final String sourceBranch,
                          final String targetBranch) {
        try {
            ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(repo,
                                                                      true);
            merger.setBase(commonAncestor);
            boolean canMerge = merger.merge(sourceCommitTree,
                                            targetCommitTree);
            if (!canMerge) {
                throw new GitException(String.format("Cannot merge branches from <%s> to <%s>, merge conflicts",
                                                     sourceBranch,
                                                     targetBranch));
            }
        } catch (IOException e) {
            throw new GitException(String.format("Cannot merge branches from <%s> to <%s>, merge conflicts",
                                                 sourceBranch,
                                                 targetBranch),
                                   e);
        }
    }

    private List<String> doFastForward(final RevCommit commonAncestor,
                                       final RevCommit lastSourceCommit) throws IOException {
        final List<RevCommit> sourceCommits = git.listCommits(commonAncestor,
                                                              lastSourceCommit);

        Collections.reverse(sourceCommits);

        final String[] commitsIDs = sourceCommits.stream()
                .map(AnyObjectId::getName)
                .toArray(String[]::new);

        git.cherryPick(targetBranch,
                       commitsIDs);

        if (logger.isDebugEnabled()) {
            logger.debug("Merging commits from <{}> to <{}>",
                         sourceBranch,
                         targetBranch);
        }

        return Arrays.asList(commitsIDs);
    }

    private List<String> doMerge(final RevCommit commonAncestorCommit,
                                 final RevCommit lastSourceCommit,
                                 final RevCommit lastTargetCommit) {
        try {
            final Map<String, File> contents = git.mapDiffContent(sourceBranch,
                                                                  commonAncestorCommit.getName(),
                                                                  lastSourceCommit.getName());

            final List<RevCommit> parents = Stream.of(lastTargetCommit,
                                                      lastSourceCommit).collect(Collectors.toList());

            final boolean effective = git.commit(targetBranch,
                                                 MessageCommitInfo.createMergeMessage(sourceBranch),
                                                 false,
                                                 lastTargetCommit,
                                                 new MergeCommitContent(contents,
                                                                        parents));
            if (effective) {
                return Collections.singletonList(git.getLastCommit(targetBranch).getName());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        throw new GitException(String.format("Cannot merge branches from <%s> to <%s>",
                                             sourceBranch,
                                             targetBranch));
    }
}
