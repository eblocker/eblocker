package org.eblocker.server.common.system;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class IcapServerScriptTest {
    private static final Path SCRIPT = Paths.get("src/main/package/bin/eblocker-icapserver.sh");

    @Test
    public void testMemorySettingsForSmallSystem() throws Exception {
        String javaArguments = runScriptWithMemory(1024);

        assertContains(javaArguments, "-Xmx384m");
        assertContains(javaArguments, "-XX:MaxDirectMemorySize=100m");
        assertContains(javaArguments, "-Dio.netty.allocator.numDirectArenas=4");
        assertContains(javaArguments, "-Dio.netty.allocator.numHeapArenas=4");
    }

    @Test
    public void testMemorySettingsForTwoGigabyteSystem() throws Exception {
        String javaArguments = runScriptWithMemory(2048);

        assertContains(javaArguments, "-Xmx768m");
        assertContains(javaArguments, "-XX:MaxDirectMemorySize=200m");
        assertContains(javaArguments, "-Dio.netty.allocator.numDirectArenas=8");
        assertContains(javaArguments, "-Dio.netty.allocator.numHeapArenas=8");
    }

    @Test
    public void testMemorySettingsScaleWithLargerSystems() throws Exception {
        String javaArguments = runScriptWithMemory(4096);

        assertContains(javaArguments, "-Xmx2048m");
        assertContains(javaArguments, "-XX:MaxDirectMemorySize=200m");
        assertContains(javaArguments, "-Dio.netty.allocator.numDirectArenas=8");
        assertContains(javaArguments, "-Dio.netty.allocator.numHeapArenas=8");
    }

    private String runScriptWithMemory(int systemMemoryMb) throws Exception {
        Path testDirectory = Files.createTempDirectory("icapserver-script-test");
        Path binDirectory = Files.createDirectory(testDirectory.resolve("bin"));
        Path javaArgumentsFile = testDirectory.resolve("java-arguments.txt");

        writeExecutable(binDirectory.resolve("free"), "#!/bin/sh\n"
                + "echo '              total        used        free      shared  buff/cache   available'\n"
                + "echo 'Mem:           " + systemMemoryMb + "         128         128           0         768         768'\n"
                + "echo 'Swap:             0           0           0'\n");
        writeExecutable(binDirectory.resolve("java"), "#!/bin/sh\n"
                + "printf '%s\\n' \"$@\" > \"$JAVA_ARGUMENTS_FILE\"\n");

        Path script = testDirectory.resolve("eblocker-icapserver.sh");
        Files.writeString(script, Files.readString(SCRIPT, StandardCharsets.UTF_8)
                .replace("${project.build.finalName}", "eblocker-icapserver-test"), StandardCharsets.UTF_8);

        ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", script.toString());
        Map<String, String> environment = processBuilder.environment();
        environment.put("PATH", binDirectory + ":" + environment.get("PATH"));
        environment.put("JAVA_ARGUMENTS_FILE", javaArgumentsFile.toString());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Assert.assertTrue("Script did not finish", finished);
        Assert.assertEquals(output, 0, process.exitValue());

        return Files.readString(javaArgumentsFile, StandardCharsets.UTF_8);
    }

    private void writeExecutable(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        Assert.assertTrue(path.toFile().setExecutable(true));
    }

    private void assertContains(String value, String expectedSubstring) {
        Assert.assertTrue("Expected to find <" + expectedSubstring + "> in:\n" + value, value.contains(expectedSubstring));
    }
}
