package com.rfizzle.tribulation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the gametest entrypoint split: the gametest classes live only in the
 * {@code gametest} source set, so the shipped manifest must not declare them.
 * {@code fabric-gametest-api-v1} is a dev-only Fabric module whose initializer is
 * ungated — it instantiates every declared {@code fabric-gametest} entrypoint on
 * any dev launch, so a stale entry in the shipped manifest crashes {@code runServer},
 * whose run set does not carry the gametest source set.
 */
class GametestEntrypointTest {

    private static final Gson GSON = new Gson();
    private static final String GAMETEST_PKG = "com.rfizzle.tribulation.gametest.";

    /**
     * The test classpath carries a {@code fabric.mod.json} for every Fabric API submodule,
     * so a plain {@code getResourceAsStream} returns an arbitrary one. Enumerate them all and
     * select Tribulation's own manifest by mod id.
     */
    private static JsonObject readTribulationManifest() {
        try {
            Enumeration<URL> urls = GametestEntrypointTest.class.getClassLoader()
                    .getResources("fabric.mod.json");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream stream = url.openStream()) {
                    JsonObject manifest = GSON.fromJson(
                            new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                    if (manifest != null && manifest.has("id")
                            && Tribulation.MOD_ID.equals(manifest.get("id").getAsString())) {
                        return manifest;
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("failed to scan the classpath for fabric.mod.json", e);
        }
        throw new AssertionError("no fabric.mod.json with id '" + Tribulation.MOD_ID + "' on the test classpath");
    }

    @Test
    void shippedManifestDeclaresNoGametestEntrypoints() {
        JsonObject manifest = readTribulationManifest();
        JsonObject entrypoints = manifest.getAsJsonObject("entrypoints");
        assertNotNull(entrypoints, "fabric.mod.json must declare entrypoints");
        assertFalse(entrypoints.has("fabric-gametest"),
                "the shipped manifest must not declare fabric-gametest entrypoints — "
                        + "they belong to the tribulation-gametest manifest in src/gametest/resources");
    }

    /**
     * The gametest manifest is not on any classpath {@code ./gradlew build} reads, so an
     * unregistered suite is silent — it simply never runs. Compare the declared entrypoints
     * against the classes on disk so a new suite cannot rot unnoticed.
     */
    @Test
    void everyGametestSuiteIsRegistered() throws IOException {
        Path sources = Path.of("src/gametest/java/com/rfizzle/tribulation/gametest");
        assertTrue(Files.isDirectory(sources), "missing gametest source dir: " + sources.toAbsolutePath());

        Set<String> onDisk;
        try (Stream<Path> files = Files.list(sources)) {
            onDisk = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith("GameTest.java"))
                    .map(n -> GAMETEST_PKG + n.substring(0, n.length() - ".java".length()))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        Path manifestPath = Path.of("src/gametest/resources/fabric.mod.json");
        assertTrue(Files.isRegularFile(manifestPath), "missing gametest manifest: " + manifestPath.toAbsolutePath());

        JsonObject manifest;
        try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            manifest = GSON.fromJson(reader, JsonObject.class);
        }

        Set<String> declared = new TreeSet<>();
        manifest.getAsJsonObject("entrypoints").getAsJsonArray("fabric-gametest")
                .forEach(e -> declared.add(e.getAsString()));

        Set<String> unregistered = new TreeSet<>(onDisk);
        unregistered.removeAll(declared);
        assertTrue(unregistered.isEmpty(),
                "gametest classes exist but are not registered, so their tests never run: " + unregistered);

        Set<String> dangling = new TreeSet<>(declared);
        dangling.removeAll(onDisk);
        assertTrue(dangling.isEmpty(),
                "the gametest manifest names classes that no longer exist: " + dangling);
    }

    @Test
    void shippedManifestKeepsItsRuntimeEntrypoints() {
        JsonObject entrypoints = readTribulationManifest().getAsJsonObject("entrypoints");
        assertEquals("com.rfizzle.tribulation.Tribulation",
                entrypoints.getAsJsonArray("main").get(0).getAsString());
        assertEquals("com.rfizzle.tribulation.client.TribulationClient",
                entrypoints.getAsJsonArray("client").get(0).getAsString());
    }
}
