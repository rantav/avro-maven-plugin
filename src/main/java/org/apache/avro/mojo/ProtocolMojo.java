/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.avro.mojo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.apache.avro.Protocol;
import org.apache.avro.genavro.GenAvro;
import org.apache.avro.genavro.ParseException;
import org.apache.avro.specific.SpecificCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

/**
 * Compile an Avro protocol schema file.
 *
 * @goal protocol
 * @phase generate-sources
 */
public class ProtocolMojo extends AbstractMojo {

    public static final String IDL_EXTENSION = ".genavro";
    public static final String PROTOCOL_EXTENSION = ".avpr";

    /**
     * @parameter expression="${sourceDirectory}" default-value="${basedir}/src/main/avro"
     */
    protected File sourceDirectory;

    /**
     * @parameter expression="${outputDirectory}" default-value="${project.build.directory}/generated-sources/avro"
     */
    protected File outputDirectory;

    /**
     * A set of Ant-like inclusion patterns used to select files from
     * the source directory for processing. By default, the pattern
     * <code>**&#47;*.avro</code> is used to select grammar files.
     *
     * @parameter
     */
    private final String[] includes = new String[] { "**/*" + PROTOCOL_EXTENSION,
                                                     "**/*" + IDL_EXTENSION };

    /**
     * A set of Ant-like exclusion patterns used to prevent certain
     * files from being processed. By default, this set is empty such
     * that no files are excluded.
     *
     * @parameter
     */
    private final String[] excludes = new String[0];

    /**
     * The current Maven project.
     *
     * @parameter default-value="${project}"
     * @readonly
     * @required
     */
    protected MavenProject project;

    private final FileSetManager fileSetManager = new FileSetManager();

    public void execute() throws MojoExecutionException {
        if (!sourceDirectory.isDirectory()) {
            // Some prefer to throw an exception if there's not avro directory, but
            // I think it's fine not to have a directory since in a multi-module project
            // some subprojects would have a src/main/avro and some won't
            return;
            // not: throw new MojoExecutionException(sourceDirectory + " is not a directory");
        }

        FileSet fs = new FileSet();
        fs.setDirectory(sourceDirectory.getAbsolutePath());
        fs.setFollowSymlinks(false);

        for (String include : includes) {
            fs.addInclude(include);
        }
        for (String exclude : excludes) {
            fs.addExclude(exclude);
        }

        String[] includedFiles = fileSetManager.getIncludedFiles(fs);

        // Make dir for genavro tmp files
        String tmpOutDir = System.getProperty("java.io.tmpdir") + "avro";
        new File(tmpOutDir).mkdirs();

        for (String filename : includedFiles) {
            try {
                // Step 1: genavro
                String in = sourceDirectory.getAbsolutePath() + File.separator + filename;
                if (in.endsWith(IDL_EXTENSION)) {
                    String out = tmpOutDir + File.separator + getNameWithoutExtension(filename) +
                            PROTOCOL_EXTENSION;
                    InputStream parseIn = new FileInputStream(in);
                    PrintStream parseOut = new PrintStream(new FileOutputStream(out));
                    GenAvro parser = new GenAvro(parseIn);
                    Protocol p = parser.CompilationUnit();
                    parseOut.print(p.toString(true));
                    in = out;
                }
                // Step 2: SpecificCompiler
                SpecificCompiler.compileProtocol(new File(in), outputDirectory);
            } catch (IOException e) {
                throw new MojoExecutionException("Error compiling protocol file " + filename
                        + " to " + outputDirectory, e);
            } catch (ParseException e) {
                throw new MojoExecutionException("Error parsing genavro file " + filename + " to "
                        + outputDirectory, e);
            }
        }

        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

        // cleanup
        deleteDir(tmpOutDir);
    }

    /**
     * Gets a file name from the string without the file extension.
     * For example: x.genavro => x.
     */
    private String getNameWithoutExtension(String filename) {
      if (filename == null) {
        return null;
      }
      int dot = filename.lastIndexOf('.');
      return filename.substring(0, dot);
    }

    /**
     * Deletes all files and subdirectories under dir. If a deletion fails, the method stops
     * attempting to delete and returns false.
     **/
    public static boolean deleteDir(String path) {
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]).getAbsolutePath());
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

}
