package com.upload;

import java.nio.file.Path;

public interface Uploader {
    boolean upload(Path file, String relativePath);
}
