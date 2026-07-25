// Tier: 1 (pure JUnit)
package com.rfizzle.tribulation;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the sprite-margin rule on every texture this mod ships from a {@code kind: sprite}
 * glyph spec: the motif must sit inside a 1px fully transparent border, so its {@code ink}
 * outline closes and the sprite reads against any background instead of looking cut off at
 * the slot edge.
 *
 * <p>{@code glyph.py} reports the same violation when a spec is rendered, but the renderer is
 * a local tool — the build only ever sees the PNG. Reading the shipped masters directly is what
 * keeps a clipped silhouette from reaching a release because nobody happened to re-render.
 *
 * <p>The sprite set is derived from the specs rather than hardcoded, because {@code kind:} is
 * what decides which rule a texture owes — a {@code block}, {@code cap}, or {@code ui} texture
 * bleeds to its edges by design and must not be held to this. A sprite added later is covered
 * with no edit here. {@code build.gradle} declares {@code art/glyphs} a test input so this
 * class cannot stay {@code UP-TO-DATE} while the art underneath it changes.
 */
class SpriteMarginTest {

    private static final Path SPEC_DIR = Path.of("art/glyphs");

    /** A shipped master owed the sprite margin, and the native frame size it was authored at. */
    private record Sprite(String spec, Path master, int size) {
        @Override
        public String toString() {
            return master + " (from " + spec + ")";
        }
    }

    /** Strips the directive name, then any trailing {@code #} comment. */
    private static String directiveValue(String line) {
        String value = line.substring(line.indexOf(':') + 1);
        int comment = value.indexOf('#');
        if (comment >= 0) {
            value = value.substring(0, comment);
        }
        return value.strip();
    }

    /**
     * Collects the {@code kind: sprite} specs and the masters their {@code ships:} lines name.
     * Only the directive block above {@code frame:} is scanned — the legend below it carries
     * raw hex that would otherwise read as a comment-bearing directive.
     */
    private static List<Sprite> discoverSprites() throws IOException {
        assertTrue(Files.isDirectory(SPEC_DIR), "missing glyph spec dir: " + SPEC_DIR.toAbsolutePath());

        List<Path> specs;
        try (Stream<Path> files = Files.list(SPEC_DIR)) {
            specs = files.filter(p -> p.getFileName().toString().endsWith(".glyph")).sorted().toList();
        }

        List<Sprite> sprites = new ArrayList<>();
        for (Path spec : specs) {
            String kind = null;
            int size = -1;
            List<String> ships = new ArrayList<>();
            for (String raw : Files.readAllLines(spec, StandardCharsets.UTF_8)) {
                String line = raw.strip();
                if (line.equals("frame:")) break;
                if (line.startsWith("kind:")) {
                    kind = directiveValue(line);
                } else if (line.startsWith("size:")) {
                    size = Integer.parseInt(directiveValue(line));
                } else if (line.startsWith("ships:")) {
                    ships.add(directiveValue(line));
                }
            }
            if (!"sprite".equals(kind)) continue;

            String name = spec.getFileName().toString();
            assertTrue(size > 0, name + " declares kind: sprite but no usable size:");
            for (String ship : ships) {
                // 'ships: <path> [size]' — a size ladder suffixes the tier it renders.
                String target = ship.split("\\s+")[0];
                int shipped = ship.contains(" ") ? Integer.parseInt(ship.split("\\s+")[1]) : size;
                sprites.add(new Sprite(name, Path.of(target), shipped));
            }
        }
        return sprites;
    }

    /** Border pixels of the {@code size}-tall frame starting at {@code top} that are not fully transparent. */
    private static int opaqueBorderPixels(BufferedImage image, int top, int size) {
        int opaque = 0;
        for (int x = 0; x < size; x++) {
            if ((image.getRGB(x, top) >>> 24) != 0) opaque++;
            if ((image.getRGB(x, top + size - 1) >>> 24) != 0) opaque++;
        }
        for (int y = top + 1; y < top + size - 1; y++) {
            if ((image.getRGB(0, y) >>> 24) != 0) opaque++;
            if ((image.getRGB(size - 1, y) >>> 24) != 0) opaque++;
        }
        return opaque;
    }

    @Test
    void everyShippedSpriteHoldsAOnePixelTransparentMargin() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Sprite sprite : discoverSprites()) {
            if (!Files.isRegularFile(sprite.master())) {
                problems.add(sprite + ": the master its spec ships to does not exist");
                continue;
            }
            BufferedImage image = ImageIO.read(sprite.master().toFile());
            assertTrue(image != null, "could not decode " + sprite.master());

            if (image.getWidth() != sprite.size()) {
                problems.add(sprite + ": is " + image.getWidth() + "px wide but its spec declares "
                        + sprite.size() + "px");
                continue;
            }
            // An animated sprite ships a vertical strip of square frames; a static one is a
            // single frame. Either way each frame owes the margin on its own four sides.
            if (image.getHeight() % sprite.size() != 0) {
                problems.add(sprite + ": height " + image.getHeight()
                        + "px is not a whole number of " + sprite.size() + "px frames");
                continue;
            }
            int frames = image.getHeight() / sprite.size();
            for (int frame = 0; frame < frames; frame++) {
                int opaque = opaqueBorderPixels(image, frame * sprite.size(), sprite.size());
                if (opaque > 0) {
                    String where = frames > 1 ? " frame " + frame : "";
                    problems.add(sprite + where + ": " + opaque
                            + " border pixels are not transparent — the motif runs into the edge, "
                            + "leaving the ink outline open");
                }
            }
        }

        assertTrue(problems.isEmpty(),
                "sprites must hold a 1px transparent margin on all four sides; edit the .glyph "
                        + "and re-render to the path its ships: line names:\n  "
                        + String.join("\n  ", problems));
    }

    /**
     * The margin check derives its subjects by parsing the specs, so a parsing regression would
     * quietly turn it into a no-op that always passes. Pin the sprites that must be found.
     */
    @Test
    void spriteDiscoveryFindsTheShippedItemSprites() throws IOException {
        Set<String> found = discoverSprites().stream()
                .map(s -> s.master().toString().replace('\\', '/'))
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> expected = new TreeSet<>(Set.of(
                "src/main/resources/assets/tribulation/textures/item/heart_fragment.png",
                "src/main/resources/assets/tribulation/textures/item/shatter_shard.png",
                "src/main/resources/assets/tribulation/textures/item/ascendant_shard.png"));

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(found);
        assertTrue(missing.isEmpty(),
                "sprite discovery missed masters it must cover — the margin check is not "
                        + "guarding them: " + missing + " (found: " + found + ")");
    }
}
