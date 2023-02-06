package io.metersphere.api.util;

import io.metersphere.api.vo.RepositoryRequest;
import io.metersphere.utils.JsonUtils;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GitRepositoryUtil {
    private final String REF_SPACE = "+refs/heads/*:refs/heads/*";
    private final String DEFAULT_GIT_USERNAME = "PRIVATE-TOKEN";

    private final String repositoryUrl;
    private final String userName;
    private final String token;

    private Git git;

    public GitRepositoryUtil(String repositoryUrl, String userName, String token) {
        this.repositoryUrl = StringUtils.trim(repositoryUrl);
        if (StringUtils.isNotBlank(userName)) {
            this.userName = StringUtils.trim(userName);
        } else {
            this.userName = this.DEFAULT_GIT_USERNAME;
        }
        this.token = StringUtils.trim(token);
        LoggerUtil.info("初始化文件库完成. repositoryUrl：" + repositoryUrl + "; userName：" + userName + "; token：" + token);
    }

    public byte[] getSingleFile(String filePath, String commitId) throws Exception {
        LoggerUtil.info("准备获取文件. repositoryUrl：" + repositoryUrl + "; filePath：" + filePath + "; commitId：" + commitId);
        InMemoryRepository repo = this.getGitRepositoryInMemory(repositoryUrl, userName, token);
        ObjectId fileCommitObjectId = repo.resolve(commitId);
        RevWalk revWalk = new RevWalk(repo);
        RevCommit commit = revWalk.parseCommit(fileCommitObjectId);
        RevTree tree = commit.getTree();
        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(filePath));
        if (!treeWalk.next()) {
            LoggerUtil.info("未获取到文件!. repositoryUrl：" + repositoryUrl + "; filePath：" + filePath + "; commitId：" + commitId);
            return null;
        }
        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repo.open(objectId);
        byte[] returnBytes = loader.getBytes();
        this.closeConnection(repo);
        return returnBytes;
    }

    public Map<String, byte[]> getFiles(List<RepositoryRequest> repositoryRequestList) throws Exception {
        Map<String, byte[]> returnMap = new HashMap<>();
        if (CollectionUtils.isEmpty(repositoryRequestList)) {
            return returnMap;
        }
        Map<String, List<RepositoryRequest>> commitIdFilePathMap = repositoryRequestList.stream().collect(Collectors.groupingBy(RepositoryRequest::getCommitId));
        LoggerUtil.info("准备批量获取文件. repositoryUrl：" + repositoryUrl + "; commitIdFilePathMap：" + JsonUtils.toJSONString(repositoryRequestList));
        InMemoryRepository repo = this.getGitRepositoryInMemory(repositoryUrl, userName, token);
        ObjectId fileCommitObjectId = null;
        for (Map.Entry<String, List<RepositoryRequest>> commitFilePathEntry : commitIdFilePathMap.entrySet()) {
            String commitId = commitFilePathEntry.getKey();
            List<RepositoryRequest> itemRequestList = commitFilePathEntry.getValue();
            for (RepositoryRequest repositoryRequest : itemRequestList) {
                String filePath = repositoryRequest.getFilePath();
                fileCommitObjectId = repo.resolve(commitId);
                RevWalk revWalk = new RevWalk(repo);
                RevCommit commit = revWalk.parseCommit(fileCommitObjectId);
                RevTree tree = commit.getTree();
                TreeWalk treeWalk = new TreeWalk(repo);
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filePath));
                if (!treeWalk.next()) {
                    LoggerUtil.info("未获取到文件!. repositoryUrl：" + repositoryUrl + "; filePath：" + filePath + "; commitId：" + commitId);
                    continue;
                }
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repo.open(objectId);
                returnMap.put(repositoryRequest.getFileMetadataId(), loader.getBytes());
            }
            this.closeConnection(repo);
        }
        LoggerUtil.info("准备批量获取文件结束. repositoryUrl：" + repositoryUrl);
        return returnMap;
    }

    private InMemoryRepository getGitRepositoryInMemory(String repositoryUrl, String userName, String token) throws Exception {
        DfsRepositoryDescription repoDesc = new DfsRepositoryDescription();
        InMemoryRepository repo = new InMemoryRepository(repoDesc);
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(userName, token);
        git = new Git(repo);
        git.fetch().setRemote(repositoryUrl).setRefSpecs(new RefSpec(REF_SPACE)).setCredentialsProvider(credentialsProvider).call();
        repo.getObjectDatabase();
        return repo;
    }

    private void closeConnection(Repository repo) {
        if (git != null) {
            git.close();
        }
        if (repo != null) {
            repo.close();
        }
    }
}
