package com.cloud.common;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * The class for storing information about file
 */
public class FileInfo implements Serializable {
    private String fileName;
    private long size;
    private LocalDateTime lastTimeModify;

    public FileInfo(Path path) {

        try {
            this.fileName = path.getFileName().toString();
            if (Files.isDirectory(path)) {
                this.size = -1L;
            }else {
                this.size = Files.size(path);
            }
            this.lastTimeModify = LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(),
                    ZoneOffset.ofHours(0));
        } catch (IOException e) {
            throw new RuntimeException("Can not determine a file info");
        }

    }

    public String getFilename() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    public LocalDateTime getLastTimeModify() {
        return lastTimeModify;
    }
}
