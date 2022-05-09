package com.jgit.Utility;



import com.jgit.Blobs.Blob;
import com.jgit.Blobs.BlobPool;
import com.jgit.Commits.CommitChain;
import com.jgit.Stage.Stage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/**
 * 工具类，封装一些常用操作
 */

public class Utils {

    /**
     * 分别记录了gitlet文件夹，commit目录，暂存目录和序列化commitChain的文件名称
     * 主要供初始化Gitlet对象时使用，后面一般从对应的对象的getPath()方法中获得路径
     */
    public static final String GIT_DIR_NAME = ".git";
    public static final String STAGE_SERIALIZATION_NAME = "stage";
    public static final String COMMIT_CHAIN_SERIALIZATION_NAME = "commitchain";
    public static final String FILES_DIR_NAME = "objects";
    public static final String BLOB_POOL_SERIALIZATION_NAME = "blobs";

    public static Path getGitDirPath() {
        return Paths.get(GIT_DIR_NAME);
    }

    public static Path getStageFilePath() { return getGitDirPath().resolve(STAGE_SERIALIZATION_NAME); }

    public static Path getCommitChainPath() {
        return getGitDirPath().resolve(COMMIT_CHAIN_SERIALIZATION_NAME);
    }

    public static Path getBlobsPath() { return getGitDirPath().resolve(BLOB_POOL_SERIALIZATION_NAME); }

    public static Path getFilesPath() { return getGitDirPath().resolve(FILES_DIR_NAME); }

    /**
     * 从SHA-1字符串中截取后6位
     *
     * 主要作为某次commit的目录名称使用
     * @param hash SHA-1字符串
     * @return hash的后六位
     */
    public static String fromHash2DirName(String hash) { return hash.substring(hash.length()-6); }

    /**
     * 检查工作目录是否已经被初始化
     */


    /**
     * 计算字符串的sha-1值
     * @param str 输入字符串
     * @return 该字符串的sha-1值
     */
    public static String encrypt(String str, String algorithm)  {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] bytes = md.digest(str.getBytes());
            return new BigInteger(1, bytes).toString(16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "impossible";
        }
    }

    public static String encrypt(Path file, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] bytes = md.digest(Files.readAllBytes(file));
            return new BigInteger(1, bytes).toString(16);
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "impossible";
        }
    }

    public static void serializeCommitChain(CommitChain cc) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getCommitChainPath().toString()));
            oos.writeObject(cc);
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void serializeBlobPool(BlobPool bp) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getBlobsPath().toString()));
            oos.writeObject(bp);
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void serializeStage(Stage stage) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getStageFilePath().toString()));
            oos.writeObject(stage);
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void serializeAll(CommitChain cc, Stage stage, BlobPool bp) {
        serializeCommitChain(cc);
        serializeStage(stage);
        serializeBlobPool(bp);
    }

    public static void syncFilesWithHeadCommit(CommitChain commitChain, BlobPool blobPool) {
        Collection<String> hashesOfBackupFiles = commitChain.getHeadCommit().getFileHashes();
        for (String hash : hashesOfBackupFiles) {
            Blob blob = blobPool.getFile(hash);
            try {
                Files.copy(blob.getPathGit(), blob.getPathRaw(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String getRelativeDir(Path path) {
        return Paths.get("").relativize(path).toString();
    }
}
