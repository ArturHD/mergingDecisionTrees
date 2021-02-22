/*
 Copyright (c) 2013 by Artur Andrzejak <arturuni@gmail.com>, Felix Langner, Silvestre Zabala

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.


 */

package edu.pvs.batchrunner.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Artur Andrzejak
 * @author
 * @date: 16.11.2005 - 15:38:50
 * @description: A collection of simple file manipulation routines
 * @see
 */
public class Files {

    /**
     * Saves a string into a file
     *
     * @param filename the filename and path of the textfile
     * @param text     the text to save
     */
    public static void saveTextFile(String filename, String text) {
        saveTextFile(filename, text, false);
    }

    /**
     * Saves or appends a string to a file
     *
     * @param filename the filename and path of the textfile
     * @param text     the text to save
     */
    public static void saveTextFile(String filename, String text, boolean append) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(new File(filename), append));
            out.write(text);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file: " + filename + ", " + e);
        }
    }

    /**
     * Saves or appends a string to a file
     *
     * @param file the  textfile
     * @param text the text to save
     */
    public static void saveTextFile(File file, String text, boolean append) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(file, append));
            out.write(text);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file: " + file.toString() + ", " + e);
        }
    }

    /**
     * Creates a temporary file with the specified contents and returns the
     * corresponding file object. The file will be deleted on JVM exit.
     *
     * @param content
     * @return
     * @throws IOException
     */
    public static File makeTempFile(String content) throws IOException {
        File file = File.createTempFile("temp", ".temp");
        file.deleteOnExit();

        BufferedWriter out = new BufferedWriter(new FileWriter(file));
        out.write(content);
        out.close();

        return file;
    }

    /**
     * Reads a file into an array of strings, one per line (fast)
     *
     * @param fileName full path (possibly relative)
     * @return if exists: the whole file contents as array of line strings; null if not exists
     */
    public static String[] readFileToLines(String fileName) {
        String contents = readFile(fileName);
        if (contents == null) return null;
        return contents.split("\n");
    }

    /**
     * Splits an array of lines into a 2d array of space-trimmed string tokens,
     * 1st dim = line, 2nd dim = tokens in this line
     *
     * @param lines array of line Strings
     * @return null if not exists
     */
    public static String[][] splitLinesToTokens(String[] lines, String tokenSeparator) {
        if (lines == null) return null;
        String[][] tokenLines = new String[lines.length][];
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String[] tokens = line.split(tokenSeparator);
            for (int j = tokens.length - 1; j >= 0; j--) {
                tokens[j] = (tokens[j]).trim();
            }
            tokenLines[i] = tokens;
        }
        return tokenLines;
    }


    /**
     * Reads a file into a string (fast)
     *
     * @param fileName full path (possibly relative)
     * @return if exists: the whole file contents as a string; null if not exists
     */
    public static String readFile(String fileName) {
        File file = new File(fileName);
        if (!file.exists() || file.length() == 0)
            return null;

        return readFile(file);
    }

    /**
     * Reads a file into a string (fast)
     *
     * @param file a file to be read
     * @return if exists: the whole file contents as a string; null if not exists
     */
    public static String readFile(File file) {
        // Create buffer
        char[] cbuf = new char[(int) file.length() + 1];

        // read the file into the string
        try {
            FileReader fr = new FileReader(file);

            int index = 0;
            for (int charsRead = 0; charsRead > -1; index += charsRead)
                charsRead = fr.read(cbuf, index, cbuf.length - index);
            fr.close();

            return new String(cbuf, 0, index + 1);  // last param is length!
        } catch (IOException e) {
            System.err.println("Error reading file: " + file.getName() + ", " + e);
            return null;
        }
    }


    /**
     * Checks if a file exists and has length > 0
     *
     * @param fileName complete (abs or rel) path of the file
     * @return true iff the file exists && its length > 0
     */
    public static boolean fileExists(String fileName) {
        File file = new File(fileName);
        return file.exists() && file.length() > 0;
    }

    /**
     * Checks if a file exists and has length > 0
     *
     * @param file file
     * @return true iff the file exists && its length > 0
     */
    public static boolean fileExists(File file) {
        return file.exists() && file.length() > 0;
    }

    /**
     * Checks if a file exists and has length > 0
     *
     * @param parentPath optional directory name
     * @param childPath  path of the child (can be file or relative directory)
     * @return true iff the file exists && its length > 0
     */
    public static boolean fileExists(String parentPath, String childPath) {
        File file = new File(parentPath, childPath);
        return file.exists() && file.length() > 0;
    }


    /**
     * Zips a file or directory to a file, and optionally remove the zipped files
     *
     * @param srcPath          the path of the source (either dir or file)
     * @param targetPath       the full path of the file to be created
     * @param srcFilter        optional filter for files in the dir, in form e.g. *.extension
     * @param deleteOriginals  if true, delete the originals after successful operation
     * @param compressionLevel compression level (0-9) as specified in java.funutil.zip.ZipOutputStream, if < 0, default is taken
     * @return if null, everything went ok, otherwise the error text
     */
    public static String zipFileOrDir(String srcPath, String targetPath, String srcFilter, boolean deleteOriginals, int compressionLevel) {
        // check what is the target
        File srcFile = new File(srcPath);
        if (!srcFile.exists()) {
            return "Source file with path " + srcPath + " does not exist.";
        }

        List<String> srcFnames;
        if (srcFile.isDirectory()) {
            // zip a dir
            srcFnames = listDir(srcFile, srcFilter, false);
        } else {
            // zip a single file
            srcFnames = new ArrayList<String>(1);
            srcFnames.add(srcPath);
        }

        // do the REAL ZIPPING, BABY!
        String error = zipFiles(srcFnames, targetPath, compressionLevel);
        if (error != null) return error;

        // now optionally remove the originals
        if (deleteOriginals) {
            for (String fname : srcFnames) {
                File file = new File(fname);
                // bye, bye
                file.delete();
            }
        }
        return null;
    }

    /**
     * Lists files of a dir into a list, with optional filtering
     *
     * @param srcDir         path of the dir
     * @param nameFilter     optional filenamefilter; if not null, only names containing the nameFilter-string are listed (no '*' etc., simple String.indexOf-matching; if null, all files are used
     * @param includeSubdirs if true, also the names of subdirectories are included
     * @return collection of full absolute paths to the dir files
     */
    public static List<String> listDir(File srcDir, String nameFilter, boolean includeSubdirs) {
        List<String> foundFileNames = new ArrayList<String>();

        SimpleFilenameFilter filter = null;
        if (nameFilter != null) {
            filter = new SimpleFilenameFilter(nameFilter);
        }
        File[] files = srcDir.listFiles(filter);
        for (File file : files) {
            if (includeSubdirs || !file.isDirectory())
                foundFileNames.add(file.getAbsolutePath());
        }
        return foundFileNames;
    }


    /**
     * Creates a zip file from files specified by a collection on file names
     *
     * @param srcFileNames     collection of file names (full abs/rel paths)
     * @param targetPath       the full rel/abs path of the zip file to be created
     * @param compressionLevel compression level (0-9) as specified in java.funutil.zip.ZipOutputStream
     * @return null if everything went ok, otherwise the error string
     */
    public static String zipFiles(Collection<String> srcFileNames, String targetPath, int compressionLevel) {
        int bufSize = 4096;
        // Create a buffer for reading the files
        byte[] buf = new byte[bufSize];

        try {
            // Create the ZIP file
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(targetPath));
            if (compressionLevel >= 0) out.setLevel(compressionLevel);
            // Compress the files
            for (String fileName : srcFileNames) {
                FileInputStream in = new FileInputStream(fileName);
                // Add ZIP entry to output stream
                out.putNextEntry(new ZipEntry(fileName));
                // Transfer bytes from the file to the ZIP file
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                // Complete the entry
                out.closeEntry();
                in.close();
            }
            // Complete the ZIP file
            out.close();
        } catch (IOException e) {
            return e.toString();
        }
        return null;
    }


    private static class SimpleFilenameFilter implements FilenameFilter {

        private String filter;

        public SimpleFilenameFilter(String filter) {
            this.filter = filter;
        }

        public boolean accept(File dir, String name) {
            return name.contains(filter);
        }

    }


    /**
     * Serializes an object to a file
     *
     * @param fullPath    full file name
     * @param dataToStore object to be serialized
     */
    public static void writeObjectToFile(String fullPath, Object dataToStore) {

        FileOutputStream fileOut;
        try {
            fileOut = new FileOutputStream(fullPath);
            // Write object with ObjectOutputStream
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOut);
            objectOutputStream.writeObject(dataToStore);
            objectOutputStream.close();
            fileOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads an AvailSeries belonging to hostId into this map
     * Possibly substitute an existing object
     *
     * @param fullPath full file name
     */
    public static Object readObjectFromFile(String fullPath) {

        // Read from disk using FileInputStream
        FileInputStream fileInputStream;
        Object readObject = null;
        try {
            fileInputStream = new FileInputStream(fullPath);
            // Read object using ObjectInputStream
            ObjectInputStream obj_in = new ObjectInputStream(fileInputStream);
            // Read an object
            readObject = obj_in.readObject();
            obj_in.close();
            fileInputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        if (readObject == null) {
            System.err.println("Warning: could not find file or deserialize obj with fileName" + fullPath);
            return null;
        }
        return (readObject);
    }

// Serializes and saves an objToSave to disk

    public static void saveObjectToDisk(String path, String fileName, Object objToSave) {
        // save file
        try {
            //use buffering
            OutputStream file = new FileOutputStream(new File(path, fileName));
            OutputStream buffer = new BufferedOutputStream(file);
            ObjectOutput output = new ObjectOutputStream(buffer);
            try {
                output.writeObject(objToSave);
            } finally {
                output.close();
            }
        } catch (IOException ex) {
            System.err.println("Cannot save file " + fileName + " at path " + path + ", exception: " + ex.toString());
        }
    }

}

