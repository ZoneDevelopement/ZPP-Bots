package dev.nandi0813.zppbots.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DecisionSourceTest {

    private static final Pattern DECISION = Pattern.compile(
            "ctx\\.log\\(\\)\\.decision\\(\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*([A-Z][A-Z0-9_]*)\\s*,");
    private static final Pattern CODE = Pattern.compile(
            "private\\s+static\\s+final\\s+String\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*\\\"([A-Z][A-Z0-9_]*)\\\"\\s*;");

    @Test
    void decisionsUseUniqueClassAndCodePairs() throws IOException {
        Path root = Path.of("src/main/java/dev/nandi0813/zppbots");
        Set<String> pairs = new HashSet<>();
        int scannedDecisions = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                String simpleName = path.getFileName().toString().replaceFirst("\\.java$", "");
                assertFalse(Pattern.compile("\\.log\\(\\)\\.(detail|brief|warn)\\s*\\(").matcher(source).find(), path.toString());
                int calls = occurrences(source, "ctx.log().decision(");
                Matcher matcher = DECISION.matcher(source);
                int literalCalls = 0;
                while (matcher.find()) {
                    literalCalls++;
                    String sourceName = matcher.group(1);
                    String code = matcher.group(2);
                    assertEquals(simpleName, sourceName, path + " source " + sourceName);
                    Matcher declarations = CODE.matcher(source);
                    boolean declared = false;
                    while (declarations.find()) {
                        if (declarations.group(1).equals(code) && declarations.group(2).equals(code)) {
                            declared = true;
                            break;
                        }
                    }
                    assertTrue(declared, path + " missing code constant " + code);
                    assertTrue(pairs.add(sourceName + ":" + code), path + " duplicate " + sourceName + ":" + code);
                }
                assertEquals(calls, literalCalls, path + " dynamic decision source/code");
                scannedDecisions += literalCalls;
            }
        }
        assertTrue(scannedDecisions > 0, "No decisions scanned under " + root);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }
}
