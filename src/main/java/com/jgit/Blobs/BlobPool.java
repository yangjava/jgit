package com.jgit.Blobs;

import com.jgit.Utility.Utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class BlobPool implements Serializable {

    //blob pool: map sha-1 to the blob of file.
    private Map<String, Blob> pool = new HashMap<>();

    public static BlobPool deSerialFrom(Path path) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path.toString()));
            return (BlobPool) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addFile(List<Path> files) throws IOException{
        for (Path file : files) {
            Path destDir = Utils.getFilesPath().resolve(Utils.getRelativeDir(file));
            String hash = Utils.encrypt(file, "SHA-1");
            if (!Files.exists(destDir)) Files.createDirectories(destDir);
            if (!pool.containsKey(hash)) {
                Path destFile = destDir.resolve(hash);
                Files.copy(file, destFile);
                pool.put(hash, new Blob(destFile.toString(), file.toString()));
            }
        }
    }

    public Blob getFile(String hash) {
        return pool.get(hash);
    }

    public void rmFile(String hash) throws IOException{
        Files.delete(pool.get(hash).getPathRaw());
        pool.remove(hash);
    }
}
