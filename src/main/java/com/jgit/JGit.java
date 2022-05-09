package com.jgit;



import com.jgit.Blobs.BlobPool;
import com.jgit.Commits.Commit;
import com.jgit.Commits.CommitChain;
import com.jgit.Stage.Stage;
import com.jgit.Utility.Exceptions.*;
import com.jgit.Utility.Utils;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.jgit.Utility.Utils.getGitDirPath;


/**
 * Git的主类，Git从这里启动并接受命令实现所有功能
 *
 * Git类实例化时，会首先尝试从当前工作目录下反序列化commitChain，stage，blobpool三个文件
 * 分别是是Git底层数据结构的序列化文件、暂存区记录、文件池（保存了所有文件的所有版本的快照）
 */

public class JGit {

    private static BlobPool blobPool;
    private static CommitChain commitChain;
    private static Stage stage;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Please enter a command.");
            return;
        }

        if (args[0].equals("init")) {
            init(args);
        } else {
            if (!isInitialized()) {
                System.err.println("Not in an initialized Git directory.");
                System.exit(0);
            }
            blobPool = BlobPool.deSerialFrom(Utils.getBlobsPath());
            commitChain = CommitChain.deSerialFrom(Utils.getCommitChainPath());
            stage = Stage.deSerialFrom(Utils.getStageFilePath());
            switch (args[0]) {
                case "add": add(args); break;
                case "branch": branch(args); break;
                case "checkout": checkout(args); break;
                case "commit": commit(args, false); break;
                case "find": find(args); break;
                case "global-log": globalLog(args); break;
                case "log": log(args); break;
                case "merge": merge(args); break;
                case "reset": reset(args); break;
                case "rm": rm(args); break;
                case "rm-branch": rmBranch(args); break;
                case "status": status(args);break;
                default: System.err.println("No command with that name exists."); break;
            }
        }
        Utils.serializeAll(commitChain, stage, blobPool);
    }

    private static boolean isInitialized() {
        return Files.exists(getGitDirPath());
    }

    private static void checkArgsValid(String[] args, int argsLength) {
        if (args.length != argsLength) {
            System.err.println("Incorrect operands.");
            System.exit(0);
        }
    }

    /**
     * 暂存（跟踪）指定文件
     * @param args 命令行参数
     */
    private static void add(String[] args) {
        checkArgsValid(args, 2);
        try {
            String s = args[1];
            if (s.equals(".")) s = "";
            Path path = Paths.get(s);
            List<Path> files = Files.walk(path).filter((p) -> (!Files.isDirectory(p) && !(p.toString().charAt(0)=='.'))).collect(Collectors.toList());
            stage.trackFile(files);
            blobPool.addFile(files);
        } catch (IOException e) {
            System.err.println("No file with that name exists ");
            System.exit(0);
        }
    }

    /**
     * 新增一个分支，并让这个分支指向head所指向的commit
     * @param args 命令行参数
     */
    private static void branch(String[] args) {
        checkArgsValid(args, 2);
        try {
            commitChain.addBranch(args[1]);
        } catch (AlreadyExistBranchException e) {
            System.err.println("A branch with that name already exists.");
            System.exit(0);
        }
    }

    /**
     * 切换到指定分支
     * @param args 命令行参数
     */
    private static void checkout(String[] args) {
        checkArgsValid(args, 2);
        try {
            commitChain.changeBranchTo(args[1]);
        } catch (NoSuchBranchException e) {
            System.err.println("No such branch exists.");
            System.exit(0);
        }
        Utils.syncFilesWithHeadCommit(commitChain, blobPool);
        stage.clear();
    }

    /**
     * 在commitChain上添加一个Commit结点
     *
     * 首先生成提交时间，SHA-1和本次commit要保存的文件夹路径等必要信息
     * 然后比较上次commit中文件的hash和这次是否一样，如果一样的话，停止commit
     * 然后在commitChain上添加一个Commit结点，具体逻辑由commitChain实现
     * @param args 命令行参数
     * @param isFirstCommit 指示本次commit是否为本Repo的第一次commit
     */
    private static void commit(String[] args, boolean isFirstCommit) {
        checkArgsValid(args, 2);
        String log = args[1];
        ZonedDateTime commitTime = ZonedDateTime.now();
        String hash = Utils.encrypt(commitTime.toString(), "SHA-1");
        Map<String, String> stagedFiles = stage.getTrackingFiles();
        //第一次提交不需要检查提交文件的状况，因为没有上次提交，暂存区也不会有任何文件
        if (!isFirstCommit) {
            Collection<String> lastCommitFiles = commitChain.getHeadCommit().getFileHashes();
            //如果跟踪文件为0个或者这次提交的文件和上次完全一样，就不用提交了
            if (stage.getNumberOfStagedFiles()==0 ||
                    (lastCommitFiles.containsAll(stagedFiles.values()) && (lastCommitFiles.size()==stagedFiles.size()))) {
                System.err.println("No changes added to the commit.");
                System.exit(0);
            }
            //检查暂存区跟踪的文件有没有
        }
        commitChain.newCommit(commitTime, log, stagedFiles, hash, System.getProperty("user.name"));
    }

    /**
     * 打印本Repo中所有的提交记录
     * @param args 命令行参数
     */
    private static void globalLog(String[] args) {
        checkArgsValid(args, 1);
        Iterator<Map.Entry<String, Commit>> commitIterator = commitChain.getAllCommitsIterator();
        while(commitIterator.hasNext()) {
            Commit temp = commitIterator.next().getValue();
            if (commitChain.isHead(temp)) System.out.println("****current HEAD****");
            System.out.println(temp);
            System.out.println("===");
        }
    }

    /**
     * 初始化Repo
     *
     * 创建2个文件夹: .gitlet和object，前者用于记录git仓库，后者用于保存文件快照，然后执行第一次commit
     * @param args 命令行参数
     */
    private static void init(String[] args) {
        checkArgsValid(args, 1);
        blobPool = new BlobPool();
        commitChain = new CommitChain();
        stage = new Stage();
        try {
            Files.createDirectory(getGitDirPath());
            Files.createDirectory(Utils.getFilesPath());
        } catch (FileAlreadyExistsException e) {
            System.err.println("A Git version-control system already exists in the current directory.");
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
        commit(new String[]{"commit", "initial commit"}, true);
    }

    /**
     * 按时间逆序打印当前branch上的所有提交历史，直到全局的第一次提交
     * @param args 命令行参数
     */
    private static void log(String[] args) {
        checkArgsValid(args, 1);
        for (Commit temp : commitChain) {
            System.out.println(temp);
            System.out.println("===");
        }
    }

    /**
     * 合并两个文件，使用三路归并算法
     * 一个较好的解释参见https://blog.walterlv.com/post/git-merge-principle.html
     * 详细算法参见CommitChain::mergeWithBranch方法的注释
     */
    private static void merge(String[] args) {
        checkArgsValid(args, 2);
        //下面的几行注释代码是为了解决当前暂存区还有文件时进行merge的问题：是直接忽略还是提示用户提交暂存后再做决定
        //为了偷懒，我毅然选择了前者
//        if (stage.getNumberOfStagedFiles() != 0) {
//            System.err.println("There are files in stageing area. Please remove or commit them first.");
//            System.exit(0);
//        }
        //我不能跟自己merge啊
        if (commitChain.getCurBranchName().equals(args[1])) {
            System.err.println("can not merge with the branch itself.");
            System.exit(0);
        }
        try {
            ZonedDateTime commitTime = ZonedDateTime.now();
            String hash = Utils.encrypt(commitTime.toString(), "SHA-1");
            commitChain.mergeWithBranch(commitTime, hash, System.getProperty("user.name"), args[1]);
        } catch (NoSuchBranchException e) {
            System.err.println("No branch with that name exists.");
            System.exit(0);
        } catch (ReverseMergeException e) {
            //孙子要跟爷爷merge，开倒车不行
            System.err.println("can not merge with a branch that is the ancester of current working branch.");
            System.exit(0);
        } catch (MergeException e) {
            //三方的文件都不相同，无法决策保留哪个
            //其实有算法可以更加智能地解决这个问题，但是我菜啊啊啊啊啊，又菜又懒啊啊啊
            System.err.println("conflict when merge "+ e.getConflictSource());
            System.exit(0);
        }
        Utils.syncFilesWithHeadCommit(commitChain, blobPool);
        stage.clear();
    }

    /**
     * 将head改变到指定commit，同时文件夹内容也会恢复到commit时的快照内容
     *
     * 指定commit内所有文件快照都会被复制到它们原来所在的目录，替代现有的版本（如果现在存在的话）
     * @param args 命令行参数
     */
    private static void reset(String[] args) {
        checkArgsValid(args, 2);
        try {
            commitChain.resetTo(Utils.fromHash2DirName(args[1]));
        } catch (NoSuchCommitException e) {
            System.err.println("No commit with that id exists.");
            System.exit(0);
        }
        Utils.syncFilesWithHeadCommit(commitChain, blobPool);
        stage.clear();
    }

    /**
     * 删除暂存区的指定文件，同时也删除工作目录的对应文件
     * @param args 命令行参数
     */
    private static void rm(String[] args) {
        checkArgsValid(args, 2);
        try {
            String hashOfRemovedFile = stage.untrackFile(Paths.get(args[1]));
            blobPool.rmFile(hashOfRemovedFile);
        } catch (NotStagedException e) {
            System.err.println("Not staged yet.");
            System.exit(0);
        } catch (IOException e) {
            System.err.println("No file with this path exists.");
            System.exit(0);
        }
    }

    /**
     * 删除指定分支
     * @param args 命令行参数
     */
    private static void rmBranch(String[] args) {
        checkArgsValid(args, 2);
        try {
            commitChain.deleteBranch(args[1]);
        } catch (DeleteCurrentBranchException e) {
            System.err.println("Can not remove the current branch.");
            System.exit(0);
        } catch (NoSuchBranchException e) {
            System.err.println("A branch with that name does not exist.");
            System.exit(0);
        }
    }

    /**
     * 打印状态，分为三种：
     * 1. 跟踪中的文件
     * 2. 已经暂存但是在工作区已经被修改或者删除的文件
     * 3. 工作目录中没有被跟踪的文件
     * @param args 命令行参数
     */
    private static void status(String[] args) {
        checkArgsValid(args, 1);
        List<String> hashesOfStagedFiles = stage.getHashesOfStagedFiles();
        List<String> untrackFiles = new ArrayList<>(), modifiedFiles = new ArrayList<>(),
                deletedFiles = new ArrayList<>(), trackingFiles = new ArrayList<>();
        //检查已暂存文件的跟踪情况
        for(String hash : hashesOfStagedFiles) {
            Path dirRaw = blobPool.getFile(hash).getPathRaw();
            //只要还在暂存区里，就是正在跟踪的文件
            trackingFiles.add(dirRaw.toString());
            //用户使用shell的命令删除或移动了文件，导致原路径的文件找不到了，那就标记为被删除
            if (!Files.exists(dirRaw))
                deletedFiles.add(dirRaw.toString());
            //文件还在，但是跟暂存区的最新版本不一样了，那就是被修改过了，但是还没暂存
            else if (!Utils.encrypt(dirRaw, "SHA-1").equals(hash))
                modifiedFiles.add(dirRaw.toString());
        }
        //检查工作目录下未跟踪的文件
        try {
            //一个文件，如果他不属于上面三种的任何一个，就是未跟踪的文件
            Files.list(Paths.get("")).forEach((path -> {
                String p = path.toString();
                if (!(p.equals(Utils.GIT_DIR_NAME)) && !trackingFiles.contains(p) && !modifiedFiles.contains(p) && !deletedFiles.contains(p))
                    untrackFiles.add(p);
            }));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
        System.out.println("current working branch: " + commitChain.getCurBranchName());
        System.out.println();
        System.out.println("tracking files:");
        trackingFiles.forEach(System.out::println);
        System.out.println();
        System.out.println("Staged but modified files:");
        modifiedFiles.forEach(System.out::println);
        System.out.println();
        System.out.println("Staged but removed files:");
        deletedFiles.forEach(System.out::println);
        System.out.println();
        System.out.println("Untracked files:");
        untrackFiles.forEach(System.out::println);
    }

    /**
     * 遍历所有Commit对象，打印出具有指定log的Commit对象
     * @param args
     */
    private static void find(String[] args) {
        checkArgsValid(args, 2);
        boolean noSuchCommit = true;
        Iterator<Map.Entry<String,Commit>> iterator = commitChain.getAllCommitsIterator();
        while(iterator.hasNext()) {
            Map.Entry<String,Commit> temp = iterator.next();
            if (temp.getValue().getLog().equals(args[1])) {
                System.out.println(temp.getValue());
                noSuchCommit = false;
            }
        }
        if (noSuchCommit)
            System.out.println("Found no commit with that message.");
    }
}