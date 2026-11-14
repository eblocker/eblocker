package org.eblocker.server.common.blocker;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertThrows;

public class BlockerListValidatorTest {
    private ExternalDefinition pattern(String name) {
        return new ExternalDefinition(1035, name, null, Category.ADS, Type.PATTERN,
                null, Format.EASYLIST, null, UpdateInterval.NEVER, UpdateStatus.NEW,
                null, null, true, "blacklist");
    }

    @Test
    public void acceptsValidPatternListBeyondFiftyThousandRules() throws Exception {
        Path source = Files.createTempFile("eblocker-pattern-rules", ".txt");
        try {
            try (var writer = Files.newBufferedWriter(source)) {
                for (int i = 0; i < 50_001; i++) {
                    writer.write("||example" + i + ".invalid^");
                    writer.newLine();
                }
            }
            new BlockerListValidator().validate(pattern("Large valid list"), source);
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    public void stillRejectsPatternSourceAboveEightMiB() throws Exception {
        Path oversized = Files.createTempFile("eblocker-oversized", ".txt");
        try {
            try (var out = Files.newOutputStream(oversized)) {
                byte[] chunk = new byte[8192];
                for (int i = 0; i <= 1024; i++) out.write(chunk);
            }
            assertThrows(java.io.IOException.class,
                    () -> new BlockerListValidator().validate(pattern("Oversized"), oversized));
        } finally {
            Files.deleteIfExists(oversized);
        }
    }
}
