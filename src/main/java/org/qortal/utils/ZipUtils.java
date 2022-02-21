/*
 * MIT License
 *
 * Copyright (c) 2017 Eugen Paraschiv
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Code modified in 2021 for Qortal Core
 *
 */

package org.qortal.utils;

import org.qortal.controller.Controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    public static void zip(String sourcePath, String destFilePath, String enclosingFolderName) throws IOException, InterruptedException {
        File sourceFile = new File(sourcePath);
        boolean isSingleFile = Paths.get(sourcePath).toFile().isFile();
        FileOutputStream fileOutputStream = new FileOutputStream(destFilePath);
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
        ZipUtils.zip(sourceFile, enclosingFolderName, zipOutputStream, isSingleFile);
        zipOutputStream.close();
        fileOutputStream.close();
    }

    public static void zip(final File fileToZip, final String enclosingFolderName, final ZipOutputStream zipOut, boolean isSingleFile) throws IOException, InterruptedException {
        if (Controller.isStopping()) {
            throw new InterruptedException("Controller is stopping");
        }

        // Handle single file resources slightly differently
        if (isSingleFile) {
            // Create enclosing folder
            zipOut.putNextEntry(new ZipEntry(enclosingFolderName + "/"));
            zipOut.closeEntry();
            // Place the supplied file within the folder
            ZipUtils.zip(fileToZip, enclosingFolderName + "/" + fileToZip.getName(), zipOut, false);
            return;
        }

        if (fileToZip.isDirectory()) {
            if (enclosingFolderName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(enclosingFolderName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(enclosingFolderName + "/"));
                zipOut.closeEntry();
            }
            final File[] children = fileToZip.listFiles();
            for (final File childFile : children) {
                ZipUtils.zip(childFile, enclosingFolderName + "/" + childFile.getName(), zipOut, false);
            }
            return;
        }
        final FileInputStream fis = new FileInputStream(fileToZip);
        final ZipEntry zipEntry = new ZipEntry(enclosingFolderName);
        zipOut.putNextEntry(zipEntry);
        final byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    public static void unzip(String sourcePath, String destPath) throws IOException {
        final File destDir = new File(destPath);
        final byte[] buffer = new byte[1024];
        final ZipInputStream zis = new ZipInputStream(new FileInputStream(sourcePath));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            final File newFile = ZipUtils.newFile(destDir, zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                final FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

    /**
     * See: https://snyk.io/research/zip-slip-vulnerability
     */
    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

}
